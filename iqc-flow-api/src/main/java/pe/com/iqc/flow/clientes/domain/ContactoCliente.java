package pe.com.iqc.flow.clientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**persona de contacto de un cliente. Sus datos son personales: la bitácora los guarda enmascarados. */
@Entity
@Table(name = "contactos_cliente")
public class ContactoCliente extends EntidadBase {
  @Column(name = "cliente_id", nullable = false, updatable = false)
  private UUID clienteId;

  @Column(name = "nombre_completo", nullable = false, length = 200)
  private String nombreCompleto;

  @Column(length = 120)
  private String cargo;

  @Column(length = 320)
  private String correo;

  @Column(length = 40)
  private String telefono;

  @Convert(converter = ConvertidorRolesContacto.class)
  @Column(nullable = false, length = 200)
  private Set<RolContacto> roles = EnumSet.noneOf(RolContacto.class);

  @Column(name = "es_principal", nullable = false)
  private boolean esPrincipal;

  @Column(nullable = false)
  private boolean activo = true;

  @Column(length = 1000)
  private String notas;

  protected ContactoCliente() {
  }

  public ContactoCliente(UUID clienteId) { this.clienteId = clienteId; }

  public void actualizar(String nombreCompleto, String cargo, String correo, String telefono, Set<RolContacto> roles,
      boolean activo, String notas) {
    if (correo == null && telefono == null) {
      throw new ReglaNegocioException("CONTACTO_SIN_MEDIO", "Registra al menos un correo o un teléfono del contacto.");
    }
    this.nombreCompleto = nombreCompleto.trim();
    this.cargo = cargo;
    this.correo = correo == null ? null : correo.trim().toLowerCase(java.util.Locale.ROOT);
    this.telefono = telefono;
    this.roles = roles == null || roles.isEmpty() ? EnumSet.noneOf(RolContacto.class) : EnumSet.copyOf(roles);
    this.activo = activo;
    this.notas = notas;
    if (!activo) this.esPrincipal = false;
  }

  public void marcarPrincipal(boolean principal) {
    if (principal && !activo) {
      throw new ReglaNegocioException("CONTACTO_INACTIVO", "Un contacto inactivo no puede ser el principal.");
    }
    this.esPrincipal = principal;
  }

  public UUID getClienteId() { return clienteId; }
  public String getNombreCompleto() { return nombreCompleto; }
  public String getCargo() { return cargo; }
  public String getCorreo() { return correo; }
  public String getTelefono() { return telefono; }
  public Set<RolContacto> getRoles() { return roles.isEmpty() ? Set.of() : EnumSet.copyOf(roles); }
  public boolean isEsPrincipal() { return esPrincipal; }
  public boolean isActivo() { return activo; }
  public String getNotas() { return notas; }
}
