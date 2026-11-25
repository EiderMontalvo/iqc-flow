package pe.com.iqc.flow.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

/**«Olvidé mi contraseña» sin correo: aviso a los administradores y contraseña temporal*/
class RecuperacionAccesoIT extends PruebaIntegracion {

  @Test
  void elOlvidoAvisaAlAdministradorYLaContrasenaTemporalObligaACambiarla() throws Exception {
    crearUsuario("master@iqcperu.com", false, CodigosRol.MASTER);
    Usuario olvidadizo = crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);

    // Público y con la misma respuesta exista o no la cuenta
    mvc.perform(post("/api/v1/autenticacion/olvido-contrasena").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"correo\": \"Comercial@iqcperu.com\"}")).andExpect(status().isAccepted());
    mvc.perform(post("/api/v1/autenticacion/olvido-contrasena").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"correo\": \"nadie@iqcperu.com\"}")).andExpect(status().isAccepted());
    Long avisos = jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE tipo = 'ACCESO_SOLICITADO' AND sujeto_id = ?",
        Long.class, olvidadizo.getId());
    assertThat(avisos).isEqualTo(1);
    // Repetirlo el mismo día no duplica el aviso
    mvc.perform(post("/api/v1/autenticacion/olvido-contrasena").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"correo\": \"comercial@iqcperu.com\"}")).andExpect(status().isAccepted());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE tipo = 'ACCESO_SOLICITADO'", Long.class)).isEqualTo(1);

    // Solo quien gestiona usuarios asigna la temporal
    MockHttpSession comercial = iniciarSesion("comercial@iqcperu.com");
    String cuerpo = "{\"contrasenaTemporal\": \"Temporal-Segura-2026!\", \"version\": " + olvidadizo.getVersion() + "}";
    mvc.perform(post("/api/v1/usuarios/" + olvidadizo.getId() + "/contrasena-temporal").session(comercial).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
    MockHttpSession master = iniciarSesion("master@iqcperu.com");
    mvc.perform(post("/api/v1/usuarios/" + olvidadizo.getId() + "/contrasena-temporal").session(master).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarContrasena").value(true));
    // Entra con la temporal y queda obligado a cambiarla
    MockHttpSession conTemporal = iniciarSesion("comercial@iqcperu.com", "Temporal-Segura-2026!");
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/clientes").session(conTemporal))
        .andExpect(jsonPath("$.codigo").value("CAMBIO_CONTRASENA_REQUERIDO"));
  }
}
