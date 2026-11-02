package pe.com.iqc.flow.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

class AutenticacionIT extends PruebaIntegracion {
  @Test
  void inicioDeSesionCorrectoDevuelveUsuarioRolesYPermisos() throws Exception {
    crearUsuario("ana@iqcperu.com", false, CodigosRol.MASTER);

    MockHttpSession sesion = iniciarSesion(" ANA@IQCperu.com ");

    mvc.perform(get("/api/v1/autenticacion/yo").session(sesion))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correo").value("ana@iqcperu.com"))
        .andExpect(jsonPath("$.organizacion.codigo").value("IQC_PERU"))
        .andExpect(jsonPath("$.roles", hasItem("MASTER")))
        .andExpect(jsonPath("$.permisos", hasItem("usuarios:gestionar")))
        .andExpect(jsonPath("$.debeCambiarContrasena").value(false));
    assertThat(contarBitacora("INICIO_SESION", "EXITO")).isEqualTo(1);
  }

  @Test
  void contrasenaIncorrectaYCorreoInexistenteDevuelvenElMismoError() throws Exception {
    crearUsuario("ana@iqcperu.com", false);

    mvc.perform(post(RUTA_INICIO_SESION).with(csrf()).param("correo", "ana@iqcperu.com").param("contrasena", "incorrecta"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    mvc.perform(post(RUTA_INICIO_SESION).with(csrf()).param("correo", "nadie@iqcperu.com").param("contrasena", CLAVE))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

    assertThat(contarBitacora("INICIO_SESION", "FALLIDO")).isEqualTo(2);
  }

  @Test
  void bloqueaLaCuentaAlAlcanzarLosIntentosMaximos() throws Exception {
    Usuario ana = crearUsuario("ana@iqcperu.com", false);
    for (int i = 0; i < 5; i++) {
      mvc.perform(post(RUTA_INICIO_SESION).with(csrf()).param("correo", "ana@iqcperu.com").param("contrasena", "mala-" + i))
          .andExpect(status().isUnauthorized());
    }

    // Con la cuenta bloqueada, ni la contraseña correcta permite entrar
    mvc.perform(post(RUTA_INICIO_SESION).with(csrf()).param("correo", "ana@iqcperu.com").param("contrasena", CLAVE))
        .andExpect(status().isUnauthorized());

    assertThat(usuarios.findById(ana.getId()).orElseThrow().getBloqueadoHasta()).isNotNull();
    assertThat(contarBitacora("BLOQUEO_CUENTA", "EXITO")).isEqualTo(1);
  }

  @Test
  void inicioDeSesionSinTokenCsrfEsRechazado() throws Exception {
    crearUsuario("ana@iqcperu.com", false);

    mvc.perform(post(RUTA_INICIO_SESION).param("correo", "ana@iqcperu.com").param("contrasena", CLAVE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));
  }

  @Test
  void elCambioDeContrasenaObligatorioRestringeElAccesoHastaRealizarse() throws Exception {
    crearUsuario("ana@iqcperu.com", true, CodigosRol.MASTER);
    MockHttpSession sesion = iniciarSesion("ana@iqcperu.com");

    mvc.perform(get("/api/v1/usuarios").session(sesion))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CAMBIO_CONTRASENA_REQUERIDO"));
    mvc.perform(get("/api/v1/autenticacion/yo").session(sesion))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.debeCambiarContrasena").value(true));

    mvc.perform(post("/api/v1/autenticacion/cambio-contrasena").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"contrasenaActual": "%s", "contrasenaNueva": "Otra-Frase-Segura-2026"}
                """.formatted(CLAVE)))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/usuarios").session(sesion)).andExpect(status().isOk());
    assertThat(contarBitacora("CONTRASENA_CAMBIADA", "EXITO")).isEqualTo(1);
  }

  @Test
  void cambioDeContrasenaExigeLaContrasenaActual() throws Exception {
    crearUsuario("ana@iqcperu.com", false);
    MockHttpSession sesion = iniciarSesion("ana@iqcperu.com");

    mvc.perform(post("/api/v1/autenticacion/cambio-contrasena").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"contrasenaActual": "no-es-la-actual", "contrasenaNueva": "Otra-Frase-Segura-2026"}
                """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONTRASENA_ACTUAL_INCORRECTA"));
  }

  @Test
  void cerrarSesionInvalidaLaSesion() throws Exception {
    crearUsuario("ana@iqcperu.com", false);
    MockHttpSession sesion = iniciarSesion("ana@iqcperu.com");

    mvc.perform(post("/api/v1/autenticacion/cierre-sesion").session(sesion).with(csrf()))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/autenticacion/yo").session(sesion))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    assertThat(contarBitacora("CIERRE_SESION", "EXITO")).isEqualTo(1);
  }

  @Test
  void unUsuarioDeshabilitadoPierdeSuSesionDeInmediato() throws Exception {
    Usuario ana = crearUsuario("ana@iqcperu.com", false);
    MockHttpSession sesion = iniciarSesion("ana@iqcperu.com");

    jdbc.update("UPDATE usuarios SET estado = 'DESHABILITADO' WHERE id = ?", ana.getId());

    mvc.perform(get("/api/v1/autenticacion/yo").session(sesion)).andExpect(status().isUnauthorized());
  }

  @Test
  void elEstadoPublicoNoRequiereSesionYDevuelveCorrelacion() throws Exception {
    mvc.perform(get("/api/v1/publico/estado").header("X-Correlation-Id", "prueba-estado"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Correlation-Id", "prueba-estado"))
        .andExpect(jsonPath("$.servicio").value("IQC Flow"));
  }

  @Test
  void lasRutasNoDeclaradasQuedanCerradas() throws Exception {
    mvc.perform(get("/no-existe")).andExpect(status().isUnauthorized());
  }
}
