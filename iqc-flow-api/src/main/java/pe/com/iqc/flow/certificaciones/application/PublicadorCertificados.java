package pe.com.iqc.flow.certificaciones.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.com.iqc.flow.auditorias.CertificadosPublicables;
import pe.com.iqc.flow.auditorias.CertificadosPublicables.CertificadoPublicable;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.EsquemaCatalogo;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaEdicion;
import pe.com.iqc.flow.certificaciones.MarcasPais;
import pe.com.iqc.flow.certificaciones.MarcasPais.MarcaVigente;
import pe.com.iqc.flow.certificaciones.domain.PublicacionCertificado;
import pe.com.iqc.flow.certificaciones.infrastructure.BasesConsultas;
import pe.com.iqc.flow.certificaciones.infrastructure.BasesConsultas.BaseConsulta;
import pe.com.iqc.flow.certificaciones.infrastructure.PublicacionCertificadoRepository;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.compartido.infrastructure.BloqueoDistribuido;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.documentos.ServicioDocumentos.DocumentoReferencia;

/**
 *copia a la base pública solo lo que se puede mostrar de cada certificado emitido. Compara una huella de lo
 *publicado para escribir solo lo que cambió (un dato del certificado, del cliente, de la marca o el PDF), así no
 *depende de que cada cambio avise: lo que no se pudo publicar se reintenta en la siguiente pasada.
 *Cada certificado va a la base de su país de marca (ADR-043); si cambia de marca se retira de la base anterior
 */
@Service
public class PublicadorCertificados {
  private static final Logger log = LoggerFactory.getLogger(PublicadorCertificados.class);
  /**país cuya consulta imprimió el panel anterior en todos los QR, también en los de otras marcas*/
  static final String PAIS_QR_LEGADO = "PE";
  /**clave del advisory lock de PostgreSQL de la publicación (una pasada a la vez entre todas las instancias)*/
  private static final long BLOQUEO_PUBLICACION = 0x1C0F_0001L;

  private final CertificadosPublicables certificados;
  private final ConsultaClientes clientes;
  private final MarcasPais marcas;
  private final ServicioCatalogos catalogos;
  private final ServicioDocumentos documentos;
  private final PublicacionCertificadoRepository publicaciones;
  private final BasesConsultas bases;
  private final Clock reloj;
  private final BloqueoDistribuido bloqueo;
  private final PublicadorPersonales personales;
  private final ReentrantLock enCurso = new ReentrantLock();
  private volatile ResultadoPublicacion ultima;

  public PublicadorCertificados(CertificadosPublicables certificados, ConsultaClientes clientes, MarcasPais marcas,
      ServicioCatalogos catalogos, ServicioDocumentos documentos, PublicacionCertificadoRepository publicaciones,
      BasesConsultas bases, Clock reloj, BloqueoDistribuido bloqueo, PublicadorPersonales personales) {
    this.certificados = certificados;
    this.clientes = clientes;
    this.marcas = marcas;
    this.catalogos = catalogos;
    this.documentos = documentos;
    this.publicaciones = publicaciones;
    this.bases = bases;
    this.reloj = reloj;
    this.bloqueo = bloqueo;
    this.personales = personales;
  }

  public record ResultadoPublicacion(boolean habilitada, boolean ejecutada, int revisados, int publicados,
      int sinCambios, int fallidos, Instant ejecutadaEn) {
  }

  /**lo que ve el público. Cualquier cambio en estos datos cambia la huella y provoca una nueva publicación*/
  record CertificadoPublico(UUID id, String marcaPais, String marcaNombre, String urlConsulta, String codigo,
      String esquema, String organismoAcreditador, String norma, String normaDesignacion, String alcance, String estado,
      String razonSocial, String tipoDocumento, String numeroDocumento, String paisTitular, LocalDate fechaOtorgamiento,
      LocalDate fechaEmision, LocalDate fechaExpiracion, String urlDocumentoLegado, UUID documentoPdfId,
      List<String> qrsLegados, String tokenSistema) {
  }

  public Optional<ResultadoPublicacion> ultima() { return Optional.ofNullable(ultima); }

  /**
   *publica lo que cambió desde la pasada anterior; con forzar, vuelve a escribir todo (p. ej. tras restaurar la base
   *pública). Si ya hay una pasada en curso, no hace nada
   */
  public ResultadoPublicacion publicar(boolean forzar) {
    Instant ahora = Instant.now(reloj);
    if (!bases.habilitada()) return new ResultadoPublicacion(false, false, 0, 0, 0, 0, ahora);
    if (!enCurso.tryLock()) return new ResultadoPublicacion(true, false, 0, 0, 0, 0, ahora);
    try {
      // Con varias instancias de la API, solo una publica a la vez (ADR-044)
      return bloqueo.siLibre(BLOQUEO_PUBLICACION, () -> pasada(forzar, ahora))
          .orElse(new ResultadoPublicacion(true, false, 0, 0, 0, 0, ahora));
    } finally {
      enCurso.unlock();
    }
  }

  private ResultadoPublicacion pasada(boolean forzar, Instant ahora) {
    {
      List<CertificadoPublicable> lista = certificados.listar();
      Map<UUID, PublicacionCertificado> previas = publicaciones.findAllById(lista.stream().map(CertificadoPublicable::id)
          .toList()).stream().collect(Collectors.toMap(PublicacionCertificado::getCertificadoId, p -> p));
      Map<UUID, Map<UUID, ClienteReferencia>> titulares = new HashMap<>();
      lista.stream().collect(Collectors.groupingBy(CertificadoPublicable::organizacionId)).forEach((org, deOrg) ->
          titulares.put(org, clientes.buscarPorIds(org, deOrg.stream().map(CertificadoPublicable::clienteId)
              .collect(Collectors.toSet()))));
      Map<String, MarcaVigente> marcasUsadas = new HashMap<>();
      int publicados = 0;
      int sinCambios = 0;
      int fallidos = 0;
      for (CertificadoPublicable c : lista) {
        PublicacionCertificado registro = previas.getOrDefault(c.id(), new PublicacionCertificado(c.id()));
        try {
          ClienteReferencia titular = titulares.getOrDefault(c.organizacionId(), Map.of()).get(c.clienteId());
          if (titular == null) throw new IllegalStateException("El cliente del certificado no existe");
          String pais = c.paisMarca() != null ? c.paisMarca() : titular.codigoPais();
          MarcaVigente marca = marcasUsadas.computeIfAbsent(c.organizacionId() + ":" + pais,
              k -> marcas.marca(c.organizacionId(), pais));
          CertificadoPublico publico = publico(c, titular, marca);
          String huella = huella(publico);
          String previo = registro.getPaisPublicado();
          if (!forzar && huella.equals(registro.getHuella()) && marca.codigoPais().equals(previo)) {
            sinCambios++;
            continue;
          }
          BaseConsulta destino = bases.de(marca.codigoPais()).orElseThrow(() -> new IllegalStateException(
              "No hay base de consulta configurada para " + marca.codigoPais()));
          escribir(destino, c, publico);
          redirigirQrsLegados(publico);
          if (previo != null && !previo.equals(marca.codigoPais()) && !bases.comparten(previo, marca.codigoPais())) {
            retirar(previo, c.id());
          }
          registro.publicado(huella, marca.codigoPais(), Instant.now(reloj));
          publicaciones.save(registro);
          publicados++;
        } catch (RuntimeException e) {
          log.warn("No se pudo publicar el certificado {}: {}", c.id(), e.getMessage());
          registro.fallo(e.getMessage(), Instant.now(reloj));
          publicaciones.save(registro);
          fallidos++;
        }
      }
      // Certificados personales en la misma pasada y con el mismo bloqueo
      int[] personal = personales.publicar(forzar);
      publicados += personal[0];
      sinCambios += personal[1];
      fallidos += personal[2];
      ultima = new ResultadoPublicacion(true, true, lista.size() + personal[0] + personal[1] + personal[2], publicados,
          sinCambios, fallidos, ahora);
      if (publicados > 0 || fallidos > 0) {
        log.info("Publicación a la consulta pública: {} publicados, {} sin cambios, {} con error", publicados, sinCambios,
            fallidos);
      }
      return ultima;
    }
  }

  private CertificadoPublico publico(CertificadoPublicable c, ClienteReferencia titular, MarcaVigente marca) {
    String organismo = catalogos.esquema(c.esquema()).map(EsquemaCatalogo::organismoAcreditador).orElse(c.esquema());
    String designacion = catalogos.buscarEdicion(c.norma(), c.edicion()).map(NormaEdicion::designacion)
        .orElse(c.norma() + ":" + c.edicion());
    return new CertificadoPublico(c.id(), marca.codigoPais(), marca.nombre(), marca.urlConsulta(), c.codigo(), c.esquema(),
        organismo, c.norma(), designacion, c.alcance(), c.estado(), titular.razonSocial(), titular.tipoDocumento(),
        titular.numeroDocumento(), titular.codigoPais(), c.fechaOtorgamiento(), c.fechaEmision(), c.fechaExpiracion(),
        c.urlDocumentoLegado(), c.documentoPdfId(), c.qrsLegados(), c.tokenVerificacion().toString());
  }

  /**
   *los QR del panel anterior llevan la consulta de Perú aunque el certificado sea de otra marca: la base de Perú
   *guarda a dónde reenviarlos. Si el certificado es de Perú, no hace falta reenviar nada
   */
  private void redirigirQrsLegados(CertificadoPublico p) {
    Optional<BaseConsulta> peru = bases.de(PAIS_QR_LEGADO);
    if (peru.isEmpty()) return;
    boolean ajeno = !PAIS_QR_LEGADO.equals(p.marcaPais());
    if (!ajeno && !redireccionesDe(peru.get(), p.id())) return;
    Timestamp ahora = Timestamp.from(Instant.now(reloj));
    peru.get().enTransaccion(jdbc -> {
      jdbc.update("DELETE FROM qr_redirecciones WHERE certificado_id = ?", p.id());
      if (!ajeno) return;
      jdbc.batchUpdate("""
          INSERT INTO qr_redirecciones (token, certificado_id, marca_pais, url_destino, actualizado_en)
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT (token) DO UPDATE SET certificado_id = EXCLUDED.certificado_id, marca_pais = EXCLUDED.marca_pais,
            url_destino = EXCLUDED.url_destino, actualizado_en = EXCLUDED.actualizado_en
          """, p.qrsLegados().stream()
          .map(t -> new Object[] {t, p.id(), p.marcaPais(), p.urlConsulta() + t, ahora}).toList());
    });
  }

  private static boolean redireccionesDe(BaseConsulta base, UUID certificadoId) {
    Integer n = base.jdbc().queryForObject("SELECT count(*) FROM qr_redirecciones WHERE certificado_id = ?",
        Integer.class, certificadoId);
    return n != null && n > 0;
  }

  /**quita el certificado de la base de su marca anterior (sus QR y su PDF se borran en cascada)*/
  private void retirar(String pais, UUID certificadoId) {
    bases.de(pais).ifPresentOrElse(
        b -> b.enTransaccion(jdbc -> jdbc.update("DELETE FROM certificados_publicos WHERE id = ?", certificadoId)),
        () -> log.warn("El certificado {} sigue publicado en la base de {}, que ya no está configurada", certificadoId,
            pais));
  }

  private void escribir(BaseConsulta base, CertificadoPublicable c, CertificadoPublico p) {
    DocumentoReferencia pdf = p.documentoPdfId() == null ? null
        : documentos.buscar(c.organizacionId(), p.documentoPdfId()).orElse(null);
    byte[] contenido = pdf == null || pdfIgual(base, c.id(), pdf.sha256()) ? null
        : documentos.leerContenido(c.organizacionId(), pdf.id());
    Timestamp ahora = Timestamp.from(Instant.now(reloj));
    base.enTransaccion(jdbc -> {
      jdbc.update("""
          INSERT INTO certificados_publicos (id, marca_pais, marca_nombre, url_consulta, codigo, esquema,
            organismo_acreditador, norma, norma_designacion, alcance, estado, titular_razon_social, titular_tipo_documento,
            titular_numero_documento, titular_pais, fecha_otorgamiento, fecha_emision, fecha_expiracion,
            url_documento_legado, tiene_pdf, actualizado_en)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT (id) DO UPDATE SET marca_pais = EXCLUDED.marca_pais, marca_nombre = EXCLUDED.marca_nombre,
            url_consulta = EXCLUDED.url_consulta, codigo = EXCLUDED.codigo, esquema = EXCLUDED.esquema,
            organismo_acreditador = EXCLUDED.organismo_acreditador, norma = EXCLUDED.norma,
            norma_designacion = EXCLUDED.norma_designacion, alcance = EXCLUDED.alcance, estado = EXCLUDED.estado,
            titular_razon_social = EXCLUDED.titular_razon_social, titular_tipo_documento = EXCLUDED.titular_tipo_documento,
            titular_numero_documento = EXCLUDED.titular_numero_documento, titular_pais = EXCLUDED.titular_pais,
            fecha_otorgamiento = EXCLUDED.fecha_otorgamiento, fecha_emision = EXCLUDED.fecha_emision,
            fecha_expiracion = EXCLUDED.fecha_expiracion, url_documento_legado = EXCLUDED.url_documento_legado,
            tiene_pdf = EXCLUDED.tiene_pdf, actualizado_en = EXCLUDED.actualizado_en
          """, p.id(), p.marcaPais(), p.marcaNombre(), p.urlConsulta(), p.codigo(), p.esquema(), p.organismoAcreditador(),
          p.norma(), p.normaDesignacion(), p.alcance(), p.estado(), p.razonSocial(), p.tipoDocumento(), p.numeroDocumento(),
          p.paisTitular(), fecha(p.fechaOtorgamiento()), fecha(p.fechaEmision()), fecha(p.fechaExpiracion()),
          p.urlDocumentoLegado(), pdf != null, ahora);
      jdbc.update("DELETE FROM qr_publicos WHERE certificado_id = ?", p.id());
      List<Object[]> qrs = new ArrayList<>();
      p.qrsLegados().forEach(t -> qrs.add(new Object[] {t, p.id(), "LEGADO"}));
      qrs.add(new Object[] {p.tokenSistema(), p.id(), "SISTEMA"});
      jdbc.batchUpdate("""
          INSERT INTO qr_publicos (token, certificado_id, origen) VALUES (?, ?, ?)
          ON CONFLICT (token) DO UPDATE SET certificado_id = EXCLUDED.certificado_id, origen = EXCLUDED.origen
          """, qrs);
      if (pdf == null) {
        jdbc.update("DELETE FROM documentos_publicos WHERE certificado_id = ?", p.id());
      } else if (contenido != null) {
        jdbc.update("""
            INSERT INTO documentos_publicos (certificado_id, tipo_contenido, nombre, sha256, contenido, actualizado_en)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (certificado_id) DO UPDATE SET tipo_contenido = EXCLUDED.tipo_contenido, nombre = EXCLUDED.nombre,
              sha256 = EXCLUDED.sha256, contenido = EXCLUDED.contenido, actualizado_en = EXCLUDED.actualizado_en
            """, p.id(), pdf.tipoContenido(), nombrePdf(p.codigo()), pdf.sha256(), contenido, ahora);
      }
    });
  }

  /**evita volver a copiar el PDF si ya está publicado el mismo archivo*/
  private static boolean pdfIgual(BaseConsulta base, UUID certificadoId, String sha256) {
    JdbcTemplate jdbc = base.jdbc();
    List<String> actual = jdbc.queryForList("SELECT sha256 FROM documentos_publicos WHERE certificado_id = ?",
        String.class, certificadoId);
    return !actual.isEmpty() && sha256.equals(actual.get(0));
  }

  private static String nombrePdf(String codigo) { return codigo.replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf"; }

  private static Date fecha(LocalDate fecha) { return fecha == null ? null : Date.valueOf(fecha); }

  static String huella(CertificadoPublico publico) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(publico.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
