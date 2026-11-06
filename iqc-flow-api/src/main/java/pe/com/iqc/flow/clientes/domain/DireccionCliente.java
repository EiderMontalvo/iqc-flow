package pe.com.iqc.flow.clientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**dirección fiscal, sede u oficina de correspondencia de un cliente. Las sedes se usarán en las auditorías. */
@Entity
@Table(name = "direcciones_cliente")
public class DireccionCliente extends EntidadBase {
  @Column(name = "cliente_id", nullable = false, updatable = false)
  private UUID clienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoDireccion tipo;

  @Column(length = 120)
  private String nombre;

  @Column(nullable = false, length = 300)
  private String direccion;

  @Column(length = 100)
  private String distrito;

  @Column(length = 100)
  private String provincia;

  @Column(length = 100)
  private String departamento;

  @Column(name = "codigo_pais", nullable = false, length = 2)
  private String codigoPais;

  @Column(length = 6)
  private String ubigeo;

  @Column(length = 300)
  private String referencia;

  protected DireccionCliente() {
  }

  public DireccionCliente(UUID clienteId) { this.clienteId = clienteId; }

  public void actualizar(TipoDireccion tipo, String nombre, String direccion, String distrito, String provincia,
      String departamento, String codigoPais, String ubigeo, String referencia) {
    String pais = ValidadorDocumento.normalizarPais(codigoPais);
    // Código de ubicación de 6 dígitos: ubigeo INEI en Perú; código DPA del INEC (provincia, cantón, parroquia) en Ecuador
    if (ubigeo != null && !"PE".equals(pais) && !"EC".equals(pais)) {
      throw new ReglaNegocioException("UBIGEO_NO_APLICA",
          "El código de ubicación solo aplica a direcciones en Perú (ubigeo) y Ecuador (código DPA).");
    }
    this.tipo = tipo;
    this.nombre = nombre;
    this.direccion = direccion.trim();
    this.distrito = distrito;
    this.provincia = provincia;
    this.departamento = departamento;
    this.codigoPais = pais;
    this.ubigeo = ubigeo;
    this.referencia = referencia;
  }

  public UUID getClienteId() { return clienteId; }
  public TipoDireccion getTipo() { return tipo; }
  public String getNombre() { return nombre; }
  public String getDireccion() { return direccion; }
  public String getDistrito() { return distrito; }
  public String getProvincia() { return provincia; }
  public String getDepartamento() { return departamento; }
  public String getCodigoPais() { return codigoPais; }
  public String getUbigeo() { return ubigeo; }
  public String getReferencia() { return referencia; }
}
