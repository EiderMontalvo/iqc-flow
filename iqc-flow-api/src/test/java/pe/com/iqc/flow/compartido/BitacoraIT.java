package pe.com.iqc.flow.compartido;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

class BitacoraIT extends PruebaIntegracion {
  @Test
  void laBaseDeDatosImpideModificarOEliminarRegistros() {
    assertThatThrownBy(() -> jdbc.update("UPDATE bitacora SET accion = 'ALTERADA'"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("solo inserción");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM bitacora"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("solo inserción");
  }

  @Test
  void consultaElHistorialDeUnaEntidad() throws Exception {
    crearUsuario("ana@iqcperu.com", false, CodigosRol.MASTER);
    MockHttpSession sesion = iniciarSesion("ana@iqcperu.com");
    String creado = mvc.perform(post("/api/v1/usuarios").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"correo": "luis@iqcperu.com", "nombre": "Luis", "contrasenaInicial": "Inicial-Segura-2026"}
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(creado, "$.id");

    mvc.perform(get("/api/v1/bitacora").param("entidadTipo", "USUARIO").param("entidadId", id).session(sesion))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].accion").value("USUARIO_CREADO"))
        .andExpect(jsonPath("$.contenido[0].tipoActor").value("USUARIO"))
        .andExpect(jsonPath("$.contenido[0].estadoNuevo.correo").value("luis@iqcperu.com"));
  }

  @Test
  void unComercialNoPuedeLeerLaBitacora() throws Exception {
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    MockHttpSession sesion = iniciarSesion("comercial@iqcperu.com");

    mvc.perform(get("/api/v1/bitacora").session(sesion)).andExpect(status().isForbidden());
  }
}
