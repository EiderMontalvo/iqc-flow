package pe.com.iqc.flow.identidad.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import pe.com.iqc.flow.identidad.application.ServicioAutenticacion;

/**respuestas JSON del inicio y cierre de sesión, con su registro en bitácora*/
@Component
class ManejadoresSesion implements AuthenticationSuccessHandler, AuthenticationFailureHandler, LogoutSuccessHandler {
  private final ServicioAutenticacion servicioAutenticacion;
  private final LimitadorIntentosPorIp limitador;
  private final HandlerExceptionResolver resolutorErrores;

  ManejadoresSesion(ServicioAutenticacion servicioAutenticacion, LimitadorIntentosPorIp limitador,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutorErrores) {
    this.servicioAutenticacion = servicioAutenticacion;
    this.limitador = limitador;
    this.resolutorErrores = resolutorErrores;
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest solicitud, HttpServletResponse respuesta,
      Authentication autenticacion) {
    PrincipalUsuario principal = (PrincipalUsuario) autenticacion.getPrincipal();
    servicioAutenticacion.registrarInicioExitoso(principal.id(), principal.organizacionId());
    //el token CSRF se renueva al iniciar sesión; cargarlo aquí envía la nueva cookie en esta misma respuesta
    if (solicitud.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) token.getToken();
    respuesta.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

  @Override
  public void onAuthenticationFailure(HttpServletRequest solicitud, HttpServletResponse respuesta,
      AuthenticationException excepcion) {
    limitador.registrarFallo(solicitud.getRemoteAddr());
    servicioAutenticacion.registrarInicioFallido(solicitud.getParameter("correo"), excepcion.getClass().getSimpleName());
    resolutorErrores.resolveException(solicitud, respuesta, null, excepcion);
  }

  @Override
  public void onLogoutSuccess(HttpServletRequest solicitud, HttpServletResponse respuesta,
      Authentication autenticacion) {
    if (autenticacion != null && autenticacion.getPrincipal() instanceof PrincipalUsuario principal) {
      servicioAutenticacion.registrarCierreSesion(principal.id(), principal.organizacionId());
    }
    respuesta.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }
}
