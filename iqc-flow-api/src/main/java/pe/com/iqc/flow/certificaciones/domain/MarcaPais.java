package pe.com.iqc.flow.certificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import pe.com.iqc.flow.certificaciones.MarcasPais.MarcaVigente;

/**marca configurada de un país; sin fila se usa la predeterminada (MarcasPredeterminadas)*/
@Entity
@Table(name = "marcas_pais")
public class MarcaPais {
  @EmbeddedId
  private Clave clave;

  @Column(nullable = false, length = 80)
  private String nombre;

  @Column(name = "url_consulta", nullable = false)
  private String urlConsulta;

  @Column(name = "sitio_web")
  private String sitioWeb;

  private String correo;

  @Column(length = 60)
  private String telefono;

  @Column(length = 500)
  private String direccion;

  @Column(name = "logo_documento_id")
  private UUID logoDocumentoId;

  @Column(name = "actualizado_por")
  private UUID actualizadoPor;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  private long version;

  protected MarcaPais() {
  }

  public MarcaPais(UUID organizacionId, String codigoPais) { this.clave = new Clave(organizacionId, codigoPais); }

  public void definir(String nombre, String urlConsulta, String sitioWeb, String correo, String telefono,
      String direccion, UUID logoDocumentoId, UUID actor, Instant ahora) {
    this.nombre = nombre;
    this.urlConsulta = urlConsulta;
    this.sitioWeb = sitioWeb;
    this.correo = correo;
    this.telefono = telefono;
    this.direccion = direccion;
    this.logoDocumentoId = logoDocumentoId;
    this.actualizadoPor = actor;
    this.actualizadoEn = ahora;
  }

  public MarcaVigente vigente() {
    return new MarcaVigente(clave.codigoPais, nombre, urlConsulta, sitioWeb, correo, telefono, direccion,
        logoDocumentoId, false);
  }

  public String getCodigoPais() { return clave.codigoPais; }
  public UUID getActualizadoPor() { return actualizadoPor; }
  public Instant getActualizadoEn() { return actualizadoEn; }
  public long getVersion() { return version; }

  @Embeddable
  public static class Clave implements Serializable {
    @Column(name = "organizacion_id")
    private UUID organizacionId;

    @Column(name = "codigo_pais", length = 2)
    private String codigoPais;

    protected Clave() {
    }

    public Clave(UUID organizacionId, String codigoPais) {
      this.organizacionId = organizacionId;
      this.codigoPais = codigoPais;
    }

    @Override
    public boolean equals(Object otro) {
      return otro instanceof Clave c && Objects.equals(organizacionId, c.organizacionId)
          && Objects.equals(codigoPais, c.codigoPais);
    }

    @Override
    public int hashCode() { return Objects.hash(organizacionId, codigoPais); }
  }
}
