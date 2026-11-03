package pe.com.iqc.flow.identidad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;

/**conjunto de permisos. Sin organización es un rol del sistema disponible para todas*/
@Entity
@Table(name = "roles")
public class Rol extends EntidadBase {
  @Column(name = "organizacion_id")
  private UUID organizacionId;

  @Column(nullable = false, length = 60)
  private String codigo;

  @Column(nullable = false, length = 120)
  private String nombre;

  @Column(length = 500)
  private String descripcion;

  @Column(name = "es_sistema", nullable = false)
  private boolean esSistema;

  @ManyToMany
  @JoinTable(name = "roles_permisos",
      joinColumns = @JoinColumn(name = "rol_id"),
      inverseJoinColumns = @JoinColumn(name = "permiso_codigo"))
  private Set<Permiso> permisos = new HashSet<>();

  protected Rol() {
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getCodigo() { return codigo; }
  public String getNombre() { return nombre; }
  public String getDescripcion() { return descripcion; }
  public boolean isEsSistema() { return esSistema; }
  public Set<Permiso> getPermisos() { return Set.copyOf(permisos); }
}
