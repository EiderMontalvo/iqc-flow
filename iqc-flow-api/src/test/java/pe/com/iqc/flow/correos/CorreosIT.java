package pe.com.iqc.flow.correos;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**configuración del correo: solo quien administra la ve; y sin cuenta en el servidor, la prueba dice por qué no envía*/
class CorreosIT extends PruebaIntegracion {
  private MockHttpSession master;
  private MockHttpSession comercial;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    master = iniciarSesion("gerencia@iqcperu.com");
    comercial = iniciarSesion("comercial@iqcperu.com");
  }

  @Test
  void soloQuienConfiguraVeLasCuentasYElEstadoNoTraeClaveAlguna() throws Exception {
    mvc.perform(get("/api/v1/correos/estado").session(comercial)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/correos/estado").session(master)).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.cuenta == 'administracion')].configurado").value(false))
        .andExpect(jsonPath("$[?(@.cuenta == 'gmail')].configurado").value(false))
        .andExpect(jsonPath("$[0].clave").doesNotExist());
  }

  @Test
  void laPruebaSinCredencialesResponde422ConUnCodigoClaro() throws Exception {
    mvc.perform(post("/api/v1/correos/prueba").session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"cuenta\": \"administracion\", \"para\": \"yo@ejemplo.pe\"}"))
        .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("CORREO_NO_CONFIGURADO"));
    mvc.perform(post("/api/v1/correos/prueba").session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"cuenta\": \"inventada\", \"para\": \"yo@ejemplo.pe\"}"))
        .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("CORREO_CUENTA_DESCONOCIDA"));
  }

  @Test
  void laPruebaValidaLosDatosYExigePermiso() throws Exception {
    mvc.perform(post("/api/v1/correos/prueba").session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"cuenta\": \"administracion\", \"para\": \"no-es-correo\"}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/correos/prueba").session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"cuenta\": \"x y\", \"para\": \"yo@ejemplo.pe\"}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/correos/prueba").session(comercial).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"cuenta\": \"administracion\", \"para\": \"yo@ejemplo.pe\"}")).andExpect(status().isForbidden());
  }
}
