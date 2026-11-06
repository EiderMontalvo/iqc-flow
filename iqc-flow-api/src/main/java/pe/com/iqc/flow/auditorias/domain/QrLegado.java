package pe.com.iqc.flow.auditorias.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**QR impreso por la plataforma anterior: su identificador cifrado lleva al certificado importado (ADR-003)*/
@Entity
@Table(name = "qr_legado")
public class QrLegado {
  @Id
  @Column(name = "encriptacion_id", length = 60)
  private String encriptacionId;

  @Column(name = "id_qr", nullable = false, length = 40)
  private String idQr;

  @Column(name = "organizacion_id", nullable = false)
  private UUID organizacionId;

  @Column(name = "certificado_id", nullable = false)
  private UUID certificadoId;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "registrado_en")
  private Instant registradoEn;

  @Column(name = "importado_en", nullable = false)
  private Instant importadoEn;

  protected QrLegado() {
  }

  public QrLegado(String encriptacionId, String idQr, UUID organizacionId, UUID certificadoId, boolean activo,
      Instant registradoEn, Instant importadoEn) {
    this.encriptacionId = encriptacionId;
    this.idQr = idQr;
    this.organizacionId = organizacionId;
    this.certificadoId = certificadoId;
    this.activo = activo;
    this.registradoEn = registradoEn;
    this.importadoEn = importadoEn;
  }

  public String getEncriptacionId() { return encriptacionId; }
  public String getIdQr() { return idQr; }
  public UUID getCertificadoId() { return certificadoId; }
  public boolean isActivo() { return activo; }
}
