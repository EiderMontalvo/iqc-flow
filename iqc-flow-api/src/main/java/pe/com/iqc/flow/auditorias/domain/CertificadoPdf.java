package pe.com.iqc.flow.auditorias.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**versión del PDF de un certificado. La última es la vigente; las anteriores quedan como historial*/
@Entity
@Immutable
@Table(name = "certificados_pdf")
public class CertificadoPdf {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "certificado_id", nullable = false)
  private UUID certificadoId;

  @Column(name = "documento_id", nullable = false)
  private UUID documentoId;

  @Column(nullable = false)
  private int numero;

  @Column(length = 500)
  private String motivo;

  @Column(name = "subido_por", nullable = false)
  private UUID subidoPor;

  @Column(name = "subido_en", nullable = false)
  private Instant subidoEn;

  protected CertificadoPdf() {
  }

  public CertificadoPdf(UUID certificadoId, UUID documentoId, int numero, String motivo, UUID subidoPor, Instant subidoEn) {
    this.certificadoId = certificadoId;
    this.documentoId = documentoId;
    this.numero = numero;
    this.motivo = motivo;
    this.subidoPor = subidoPor;
    this.subidoEn = subidoEn;
  }

  public UUID getId() { return id; }
  public UUID getCertificadoId() { return certificadoId; }
  public UUID getDocumentoId() { return documentoId; }
  public int getNumero() { return numero; }
  public String getMotivo() { return motivo; }
  public UUID getSubidoPor() { return subidoPor; }
  public Instant getSubidoEn() { return subidoEn; }
}
