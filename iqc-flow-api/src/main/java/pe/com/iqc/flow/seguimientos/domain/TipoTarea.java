package pe.com.iqc.flow.seguimientos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**catálogo de tipos de tarea, definido por las migraciones*/
@Entity
@Table(name = "tipos_tarea")
public class TipoTarea {
  @Id
  @Column(length = 40)
  private String codigo;

  @Column(nullable = false, length = 120)
  private String nombre;

  @Enumerated(EnumType.STRING)
  @Column(name = "prioridad_por_defecto", nullable = false, length = 10)
  private PrioridadTarea prioridadPorDefecto;

  @Column(nullable = false)
  private int orden;

  @Column(nullable = false)
  private boolean activo;

  protected TipoTarea() {
  }

  public String getCodigo() { return codigo; }
  public String getNombre() { return nombre; }
  public PrioridadTarea getPrioridadPorDefecto() { return prioridadPorDefecto; }
  public int getOrden() { return orden; }
  public boolean isActivo() { return activo; }
}
