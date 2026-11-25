package pe.com.iqc.flow.identidad.infrastructure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 *parámetros de bloqueo de cuentas, límite de intentos por IP y orígenes del panel (iqc.seguridad.*).
 *origenesPermitidos: dominios desde los que el panel llama a la API cuando se aloja aparte (CORS con cookies), por
 *ejemplo https://panel.iqcperu.com. Vacío: solo el mismo origen
 */
@ConfigurationProperties("iqc.seguridad")
public record PropiedadesSeguridad(
    @DefaultValue("5") int intentosMaximos,
    @DefaultValue("15m") Duration duracionBloqueo,
    @DefaultValue("20") int limiteIpIntentos,
    @DefaultValue("15m") Duration limiteIpVentana,
    List<String> origenesPermitidos) {

  public PropiedadesSeguridad {
    origenesPermitidos = origenesPermitidos == null ? List.of()
        : origenesPermitidos.stream().map(String::trim).filter(o -> !o.isEmpty()).map(o -> o.replaceAll("/+$", "")).toList();
  }
}
