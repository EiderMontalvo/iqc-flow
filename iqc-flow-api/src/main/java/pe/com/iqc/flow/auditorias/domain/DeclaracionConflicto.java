package pe.com.iqc.flow.auditorias.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *declaración de conflicto de interés de un miembro del equipo para una auditoría (estudio de conflictos).
 *Con conflicto, ese auditor no puede integrar el equipo ni la auditoría registrarse como ejecutada
 */
@Entity
@Table(name = "declaraciones_conflicto")
public class DeclaracionConflicto extends EntidadBase {
  /**cómo se firmó la declaración*/
  public enum Modo {
    /**el auditor tiene usuario vinculado y la firmó en IQC Flow*/
    FIRMA_PROPIA,
    /**el coordinador la registró en nombre de un auditor (p. ej. externo) y adjuntó la declaración firmada*/
    EN_NOMBRE_DEL_AUDITOR
  }

  @Column(name = "auditoria_id", nullable = false, updatable = false)
  private UUID auditoriaId;

  @Column(name = "auditor_id", nullable = false, updatable = false)
  private UUID auditorId;

  @Column(name = "tiene_conflicto", nullable = false)
  private boolean tieneConflicto;

  @Column(length = 2000)
  private String detalle;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 25)
  private Modo modo;

  @Column(name = "documento_id")
  private UUID documentoId;

  @Column(name = "registrado_por", nullable = false)
  private UUID registradoPor;

  @Column(name = "declarado_en", nullable = false)
  private Instant declaradoEn;

  protected DeclaracionConflicto() {
  }

  public DeclaracionConflicto(UUID auditoriaId, UUID auditorId) {
    this.auditoriaId = auditoriaId;
    this.auditorId = auditorId;
  }

  public void declarar(boolean tieneConflicto, String detalle, Modo modo, UUID documentoId, UUID registradoPor,
      Instant ahora) {
    if (tieneConflicto && (detalle == null || detalle.isBlank())) {
      throw new ReglaNegocioException("DETALLE_REQUERIDO", "Describe el conflicto de interés declarado.");
    }
    if (modo == Modo.EN_NOMBRE_DEL_AUDITOR && documentoId == null) {
      throw new ReglaNegocioException("DECLARACION_FIRMADA_REQUERIDA",
          "Adjunta la declaración firmada por el auditor (documento DECLARACION_CONFLICTO).");
    }
    this.tieneConflicto = tieneConflicto;
    this.detalle = detalle;
    this.modo = modo;
    this.documentoId = documentoId;
    this.registradoPor = registradoPor;
    this.declaradoEn = ahora;
  }

  public UUID getAuditoriaId() { return auditoriaId; }
  public UUID getAuditorId() { return auditorId; }
  public boolean isTieneConflicto() { return tieneConflicto; }
  public String getDetalle() { return detalle; }
  public Modo getModo() { return modo; }
  public UUID getDocumentoId() { return documentoId; }
  public UUID getRegistradoPor() { return registradoPor; }
  public Instant getDeclaradoEn() { return declaradoEn; }
}
