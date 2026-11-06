package pe.com.iqc.flow.organizaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**valor configurado de un parámetro; si no hay fila se usa el valor por defecto del parámetro*/
@Entity
@Table(name = "parametros_organizacion")
public class ValorParametro {
  @EmbeddedId
  private Clave clave;

  @Column(nullable = false, length = 200)
  private String valor;

  @Column(name = "actualizado_por")
  private UUID actualizadoPor;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected ValorParametro() {
  }

  public ValorParametro(UUID organizacionId, String clave, String valor, UUID actualizadoPor, Instant ahora) {
    this.clave = new Clave(organizacionId, clave);
    cambiar(valor, actualizadoPor, ahora);
  }

  public void cambiar(String valor, UUID actualizadoPor, Instant ahora) {
    this.valor = valor;
    this.actualizadoPor = actualizadoPor;
    this.actualizadoEn = ahora;
  }

  public String getValor() { return valor; }
  public UUID getActualizadoPor() { return actualizadoPor; }
  public Instant getActualizadoEn() { return actualizadoEn; }

  @Embeddable
  public static class Clave implements Serializable {
    @Column(name = "organizacion_id")
    private UUID organizacionId;

    @Column(name = "clave", length = 60)
    private String clave;

    protected Clave() {
    }

    public Clave(UUID organizacionId, String clave) {
      this.organizacionId = organizacionId;
      this.clave = clave;
    }

    @Override
    public boolean equals(Object otro) {
      return otro instanceof Clave c && Objects.equals(organizacionId, c.organizacionId) && Objects.equals(clave, c.clave);
    }

    @Override
    public int hashCode() { return Objects.hash(organizacionId, clave); }
  }
}
