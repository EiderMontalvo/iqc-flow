package pe.com.iqc.flow.certificaciones.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.com.iqc.flow.certificaciones.MarcasPais;
import pe.com.iqc.flow.certificaciones.MarcasPais.MarcaVigente;
import pe.com.iqc.flow.certificaciones.domain.CertificadoPersonal;
import pe.com.iqc.flow.certificaciones.domain.PersonaCertificada;
import pe.com.iqc.flow.certificaciones.domain.PublicacionPersonal;
import pe.com.iqc.flow.certificaciones.infrastructure.BasesConsultas;
import pe.com.iqc.flow.certificaciones.infrastructure.BasesConsultas.BaseConsulta;
import pe.com.iqc.flow.certificaciones.infrastructure.CertificadoPersonalRepository;
import pe.com.iqc.flow.certificaciones.infrastructure.PersonaCertificadaRepository;
import pe.com.iqc.flow.certificaciones.infrastructure.PublicacionPersonalRepository;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.documentos.ServicioDocumentos.DocumentoReferencia;

/**
 *publica los certificados personales en la base de consulta del país de la persona, con la misma lógica que los
 *empresariales: huella de lo publicable, solo lo que cambió y reintento en la siguiente pasada. Los anulados se
 *publican con su estado para que la consulta lo muestre
 */
@Service
public class PublicadorPersonales {
  private static final Logger log = LoggerFactory.getLogger(PublicadorPersonales.class);

  private final CertificadoPersonalRepository certificados;
  private final PersonaCertificadaRepository personas;
  private final PublicacionPersonalRepository publicaciones;
  private final MarcasPais marcas;
  private final ServicioDocumentos documentos;
  private final BasesConsultas bases;
  private final Clock reloj;

  public PublicadorPersonales(CertificadoPersonalRepository certificados, PersonaCertificadaRepository personas,
      PublicacionPersonalRepository publicaciones, MarcasPais marcas, ServicioDocumentos documentos, BasesConsultas bases,
      Clock reloj) {
    this.certificados = certificados;
    this.personas = personas;
    this.publicaciones = publicaciones;
    this.marcas = marcas;
    this.documentos = documentos;
    this.bases = bases;
    this.reloj = reloj;
  }

  record PersonalPublico(UUID id, String marcaPais, String marcaNombre, String urlConsulta, String codigo, String nombres,
      String tipoDocumento, String numeroDocumento, String curso, String normaReferencia, String normaAnio, String subtitulo,
      String descripcion, BigDecimal duracionHoras, String modalidad, LocalDate fechaInicio, LocalDate fechaFin,
      LocalDate fechaEmision, String entrenador, String estado, UUID tokenPdf, UUID documentoPdfId) {
  }

  /**[publicados, sin cambios, fallidos]*/
  public int[] publicar(boolean forzar) {
    int[] cuenta = new int[3];
    List<CertificadoPersonal> lista = certificados.findAll();
    if (lista.isEmpty()) return cuenta;
    Map<UUID, PersonaCertificada> porId = personas.findByIdIn(lista.stream().map(CertificadoPersonal::getPersonaId).toList())
        .stream().collect(Collectors.toMap(PersonaCertificada::getId, Function.identity()));
    Map<UUID, PublicacionPersonal> previas = publicaciones.findAllById(lista.stream().map(CertificadoPersonal::getId).toList())
        .stream().collect(Collectors.toMap(PublicacionPersonal::getCertificadoId, Function.identity()));
    for (CertificadoPersonal c : lista) {
      PublicacionPersonal registro = previas.getOrDefault(c.getId(), new PublicacionPersonal(c.getId()));
      try {
        PersonaCertificada p = porId.get(c.getPersonaId());
        if (p == null) throw new IllegalStateException("La persona del certificado no existe");
        MarcaVigente marca = marcas.marca(c.getOrganizacionId(), p.getCodigoPais());
        PersonalPublico publico = new PersonalPublico(c.getId(), marca.codigoPais(), marca.nombre(), marca.urlConsulta(),
            c.getCodigo(), p.getNombres(), p.getTipoDocumento(), p.getNumeroDocumento(), c.getCurso(), c.getNormaReferencia(),
            c.getNormaAnio(), c.getSubtitulo(), c.getDescripcion(), c.getDuracionHoras(), c.getModalidad(), c.getFechaInicio(),
            c.getFechaFin(), c.getFechaEmision(), c.getEntrenador(), c.getEstado().name(), c.getTokenPdf(), c.getDocumentoPdfId());
        String huella = huella(publico);
        String previo = registro.getPaisPublicado();
        if (!forzar && huella.equals(registro.getHuella()) && marca.codigoPais().equals(previo)) {
          cuenta[1]++;
          continue;
        }
        BaseConsulta destino = bases.de(marca.codigoPais()).orElseThrow(() -> new IllegalStateException(
            "No hay base de consulta configurada para " + marca.codigoPais()));
        escribir(destino, c, publico);
        if (previo != null && !previo.equals(marca.codigoPais()) && !bases.comparten(previo, marca.codigoPais())) {
          bases.de(previo).ifPresent(b -> b.enTransaccion(j -> j.update("DELETE FROM personales_publicos WHERE id = ?", c.getId())));
        }
        registro.publicado(huella, marca.codigoPais(), Instant.now(reloj));
        publicaciones.save(registro);
        cuenta[0]++;
      } catch (RuntimeException e) {
        log.warn("No se pudo publicar el certificado personal {}: {}", c.getId(), e.getMessage());
        registro.fallo(e.getMessage(), Instant.now(reloj));
        publicaciones.save(registro);
        cuenta[2]++;
      }
    }
    return cuenta;
  }

  private void escribir(BaseConsulta base, CertificadoPersonal c, PersonalPublico p) {
    DocumentoReferencia pdf = p.documentoPdfId() == null ? null
        : documentos.buscar(c.getOrganizacionId(), p.documentoPdfId()).orElse(null);
    boolean mismoPdf = pdf != null && base.jdbc().queryForList(
        "SELECT sha256 FROM documentos_personales_publicos WHERE certificado_id = ?", String.class, p.id()).contains(pdf.sha256());
    byte[] contenido = pdf == null || mismoPdf ? null : documentos.leerContenido(c.getOrganizacionId(), pdf.id());
    Timestamp ahora = Timestamp.from(Instant.now(reloj));
    base.enTransaccion(jdbc -> {
      jdbc.update("""
          INSERT INTO personales_publicos (id, marca_pais, marca_nombre, url_consulta, codigo, titular_nombres,
            titular_tipo_documento, titular_numero_documento, curso, norma_referencia, norma_anio, subtitulo, descripcion,
            duracion_horas, modalidad, fecha_inicio, fecha_fin, fecha_emision, entrenador, estado, token_pdf, tiene_pdf,
            actualizado_en)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT (id) DO UPDATE SET marca_pais = EXCLUDED.marca_pais, marca_nombre = EXCLUDED.marca_nombre,
            url_consulta = EXCLUDED.url_consulta, codigo = EXCLUDED.codigo, titular_nombres = EXCLUDED.titular_nombres,
            titular_tipo_documento = EXCLUDED.titular_tipo_documento, titular_numero_documento = EXCLUDED.titular_numero_documento,
            curso = EXCLUDED.curso, norma_referencia = EXCLUDED.norma_referencia, norma_anio = EXCLUDED.norma_anio,
            subtitulo = EXCLUDED.subtitulo, descripcion = EXCLUDED.descripcion, duracion_horas = EXCLUDED.duracion_horas,
            modalidad = EXCLUDED.modalidad, fecha_inicio = EXCLUDED.fecha_inicio, fecha_fin = EXCLUDED.fecha_fin,
            fecha_emision = EXCLUDED.fecha_emision, entrenador = EXCLUDED.entrenador, estado = EXCLUDED.estado,
            token_pdf = EXCLUDED.token_pdf, tiene_pdf = EXCLUDED.tiene_pdf, actualizado_en = EXCLUDED.actualizado_en
          """, p.id(), p.marcaPais(), p.marcaNombre(), p.urlConsulta(), p.codigo(), p.nombres(), p.tipoDocumento(),
          p.numeroDocumento(), p.curso(), p.normaReferencia(), p.normaAnio(), p.subtitulo(), p.descripcion(), p.duracionHoras(),
          p.modalidad(), fecha(p.fechaInicio()), fecha(p.fechaFin()), fecha(p.fechaEmision()), p.entrenador(), p.estado(),
          p.tokenPdf(), pdf != null, ahora);
      if (pdf == null) {
        jdbc.update("DELETE FROM documentos_personales_publicos WHERE certificado_id = ?", p.id());
      } else if (contenido != null) {
        jdbc.update("""
            INSERT INTO documentos_personales_publicos (certificado_id, tipo_contenido, nombre, sha256, contenido, actualizado_en)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (certificado_id) DO UPDATE SET tipo_contenido = EXCLUDED.tipo_contenido, nombre = EXCLUDED.nombre,
              sha256 = EXCLUDED.sha256, contenido = EXCLUDED.contenido, actualizado_en = EXCLUDED.actualizado_en
            """, p.id(), pdf.tipoContenido(), "certificado-personal.pdf", pdf.sha256(), contenido, ahora);
      }
    });
  }

  private static Date fecha(LocalDate f) { return f == null ? null : Date.valueOf(f); }

  static String huella(PersonalPublico p) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
