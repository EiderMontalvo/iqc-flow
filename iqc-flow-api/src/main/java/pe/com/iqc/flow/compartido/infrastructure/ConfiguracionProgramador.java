package pe.com.iqc.flow.compartido.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**activa los procesos programados (revisión de vencimientos, automatizaciones). Las pruebas lo desactivan*/
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "iqc.programador", name = "habilitado", havingValue = "true", matchIfMissing = true)
public class ConfiguracionProgramador {
}
