package pe.com.iqc.flow.certificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *certificado de una persona (curso o competencia). Se emite al registrarse; anularlo exige un motivo y lo retira de
 *la consulta pública. token_pdf identifica su PDF en consultas.iqcperu.com/personal-pdf/{token}
 */
@Entity
@Table(name = "certificados_personales")
public class CertificadoPersonal extends EntidadBase {
  public enum Estado { EMITIDO, ANULADO }

  /**datos editables del certificado (todo salvo persona, código y estado)*/
  public record Datos(String curso, String normaReferencia, String normaAnio, String subtitulo, String descripcion,
      BigDecimal duracionHoras, String modalidad, LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaEmision,
      String entrenador) {
  }

  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Column(name = "persona_id", nullable = false)
  private UUID personaId;

  @Column(nullable = false, length = 64, updatable = false)
  private String codigo;

  @Column(nullable = false)
  private String curso;

  @Column(name = "norma_referencia", length = 120)
  private String normaReferencia;

  @Column(name = "norma_anio", length = 4)
  private String normaAnio;

  private String subtitulo;

  private String descripcion;

  @Column(name = "duracion_horas", precision = 6, scale = 2)
  private BigDecimal duracionHoras;

  @Column(length = 50)
  private String modalidad;

  @Column(name = "fecha_inicio")
  private LocalDate fechaInicio;

  @Column(name = "fecha_fin")
  private LocalDate fechaFin;

  @Column(name = "fecha_emision", nullable = false)
  private LocalDate fechaEmision;

  private String entrenador;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private Estado estado;

  @Column(name = "motivo_anulacion", length = 500)
  private String motivoAnulacion;

  @Column(name = "documento_pdf_id")
  private UUID documentoPdfId;

  @Column(name = "url_documento_legado", length = 2048, updatable = false)
  private String urlDocumentoLegado;

  @Column(name = "token_pdf", nullable = false, updatable = false)
  private UUID tokenPdf;

  @Column(name = "fuente_legado", length = 40, updatable = false)
  private String fuenteLegado;

  @Column(name = "id_legado", length = 60, updatable = false)
  private String idLegado;

  protected CertificadoPersonal() {
  }

  public CertificadoPersonal(UUID organizacionId, UUID personaId, String codigo, Datos datos, UUID documentoPdfId,
      String urlDocumentoLegado, UUID tokenPdf, String fuenteLegado, String idLegado) {
    this.organizacionId = organizacionId;
    this.personaId = personaId;
    this.codigo = codigo;
    this.estado = Estado.EMITIDO;
    this.documentoPdfId = documentoPdfId;
    this.urlDocumentoLegado = urlDocumentoLegado;
    this.tokenPdf = tokenPdf == null ? UUID.randomUUID() : tokenPdf;
    this.fuenteLegado = fuenteLegado;
    this.idLegado = idLegado;
    aplicar(datos);
  }

  /**corrige los datos del certificado; un certificado anulado ya no cambia*/
  public void corregir(Datos datos) {
    exigirEmitido();
    aplicar(datos);
  }

  public void anular(String motivo) {
    exigirEmitido();
    this.estado = Estado.ANULADO;
    this.motivoAnulacion = motivo;
  }

  public void asignarPdf(UUID documentoId) {
    exigirEmitido();
    this.documentoPdfId = documentoId;
  }

  private void aplicar(Datos d) {
    if (d.fechaInicio() != null && d.fechaFin() != null && d.fechaFin().isBefore(d.fechaInicio())) {
      throw new ReglaNegocioException("FECHAS_INVALIDAS", "La fecha de fin no puede ser anterior a la de inicio.");
    }
    if (d.duracionHoras() != null && d.duracionHoras().signum() <= 0) {
      throw new ReglaNegocioException("DURACION_INVALIDA", "La duración debe ser mayor que cero.");
    }
    this.curso = d.curso();
    this.normaReferencia = d.normaReferencia();
    this.normaAnio = d.normaAnio();
    this.subtitulo = d.subtitulo();
    this.descripcion = d.descripcion();
    this.duracionHoras = d.duracionHoras();
    this.modalidad = d.modalidad();
    this.fechaInicio = d.fechaInicio();
    this.fechaFin = d.fechaFin();
    this.fechaEmision = d.fechaEmision();
    this.entrenador = d.entrenador();
  }

  private void exigirEmitido() {
    if (estado == Estado.ANULADO) {
      throw new ReglaNegocioException("CERTIFICADO_ANULADO", "Un certificado anulado ya no se modifica.");
    }
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public UUID getPersonaId() { return personaId; }
  public String getCodigo() { return codigo; }
  public String getCurso() { return curso; }
  public String getNormaReferencia() { return normaReferencia; }
  public String getNormaAnio() { return normaAnio; }
  public String getSubtitulo() { return subtitulo; }
  public String getDescripcion() { return descripcion; }
  public BigDecimal getDuracionHoras() { return duracionHoras; }
  public String getModalidad() { return modalidad; }
  public LocalDate getFechaInicio() { return fechaInicio; }
  public LocalDate getFechaFin() { return fechaFin; }
  public LocalDate getFechaEmision() { return fechaEmision; }
  public String getEntrenador() { return entrenador; }
  public Estado getEstado() { return estado; }
  public String getMotivoAnulacion() { return motivoAnulacion; }
  public UUID getDocumentoPdfId() { return documentoPdfId; }
  public String getUrlDocumentoLegado() { return urlDocumentoLegado; }
  public UUID getTokenPdf() { return tokenPdf; }
}
