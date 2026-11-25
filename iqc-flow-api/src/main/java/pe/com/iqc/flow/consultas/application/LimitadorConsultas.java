package pe.com.iqc.flow.consultas.application;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.consultas.infrastructure.PropiedadesPortal;

/**límite de consultas por IP y minuto, para frenar la enumeración de códigos (ventana fija en memoria)*/
@Component
public class LimitadorConsultas {
  private record Ventana(long minuto, int cuenta) {
  }

  private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();
  private final int maximo;
  private final Clock reloj;

  public LimitadorConsultas(PropiedadesPortal propiedades, Clock reloj) {
    this.maximo = propiedades.consultasPorMinuto();
    this.reloj = reloj;
  }

  public boolean permitir(String ip) {
    long minuto = reloj.millis() / 60_000;
    if (ventanas.size() > 50_000) ventanas.entrySet().removeIf(e -> e.getValue().minuto() < minuto);
    Ventana v = ventanas.merge(ip, new Ventana(minuto, 1),
        (vieja, nueva) -> vieja.minuto() == minuto ? new Ventana(minuto, vieja.cuenta() + 1) : nueva);
    return v.cuenta() <= maximo;
  }
}
