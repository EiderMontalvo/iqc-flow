package pe.com.iqc.flow.identidad.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**parámetros de bloqueo de cuentas y límite de intentos por IP (iqc.seguridad.*). */
@ConfigurationProperties("iqc.seguridad")
public record PropiedadesSeguridad(
    @DefaultValue("5") int intentosMaximos,
    @DefaultValue("15m") Duration duracionBloqueo,
    @DefaultValue("20") int limiteIpIntentos,
    @DefaultValue("15m") Duration limiteIpVentana) {
}
