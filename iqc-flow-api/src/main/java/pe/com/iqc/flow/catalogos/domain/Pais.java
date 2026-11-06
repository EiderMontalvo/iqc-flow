package pe.com.iqc.flow.catalogos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**país con su documento fiscal, moneda, impuesto y nombres de los niveles de dirección*/
@Entity
@Immutable
@Table(name = "paises")
public class Pais {
  @Id
  @Column(length = 2)
  private String codigo;

  @Column(nullable = false, length = 100)
  private String nombre;

  @Column(name = "documento_fiscal", nullable = false, length = 10)
  private String documentoFiscal;

  @Column(nullable = false, length = 3)
  private String moneda;

  @Column(name = "nombre_impuesto", nullable = false, length = 20)
  private String nombreImpuesto;

  @Column(name = "tasa_impuesto", nullable = false, precision = 5, scale = 2)
  private BigDecimal tasaImpuesto;

  @Column(name = "etiqueta_nivel1", nullable = false, length = 40)
  private String etiquetaNivel1;

  @Column(name = "etiqueta_nivel2", nullable = false, length = 40)
  private String etiquetaNivel2;

  @Column(name = "etiqueta_nivel3", nullable = false, length = 40)
  private String etiquetaNivel3;

  @Column(name = "etiqueta_codigo_ubicacion", length = 40)
  private String etiquetaCodigoUbicacion;

  @Column(name = "zona_horaria", nullable = false, length = 60)
  private String zonaHoraria;

  @Column(nullable = false)
  private boolean activo;

  protected Pais() {
  }

  public String getCodigo() { return codigo; }
  public String getNombre() { return nombre; }
  public String getDocumentoFiscal() { return documentoFiscal; }
  public String getMoneda() { return moneda; }
  public String getNombreImpuesto() { return nombreImpuesto; }
  public BigDecimal getTasaImpuesto() { return tasaImpuesto; }
  public String getEtiquetaNivel1() { return etiquetaNivel1; }
  public String getEtiquetaNivel2() { return etiquetaNivel2; }
  public String getEtiquetaNivel3() { return etiquetaNivel3; }
  public String getEtiquetaCodigoUbicacion() { return etiquetaCodigoUbicacion; }
  public String getZonaHoraria() { return zonaHoraria; }
  public boolean isActivo() { return activo; }
}
