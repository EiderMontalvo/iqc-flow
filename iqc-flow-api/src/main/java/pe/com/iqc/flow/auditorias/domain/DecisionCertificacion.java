package pe.com.iqc.flow.auditorias.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;

/**registro de una decisión de certificación (DDC-FMR-008). No se modifica: una corrección es otra decisión*/
@Entity
@Table(name = "decisiones_certificacion")
public class DecisionCertificacion extends EntidadBase {
  /**clasificación de la revisión técnica del expediente*/
  public enum Clasificacion { CONFORME, OBSERVADO, ACEPTABLE_CON_OBSERVACIONES }

  /**quién decide: el decisor habilitado o, si tiene conflicto de interés, el Comité de Imparcialidad*/
  public enum Via { DECISOR, COMITE_IMPARCIALIDAD }

  @Column(name = "expediente_id", nullable = false, updatable = false)
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 25)
  private TipoDecision tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 30)
  private Clasificacion clasificacion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 25)
  private Via via;

  @Column(name = "decisor_usuario_id", updatable = false)
  private UUID decisorUsuarioId;

  @Column(name = "acta_documento_id", updatable = false)
  private UUID actaDocumentoId;

  @Column(name = "experto_imparcialidad", updatable = false, length = 200)
  private String expertoImparcialidad;

  @Column(name = "recomendacion_experto", updatable = false, length = 4000)
  private String recomendacionExperto;

  @Column(name = "checklist_documento_id", updatable = false)
  private UUID checklistDocumentoId;

  @Column(name = "fecha_decision", nullable = false, updatable = false)
  private LocalDate fechaDecision;

  @Column(nullable = false, updatable = false, length = 4000)
  private String fundamento;

  @Column(name = "nuevo_alcance", updatable = false, length = 4000)
  private String nuevoAlcance;

  @Column(name = "registrado_por", nullable = false, updatable = false)
  private UUID registradoPor;

  @ElementCollection
  @CollectionTable(name = "decisiones_normas", joinColumns = @JoinColumn(name = "decision_id"))
  @Column(name = "norma_codigo")
  private Set<String> normas = new HashSet<>();

  protected DecisionCertificacion() {
  }

  public DecisionCertificacion(UUID expedienteId, TipoDecision tipo, Clasificacion clasificacion, Via via,
      UUID decisorUsuarioId, UUID actaDocumentoId, String expertoImparcialidad, String recomendacionExperto,
      UUID checklistDocumentoId, LocalDate fechaDecision, String fundamento, String nuevoAlcance,
      Collection<String> normas, UUID registradoPor) {
    this.expedienteId = expedienteId;
    this.tipo = tipo;
    this.clasificacion = clasificacion;
    this.via = via;
    this.decisorUsuarioId = decisorUsuarioId;
    this.actaDocumentoId = actaDocumentoId;
    this.expertoImparcialidad = expertoImparcialidad;
    this.recomendacionExperto = recomendacionExperto;
    this.checklistDocumentoId = checklistDocumentoId;
    this.fechaDecision = fechaDecision;
    this.fundamento = fundamento;
    this.nuevoAlcance = nuevoAlcance;
    this.normas.addAll(normas);
    this.registradoPor = registradoPor;
  }

  public UUID getExpedienteId() { return expedienteId; }
  public TipoDecision getTipo() { return tipo; }
  public Clasificacion getClasificacion() { return clasificacion; }
  public Via getVia() { return via; }
  public UUID getDecisorUsuarioId() { return decisorUsuarioId; }
  public UUID getActaDocumentoId() { return actaDocumentoId; }
  public String getExpertoImparcialidad() { return expertoImparcialidad; }
  public String getRecomendacionExperto() { return recomendacionExperto; }
  public UUID getChecklistDocumentoId() { return checklistDocumentoId; }
  public LocalDate getFechaDecision() { return fechaDecision; }
  public String getFundamento() { return fundamento; }
  public String getNuevoAlcance() { return nuevoAlcance; }
  public UUID getRegistradoPor() { return registradoPor; }
  public Set<String> getNormas() { return Set.copyOf(normas); }
}
