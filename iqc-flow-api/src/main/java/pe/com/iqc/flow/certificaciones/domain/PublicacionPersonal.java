package pe.com.iqc.flow.certificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**estado de la publicación de un certificado personal en la base pública de su país*/
@Entity
@Table(name = "publicaciones_personales")
public class PublicacionPersonal {
  @Id
  @Column(name = "certificado_id")
  private UUID certificadoId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 64)
  private String huella;

  @Column(name = "pais_publicado", length = 2)
  private String paisPublicado;

  @Column(name = "publicado_en")
  private Instant publicadoEn;

  @Column(name = "intentos_fallidos", nullable = false)
  private int intentosFallidos;

  @Column(name = "ultimo_error", length = 1000)
  private String ultimoError;

  @Column(name = "ultimo_intento_en")
  private Instant ultimoIntentoEn;

  protected PublicacionPersonal() {
  }

  public PublicacionPersonal(UUID certificadoId) { this.certificadoId = certificadoId; }

  public void publicado(String huella, String pais, Instant ahora) {
    this.huella = huella;
    this.paisPublicado = pais;
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
  public String getPaisPublicado() { return paisPublicado; }
  public int getIntentosFallidos() { return intentosFallidos; }
}
