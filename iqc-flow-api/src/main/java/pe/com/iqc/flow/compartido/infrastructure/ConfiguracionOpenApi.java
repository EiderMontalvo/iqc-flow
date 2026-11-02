package pe.com.iqc.flow.compartido.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionOpenApi {
  @Bean
  OpenAPI documentacionApi() {
    return new OpenAPI().info(new Info()
        .title("IQC Flow API")
        .version("v1")
        .description("API interna de IQC Flow. Autenticación por sesión (cookie IQCFLOWSESSION) y token CSRF "
            + "en la cabecera X-XSRF-TOKEN para las operaciones que modifican datos."));
  }
}
