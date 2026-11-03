package pe.com.iqc.flow.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

class UsuariosIT extends PruebaIntegracion {
  private static final String NUEVO_USUARIO = """
      {"correo": "Nuevo@IQCperu.com", "nombre": "Nuevo Usuario",
       "contrasenaInicial": "Inicial-Segura-2026", "roles": ["COMERCIAL"]}
      """;

  private Usuario ana;
  private MockHttpSession sesionAna;

  @BeforeEach
  void crearMaster() throws Exception {
    ana = crearUsuario("ana@iqcperu.com", false, CodigosRol.MASTER);
    sesionAna = iniciarSesion("ana@iqcperu.com");
  }

  @Test
  void sinSesionDevuelve401() throws Exception {
    mvc.perform(get("/api/v1/usuarios"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
  }

  @Test
  void sinPermisoDevuelve403YQuedaEnLaBitacora() throws Exception {
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    MockHttpSession sesion = iniciarSesion("comercial@iqcperu.com");

    mvc.perform(get("/api/v1/usuarios").session(sesion))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

    assertThat(contarBitacora("ACCESO_DENEGADO", "DENEGADO")).isEqualTo(1);
  }

  @Test
  void crearUsuarioLoRegistraEnLaBitacoraConCorrelacionYSinLaContrasena() throws Exception {
    mvc.perform(post("/api/v1/usuarios").session(sesionAna).with(csrf())
            .header("X-Correlation-Id", "prueba-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(NUEVO_USUARIO))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.correo").value("nuevo@iqcperu.com"))
        .andExpect(jsonPath("$.debeCambiarContrasena").value(true))
        .andExpect(jsonPath("$.roles", contains("COMERCIAL")));

    Map<String, Object> registro = jdbc.queryForMap(
        "SELECT actor_usuario_id, id_correlacion, estado_nuevo::text AS estado FROM bitacora WHERE accion = 'USUARIO_CREADO'");
    assertThat(registro.get("actor_usuario_id")).isEqualTo(ana.getId());
    assertThat(registro.get("id_correlacion")).isEqualTo("prueba-123");
    assertThat((String) registro.get("estado")).contains("COMERCIAL").doesNotContain("bcrypt").doesNotContain("Inicial");
  }

  @Test
  void correoDuplicadoDevuelve409() throws Exception {
    mvc.perform(post("/api/v1/usuarios").session(sesionAna).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"correo": "ANA@iqcperu.com", "nombre": "Otra Ana", "contrasenaInicial": "Inicial-Segura-2026"}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
  }

  @Test
  void contrasenaDebilDevuelve422() throws Exception {
    mvc.perform(post("/api/v1/usuarios").session(sesionAna).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"correo": "nuevo@iqcperu.com", "nombre": "Nuevo", "contrasenaInicial": "corta"}
                """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONTRASENA_DEBIL"));
  }

  @Test
  void datosInvalidosDevuelven400ConErroresPorCampo() throws Exception {
    mvc.perform(post("/api/v1/usuarios").session(sesionAna).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"correo": "no-es-un-correo", "nombre": "", "contrasenaInicial": "Inicial-Segura-2026"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("VALIDACION"))
        .andExpect(jsonPath("$.errores", hasSize(2)));
  }

  @Test
  void unAdministradorNoPuedeCrearUsuarios() throws Exception {
    crearUsuario("admin@iqcperu.com", false, CodigosRol.ADMINISTRADOR);
    MockHttpSession sesion = iniciarSesion("admin@iqcperu.com");

    mvc.perform(get("/api/v1/usuarios").session(sesion)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/usuarios").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(NUEVO_USUARIO))
        .andExpect(status().isForbidden());
  }

  @Test
  void noSePuedeQuitarElRolAlUltimoMaster() throws Exception {
    long version = usuarios.findById(ana.getId()).orElseThrow().getVersion();

    mvc.perform(put("/api/v1/usuarios/{id}/roles", ana.getId()).session(sesionAna).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"roles": ["ADMINISTRADOR"], "version": %d}
                """.formatted(version)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("ULTIMO_MASTER"));
  }

  @Test
  void asignarRolesReemplazaLosAnterioresEIncrementaLaVersion() throws Exception {
    Usuario luis = crearUsuario("luis@iqcperu.com", false, CodigosRol.COMERCIAL);

    mvc.perform(put("/api/v1/usuarios/{id}/roles", luis.getId()).session(sesionAna).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"roles": ["ADMINISTRADOR", "COORDINADOR_AUDITORIAS"], "version": %d}
                """.formatted(luis.getVersion())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles", contains("ADMINISTRADOR", "COORDINADOR_AUDITORIAS")))
        .andExpect(jsonPath("$.version").value(luis.getVersion() + 1));
  }

  @Test
  void unaVersionDesactualizadaDevuelve409() throws Exception {
    Usuario luis = crearUsuario("luis@iqcperu.com", false);

    mvc.perform(patch("/api/v1/usuarios/{id}", luis.getId()).session(sesionAna).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nombre": "Luis Pérez", "version": 99}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CONFLICTO_VERSION"));
  }

  @Test
  void nadiePuedeDeshabilitarseASiMismo() throws Exception {
    long version = usuarios.findById(ana.getId()).orElseThrow().getVersion();

    mvc.perform(patch("/api/v1/usuarios/{id}", ana.getId()).session(sesionAna).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"estado": "DESHABILITADO", "version": %d}
                """.formatted(version)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("NO_PUEDE_DESHABILITARSE"));
  }

  @Test
  void unTamanoDePaginaFueraDeRangoDevuelve400() throws Exception {
    mvc.perform(get("/api/v1/usuarios").param("tamano", "500").session(sesionAna))
        .andExpect(status().isBadRequest());
  }

  @Test
  void losUsuariosDeOtraOrganizacionNoSonVisibles() throws Exception {
    UUID otraOrganizacion = organizaciones.asegurarExistencia("OTRA", "Otra organización").id();
    Usuario ajeno = crearUsuarioEn(otraOrganizacion, "ajeno@otra.com", false);

    mvc.perform(get("/api/v1/usuarios/{id}", ajeno.getId()).session(sesionAna))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    mvc.perform(get("/api/v1/usuarios").session(sesionAna))
        .andExpect(jsonPath("$.totalElementos").value(1));
  }

  @Test
  void listaLosRolesConSusPermisos() throws Exception {
    mvc.perform(get("/api/v1/roles").session(sesionAna))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(5)))
        .andExpect(jsonPath("$[?(@.codigo == 'MASTER')].permisos[*]",
            hasItems("usuarios:gestionar", "roles:asignar", "bitacora:leer", "clientes:archivar")))
        .andExpect(jsonPath("$[?(@.codigo == 'COMERCIAL')].permisos[*]",
            containsInAnyOrder("clientes:leer", "clientes:escribir", "seguimientos:leer", "seguimientos:escribir")));
  }
}
