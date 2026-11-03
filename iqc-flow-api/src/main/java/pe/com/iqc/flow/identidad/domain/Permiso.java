package pe.com.iqc.flow.identidad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**permiso granular con formato recurso:acción, p. ej. "usuarios:leer". Lo definen las migraciones*/
@Entity
@Immutable
@Table(name = "permisos")
public class Permiso {
  @Id
  @Column(length = 80)
  private String codigo;

  @Column(nullable = false, length = 40)
  private String modulo;

  @Column(nullable = false, length = 255)
  private String descripcion;

  protected Permiso() {
  }

  public String getCodigo() { return codigo; }
  public String getModulo() { return modulo; }
  public String getDescripcion() { return descripcion; }
}
