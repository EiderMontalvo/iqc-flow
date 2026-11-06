package pe.com.iqc.flow.catalogos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**esquema bajo el que se certifica (INACAL, SAE, IQC Global): define sello, prefijo del código y ciclo*/
@Entity
@Immutable
@Table(name = "esquemas_acreditacion")
public class EsquemaAcreditacion {
  @Id
  @Column(length = 20)
  private String codigo;

  @Column(nullable = false, length = 120)
  private String nombre;

  @Column(name = "organismo_acreditador", nullable = false, length = 200)
  private String organismoAcreditador;

  @Column(name = "pais_acreditador", length = 2)
  private String paisAcreditador;

  @Column(name = "prefijo_certificado", length = 5)
  private String prefijoCertificado;

  @Column(name = "anios_ciclo", nullable = false)
  private int aniosCiclo;

  @Column(name = "vigilancias_por_ciclo", nullable = false)
  private int vigilanciasPorCiclo;

  @Column(name = "decide_iqc_peru")
  private Boolean decideIqcPeru;

  @Column(length = 1000)
  private String notas;

  @Column(nullable = false)
  private boolean activo;

  protected EsquemaAcreditacion() {
  }

  public String getCodigo() { return codigo; }
  public String getNombre() { return nombre; }
  public String getOrganismoAcreditador() { return organismoAcreditador; }
  public String getPaisAcreditador() { return paisAcreditador; }
  public String getPrefijoCertificado() { return prefijoCertificado; }
  public int getAniosCiclo() { return aniosCiclo; }
  public int getVigilanciasPorCiclo() { return vigilanciasPorCiclo; }
  public Boolean getDecideIqcPeru() { return decideIqcPeru; }
  public String getNotas() { return notas; }
  public boolean isActivo() { return activo; }
}
