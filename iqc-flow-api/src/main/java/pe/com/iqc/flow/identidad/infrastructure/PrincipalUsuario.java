package pe.com.iqc.flow.identidad.infrastructure;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.identidad.domain.Usuario;

/**usuario autenticado. Las autoridades son los códigos de permiso (p. ej. "usuarios:leer"). */
public final class PrincipalUsuario implements UserDetails, CredentialsContainer, UsuarioAutenticado, Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  private final UUID id;
  private final UUID organizacionId;
  private final String correo;
  private final String nombre;
  private String hashContrasena;
  private final boolean activo;
  private final boolean bloqueado;
  private final boolean debeCambiarContrasena;
  private final Set<String> roles;
  private final List<SimpleGrantedAuthority> autoridades;

  private PrincipalUsuario(Usuario usuario, Instant ahora) {
    this.id = usuario.getId();
    this.organizacionId = usuario.getOrganizacionId();
    this.correo = usuario.getCorreo();
    this.nombre = usuario.getNombre();
    this.hashContrasena = usuario.getHashContrasena();
    this.activo = usuario.estaActivo();
    this.bloqueado = usuario.estaBloqueado(ahora);
    this.debeCambiarContrasena = usuario.isDebeCambiarContrasena();
    this.roles = Set.copyOf(usuario.codigosRoles());
    this.autoridades = usuario.permisos().stream().map(SimpleGrantedAuthority::new).toList();
  }

  static PrincipalUsuario de(Usuario usuario, Instant ahora) { return new PrincipalUsuario(usuario, ahora); }

  @Override public UUID id() { return id; }
  @Override public UUID organizacionId() { return organizacionId; }
  @Override public String correo() { return correo; }
  public String nombre() { return nombre; }
  public boolean debeCambiarContrasena() { return debeCambiarContrasena; }
  public Set<String> roles() { return roles; }

  @Override public String getUsername() { return correo; }
  @Override public String getPassword() { return hashContrasena; }
  @Override public Collection<? extends GrantedAuthority> getAuthorities() { return autoridades; }
  @Override public boolean isEnabled() { return activo; }
  @Override public boolean isAccountNonLocked() { return !bloqueado; }
  @Override public void eraseCredentials() { hashContrasena = null; }
}
