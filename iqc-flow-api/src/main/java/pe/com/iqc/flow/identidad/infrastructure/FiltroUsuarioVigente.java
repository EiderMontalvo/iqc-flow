package pe.com.iqc.flow.identidad.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import pe.com.iqc.flow.compartido.domain.OperacionNoPermitidaException;

/**
 *en cada solicitud con sesión recarga el usuario desde la base de datos: si fue deshabilitado
 *pierde la sesión y sus permisos siempre reflejan los roles actuales. Además, mientras tenga
 *pendiente el cambio de contraseña obligatorio, solo puede usar las rutas de autenticación.
 */
class FiltroUsuarioVigente extends OncePerRequestFilter {
  private static final Set<String> RUTAS_CON_CAMBIO_PENDIENTE = Set.of(
      "/api/v1/autenticacion/yo",
      "/api/v1/autenticacion/csrf",
      "/api/v1/autenticacion/cambio-contrasena",
      ConfiguracionSeguridad.RUTA_CIERRE_SESION);

  private final ServicioDetallesUsuario detallesUsuario;
  private final HandlerExceptionResolver resolutorErrores;
  private final SecurityContextHolderStrategy estrategia = SecurityContextHolder.getContextHolderStrategy();

  FiltroUsuarioVigente(ServicioDetallesUsuario detallesUsuario, HandlerExceptionResolver resolutorErrores) {
    this.detallesUsuario = detallesUsuario;
    this.resolutorErrores = resolutorErrores;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {
    Authentication actual = estrategia.getContext().getAuthentication();
    if (actual != null && actual.getPrincipal() instanceof PrincipalUsuario principal) {
      Optional<PrincipalUsuario> vigente = detallesUsuario.cargarVigente(principal.id());
      if (vigente.isEmpty()) {
        estrategia.clearContext();
        HttpSession sesion = solicitud.getSession(false);
        if (sesion != null) sesion.invalidate();
      } else {
        PrincipalUsuario actualizado = vigente.get();
        actualizado.eraseCredentials();
        SecurityContext contexto = estrategia.createEmptyContext();
        contexto.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(actualizado, null, actualizado.getAuthorities()));
        estrategia.setContext(contexto);
        if (actualizado.debeCambiarContrasena() && !RUTAS_CON_CAMBIO_PENDIENTE.contains(solicitud.getRequestURI())) {
          resolutorErrores.resolveException(solicitud, respuesta, null, new OperacionNoPermitidaException(
              "CAMBIO_CONTRASENA_REQUERIDO", "Debes cambiar tu contraseña antes de continuar."));
          return;
        }
      }
    }
    cadena.doFilter(solicitud, respuesta);
  }
}
