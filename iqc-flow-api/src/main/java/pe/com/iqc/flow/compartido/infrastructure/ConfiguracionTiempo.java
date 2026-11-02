package pe.com.iqc.flow.compartido.infrastructure;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionTiempo {
  /**reloj único de la aplicación; los tests pueden reemplazarlo por uno fijo. */
  @Bean
  Clock reloj(@Value("${iqc.zona-horaria}") ZoneId zonaHoraria) { return Clock.system(zonaHoraria); }
}
