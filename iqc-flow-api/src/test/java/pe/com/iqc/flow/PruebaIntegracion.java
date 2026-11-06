package pe.com.iqc.flow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.com.iqc.flow.identidad.domain.Rol;
import pe.com.iqc.flow.identidad.domain.Usuario;
import pe.com.iqc.flow.identidad.infrastructure.RolRepository;
import pe.com.iqc.flow.identidad.infrastructure.UsuarioRepository;
import pe.com.iqc.flow.organizaciones.ServicioOrganizaciones;

/**
 *base de las pruebas de integración: PostgreSQL real (iqc_flow_test), migraciones de Flyway y
 *la cadena completa de Spring Security. Cada prueba parte de una organización sin usuarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PruebaIntegracion {
  protected static final String CLAVE = "Clave-Segura-2026!";
  protected static final String RUTA_INICIO_SESION = "/api/v1/autenticacion/inicio-sesion";

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected UsuarioRepository usuarios;
  @Autowired protected RolRepository roles;
  @Autowired protected PasswordEncoder codificador;
  @Autowired protected ServicioOrganizaciones organizaciones;

  protected UUID organizacionId;

  @BeforeEach
  void prepararBaseDeDatos() {
    // TRUNCATE no dispara el trigger que impide modificar la bitácora
    jdbc.execute("TRUNCATE bitacora, notificaciones, actividades, historial_tareas, tareas_seguimiento, contactos_cliente, direcciones_cliente, clientes, usuarios_roles, usuarios");
    jdbc.update("DELETE FROM organizaciones");
    organizacionId = organizaciones.asegurarExistencia("IQC_PERU", "IQC Perú").id();
  }

  protected Usuario crearUsuario(String correo, boolean debeCambiarContrasena, String... codigosRoles) {
    return crearUsuarioEn(organizacionId, correo, debeCambiarContrasena, codigosRoles);
  }

  protected Usuario crearUsuarioEn(UUID organizacion, String correo, boolean debeCambiarContrasena,
      String... codigosRoles) {
    Set<Rol> asignados = codigosRoles.length == 0
        ? Set.of() : Set.copyOf(roles.buscarDisponibles(Set.of(codigosRoles), organizacion));
    Usuario usuario = Usuario.nuevo(organizacion, correo, "Usuario " + correo, codificador.encode(CLAVE), asignados,
        Instant.now());
    if (!debeCambiarContrasena) usuario.cambiarContrasena(usuario.getHashContrasena(), Instant.now());
    return usuarios.save(usuario);
  }

  protected MockHttpSession iniciarSesion(String correo) throws Exception { return iniciarSesion(correo, CLAVE); }

  protected MockHttpSession iniciarSesion(String correo, String contrasena) throws Exception {
    MvcResult resultado = mvc.perform(post(RUTA_INICIO_SESION).with(csrf())
            .param("correo", correo)
            .param("contrasena", contrasena))
        .andExpect(status().isNoContent())
        .andReturn();
    return (MockHttpSession) resultado.getRequest().getSession(false);
  }

  protected long contarBitacora(String accion, String resultado) {
    return jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = ? AND resultado = ?", Long.class,
        accion, resultado);
  }
}
