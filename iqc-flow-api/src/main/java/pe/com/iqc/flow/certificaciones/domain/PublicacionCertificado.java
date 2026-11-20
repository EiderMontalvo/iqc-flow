package pe.com.iqc.flow.certificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**estado de la publicación de un certificado en la base pública: qué se publicó (huella) y el último error*/
@Entity
@Table(name = "publicaciones_certificados")
public class PublicacionCertificado {
  @Id
  @Column(name = "certificado_id")
  private UUID certificadoId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 64)
  private String huella;

  @Column(name = "publicado_en")
  private Instant publicadoEn;

  @Column(name = "intentos_fallidos", nullable = false)
  private int intentosFallidos;

  @Column(name = "ultimo_error", length = 1000)
  private String ultimoError;

  @Column(name = "ultimo_intento_en")
  private Instant ultimoIntentoEn;

  protected PublicacionCertificado() {
  }

  public PublicacionCertificado(UUID certificadoId) { this.certificadoId = certificadoId; }

  public void publicado(String huella, Instant ahora) {
    this.huella = huella;
    this.publicadoEn = ahora;
    this.ultimoIntentoEn = ahora;
    this.intentosFallidos = 0;
    this.ultimoError = null;
  }

  public void fallo(String error, Instant ahora) {
    this.intentosFallidos++;
    this.ultimoIntentoEn = ahora;
    this.ultimoError = error == null ? "Error desconocido" : error.length() > 1000 ? error.substring(0, 1000) : error;
  }

  public UUID getCertificadoId() { return certificadoId; }
  public String getHuella() { return huella; }
  public Instant getPublicadoEn() { return publicadoEn; }
  public int getIntentosFallidos() { return intentosFallidos; }
  public String getUltimoError() { return ultimoError; }
  public Instant getUltimoIntentoEn() { return ultimoIntentoEn; }
}
