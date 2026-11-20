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
import pe.com.iqc.flow.certificaciones.infrastructure.BaseConsultas;
import pe.com.iqc.flow.certificaciones.infrastructure.PublicacionCertificadoRepository;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.documentos.ServicioDocumentos.DocumentoReferencia;

/**
 *copia a la base pública solo lo que se puede mostrar de cada certificado emitido. Compara una huella de lo
 *publicado para escribir solo lo que cambió (un dato del certificado, del cliente, de la marca o el PDF), así no
 *depende de que cada cambio avise: lo que no se pudo publicar se reintenta en la siguiente pasada
 */
@Service
public class PublicadorCertificados {
  private static final Logger log = LoggerFactory.getLogger(PublicadorCertificados.class);

  private final CertificadosPublicables certificados;
  private final ConsultaClientes clientes;
  private final MarcasPais marcas;
  private final ServicioCatalogos catalogos;
  private final ServicioDocumentos documentos;
  private final PublicacionCertificadoRepository publicaciones;
  private final BaseConsultas base;
  private final Clock reloj;
  private final ReentrantLock enCurso = new ReentrantLock();
  private volatile ResultadoPublicacion ultima;

  public PublicadorCertificados(CertificadosPublicables certificados, ConsultaClientes clientes, MarcasPais marcas,
      ServicioCatalogos catalogos, ServicioDocumentos documentos, PublicacionCertificadoRepository publicaciones,
      BaseConsultas base, Clock reloj) {
    this.certificados = certificados;
    this.clientes = clientes;
    this.marcas = marcas;
    this.catalogos = catalogos;
    this.documentos = documentos;
    this.publicaciones = publicaciones;
    this.base = base;
    this.reloj = reloj;
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
    if (!base.habilitada()) return new ResultadoPublicacion(false, false, 0, 0, 0, 0, ahora);
    if (!enCurso.tryLock()) return new ResultadoPublicacion(true, false, 0, 0, 0, 0, ahora);
    try {
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
          if (!forzar && huella.equals(registro.getHuella())) {
            sinCambios++;
            continue;
          }
          escribir(c, publico);
          registro.publicado(huella, Instant.now(reloj));
          publicaciones.save(registro);
          publicados++;
        } catch (RuntimeException e) {
          log.warn("No se pudo publicar el certificado {}: {}", c.id(), e.getMessage());
          registro.fallo(e.getMessage(), Instant.now(reloj));
          publicaciones.save(registro);
          fallidos++;
        }
      }
      ultima = new ResultadoPublicacion(true, true, lista.size(), publicados, sinCambios, fallidos, ahora);
      if (publicados > 0 || fallidos > 0) {
        log.info("Publicación a la consulta pública: {} publicados, {} sin cambios, {} con error", publicados, sinCambios,
            fallidos);
      }
      return ultima;
    } finally {
      enCurso.unlock();
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

  private void escribir(CertificadoPublicable c, CertificadoPublico p) {
    DocumentoReferencia pdf = p.documentoPdfId() == null ? null
        : documentos.buscar(c.organizacionId(), p.documentoPdfId()).orElse(null);
    byte[] contenido = pdf == null || pdfIgual(c.id(), pdf.sha256()) ? null
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
  private boolean pdfIgual(UUID certificadoId, String sha256) {
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
