package pe.com.iqc.flow.identidad.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import pe.com.iqc.flow.compartido.domain.LimiteExcedidoException;

/**rechaza con 429 los inicios de sesión de una IP que superó el límite de fallos*/
class FiltroLimiteInicioSesion extends OncePerRequestFilter {
  private final LimitadorIntentosPorIp limitador;
  private final HandlerExceptionResolver resolutorErrores;

  FiltroLimiteInicioSesion(LimitadorIntentosPorIp limitador, HandlerExceptionResolver resolutorErrores) {
    this.limitador = limitador;
    this.resolutorErrores = resolutorErrores;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest solicitud) {
    return !("POST".equals(solicitud.getMethod())
        && ConfiguracionSeguridad.RUTA_INICIO_SESION.equals(solicitud.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {
    if (limitador.excedido(solicitud.getRemoteAddr())) {
      resolutorErrores.resolveException(solicitud, respuesta, null, new LimiteExcedidoException("DEMASIADOS_INTENTOS",
          "Demasiados intentos de inicio de sesión desde esta dirección. Intenta nuevamente más tarde."));
      return;
    }
    cadena.doFilter(solicitud, respuesta);
  }
}
