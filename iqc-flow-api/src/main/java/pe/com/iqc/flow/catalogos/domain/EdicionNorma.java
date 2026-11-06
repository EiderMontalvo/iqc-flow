package pe.com.iqc.flow.catalogos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**edición de una norma (año). Solo las vigentes se ofrecen para nuevas auditorías*/
@Entity
@Immutable
@Table(name = "ediciones_norma")
public class EdicionNorma {
  @Id
  private UUID id;

  @Column(name = "norma_codigo", nullable = false, length = 20)
  private String normaCodigo;

  @Column(nullable = false, length = 20)
  private String edicion;

  @Column(nullable = false)
  private boolean vigente;

  @Column(length = 500)
  private String notas;

  /** letra que la edición agrega al código del certificado (ISO 37001:2025 → "A")*/
  @Column(name = "sufijo_codigo", length = 2)
  private String sufijoCodigo;

  protected EdicionNorma() {
  }

  public UUID getId() { return id; }
  public String getNormaCodigo() { return normaCodigo; }
  public String getEdicion() { return edicion; }
  public boolean isVigente() { return vigente; }
  public String getNotas() { return notas; }
  public String getSufijoCodigo() { return sufijoCodigo; }
}
