package pe.com.iqc.flow.catalogos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Immutable;

/**norma de sistema de gestión; puede tener varias ediciones (p. ej. ISO/IEC 27001:2013 y 2022)*/
@Entity
@Immutable
@Table(name = "normas")
public class Norma {
  @Id
  @Column(length = 20)
  private String codigo;

  @Column(name = "nombre_corto", nullable = false, length = 40)
  private String nombreCorto;

  @Column(nullable = false, length = 200)
  private String titulo;

  @Column(name = "codigo_sistema", nullable = false, length = 2)
  private String codigoSistema;

  @Column(name = "codigo_operaciones", length = 5)
  private String codigoOperaciones;

  @Column(nullable = false)
  private int orden;

  @Column(nullable = false)
  private boolean activo;

  @OneToMany(mappedBy = "normaCodigo")
  @OrderBy("edicion")
  private List<EdicionNorma> ediciones = new ArrayList<>();

  protected Norma() {
  }

  public String getCodigo() { return codigo; }
  public String getNombreCorto() { return nombreCorto; }
  public String getTitulo() { return titulo; }
  public String getCodigoSistema() { return codigoSistema; }
  public String getCodigoOperaciones() { return codigoOperaciones; }
  public int getOrden() { return orden; }
  public boolean isActivo() { return activo; }
  public List<EdicionNorma> getEdiciones() { return List.copyOf(ediciones); }
}
