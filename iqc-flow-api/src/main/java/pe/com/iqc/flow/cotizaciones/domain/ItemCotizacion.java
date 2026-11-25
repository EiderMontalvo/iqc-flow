package pe.com.iqc.flow.cotizaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**línea de una cotización: una norma (o un servicio sin norma), sus días auditor y su monto sin impuesto*/
@Entity
@Table(name = "cotizaciones_items")
public class ItemCotizacion {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false)
  private int orden;

  @Column(name = "norma_codigo", length = 20)
  private String normaCodigo;

  @Column(length = 10)
  private String edicion;

  @Column(nullable = false, length = 500)
  private String descripcion;

  @Column(name = "dias_auditor", nullable = false, precision = 6, scale = 2)
  private BigDecimal diasAuditor;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal monto;

  protected ItemCotizacion() {
  }

  public ItemCotizacion(int orden, String normaCodigo, String edicion, String descripcion, BigDecimal diasAuditor, BigDecimal monto) {
    this.orden = orden;
    this.normaCodigo = normaCodigo;
    this.edicion = edicion;
    this.descripcion = descripcion;
    this.diasAuditor = diasAuditor;
    this.monto = monto;
  }

  public UUID getId() { return id; }
  public int getOrden() { return orden; }
  public String getNormaCodigo() { return normaCodigo; }
  public String getEdicion() { return edicion; }
  public String getDescripcion() { return descripcion; }
  public BigDecimal getDiasAuditor() { return diasAuditor; }
  public BigDecimal getMonto() { return monto; }
}
