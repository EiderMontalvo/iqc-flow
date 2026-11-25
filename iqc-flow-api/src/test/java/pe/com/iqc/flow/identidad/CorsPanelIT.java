package pe.com.iqc.flow.identidad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import pe.com.iqc.flow.PruebaIntegracion;

/**el panel alojado en otro dominio (BanaHosting) llama a la API con cookies solo desde los orígenes configurados*/
@TestPropertySource(properties = "iqc.seguridad.origenes-permitidos=https://panel.iqcperu.com/")
class CorsPanelIT extends PruebaIntegracion {

  @Test
  void elOrigenDelPanelPuedeEnviarCookiesYElTokenCsrf() throws Exception {
    mvc.perform(options("/api/v1/certificados")
            .header("Origin", "https://panel.iqcperu.com")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://panel.iqcperu.com"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    mvc.perform(get("/api/v1/autenticacion/csrf").header("Origin", "https://panel.iqcperu.com"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://panel.iqcperu.com"));
  }

  @Test
  void unOrigenAjenoSeRechaza() throws Exception {
    mvc.perform(options("/api/v1/certificados")
            .header("Origin", "https://otro-sitio.example")
            .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }
}
