package pe.com.iqc.flow.organizaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.com.iqc.flow.compartido.domain.EntidadBase;

/**organización usuaria de IQC Flow (tenant). Al inicio solo existe IQC Perú*/
@Entity
@Table(name = "organizaciones")
public class Organizacion extends EntidadBase {
  @Column(nullable = false, unique = true, length = 40)
  private String codigo;

  @Column(nullable = false, length = 200)
  private String nombre;

  @Column(name = "codigo_pais", nullable = false, length = 2)
  private String codigoPais;

  @Column(name = "zona_horaria", nullable = false, length = 60)
  private String zonaHoraria;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoOrganizacion estado;

  protected Organizacion() {
  }

  public Organizacion(String codigo, String nombre, String codigoPais, String zonaHoraria) {
    this.codigo = codigo;
    this.nombre = nombre;
    this.codigoPais = codigoPais;
    this.zonaHoraria = zonaHoraria;
    this.estado = EstadoOrganizacion.ACTIVA;
  }

  public String getCodigo() { return codigo; }
  public String getNombre() { return nombre; }
  public String getCodigoPais() { return codigoPais; }
  public String getZonaHoraria() { return zonaHoraria; }
  public EstadoOrganizacion getEstado() { return estado; }
}
