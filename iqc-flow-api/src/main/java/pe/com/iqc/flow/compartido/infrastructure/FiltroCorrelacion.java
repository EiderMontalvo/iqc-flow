package pe.com.iqc.flow.compartido.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**propaga el identificador de correlación en logs (MDC), respuestas y bitácora*/
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {
  public static final String CABECERA = "X-Correlation-Id";
  public static final String CLAVE_MDC = "correlationId";
  private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {
    String recibido = solicitud.getHeader(CABECERA);
    String id = recibido != null && FORMATO_VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();
    MDC.put(CLAVE_MDC, id);
    respuesta.setHeader(CABECERA, id);
    try {
      cadena.doFilter(solicitud, respuesta);
    } finally {
      MDC.remove(CLAVE_MDC);
    }
  }
}
