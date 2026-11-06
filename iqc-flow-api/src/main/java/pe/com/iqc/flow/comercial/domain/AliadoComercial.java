package pe.com.iqc.flow.comercial.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;

/**aliado comercial que refiere clientes (SPONSOR del cuadro de control). La comisión es variable por servicio*/
@Entity
@Table(name = "aliados_comerciales")
public class AliadoComercial extends EntidadBase {
  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Column(nullable = false, length = 10)
  private String codigo;

  @Column(nullable = false, length = 200)
  private String nombre;

  @Column(name = "comision_porcentaje", precision = 5, scale = 2)
  private BigDecimal comisionPorcentaje;

  @Column(nullable = false)
  private boolean activo = true;

  @Column(length = 1000)
  private String notas;

  protected AliadoComercial() {
  }

  public AliadoComercial(UUID organizacionId) { this.organizacionId = organizacionId; }

  public void actualizar(String codigo, String nombre, BigDecimal comisionPorcentaje, boolean activo, String notas) {
    this.codigo = codigo.trim().toUpperCase(Locale.ROOT);
    this.nombre = nombre.trim();
    this.comisionPorcentaje = comisionPorcentaje;
    this.activo = activo;
    this.notas = notas;
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getCodigo() { return codigo; }
  public String getNombre() { return nombre; }
  public BigDecimal getComisionPorcentaje() { return comisionPorcentaje; }
  public boolean isActivo() { return activo; }
  public String getNotas() { return notas; }
}
