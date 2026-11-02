package pe.com.iqc.flow.compartido.seguridad;

import java.util.Optional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**acceso al usuario que ejecuta la operación actual. */
@Component
public class ActorActual {
  public Optional<UsuarioAutenticado> obtener() {
    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion != null && autenticacion.isAuthenticated()
        && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
      return Optional.of(usuario);
    }
    return Optional.empty();
  }

  public UsuarioAutenticado requerido() {
    return obtener().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Se requiere iniciar sesión"));
  }

  public boolean tienePermiso(String permiso) {
    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    return autenticacion != null && autenticacion.getAuthorities().stream()
        .anyMatch(autoridad -> permiso.equals(autoridad.getAuthority()));
  }
}
