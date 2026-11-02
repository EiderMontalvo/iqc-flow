package pe.com.iqc.flow.identidad.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 *ventana deslizante en memoria de inicios de sesión fallidos por IP. Complementa el bloqueo por
 *cuenta frente a ataques que prueban muchos correos desde una misma dirección.
 */
@Component
public class LimitadorIntentosPorIp {
  private static final int MAXIMO_IPS_RASTREADAS = 10_000;

  private final Map<String, Deque<Instant>> fallosPorIp = new ConcurrentHashMap<>();
  private final PropiedadesSeguridad propiedades;
  private final Clock reloj;

  public LimitadorIntentosPorIp(PropiedadesSeguridad propiedades, Clock reloj) {
    this.propiedades = propiedades;
    this.reloj = reloj;
  }

  public boolean excedido(String ip) {
    Deque<Instant> fallos = fallosPorIp.get(ip);
    if (fallos == null) return false;
    synchronized (fallos) {
      descartarAntiguos(fallos, Instant.now(reloj));
      return fallos.size() >= propiedades.limiteIpIntentos();
    }
  }

  public void registrarFallo(String ip) {
    Instant ahora = Instant.now(reloj);
    if (fallosPorIp.size() >= MAXIMO_IPS_RASTREADAS) limpiar(ahora);
    Deque<Instant> fallos = fallosPorIp.computeIfAbsent(ip, clave -> new ArrayDeque<>());
    synchronized (fallos) {
      descartarAntiguos(fallos, ahora);
      fallos.addLast(ahora);
    }
  }

  private void limpiar(Instant ahora) {
    fallosPorIp.values().forEach(fallos -> {
      synchronized (fallos) {
        descartarAntiguos(fallos, ahora);
      }
    });
    fallosPorIp.values().removeIf(Deque::isEmpty);
  }

  private void descartarAntiguos(Deque<Instant> fallos, Instant ahora) {
    Instant limite = ahora.minus(propiedades.limiteIpVentana());
    while (!fallos.isEmpty() && !fallos.peekFirst().isAfter(limite)) fallos.pollFirst();
  }
}
