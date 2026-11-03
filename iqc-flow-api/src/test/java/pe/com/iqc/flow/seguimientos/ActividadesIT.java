package pe.com.iqc.flow.seguimientos;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

class ActividadesIT extends PruebaIntegracion {
  private Usuario luis;
  private Usuario rosa;
  private MockHttpSession sesionLuis;

  @BeforeEach
  void preparar() throws Exception {
    luis = crearUsuario("luis@iqcperu.com", false, CodigosRol.COMERCIAL);
    rosa = crearUsuario("rosa@iqcperu.com", false, CodigosRol.COMERCIAL);
    sesionLuis = iniciarSesion("luis@iqcperu.com");
  }

  @Test
  void registraUnaLlamadaYLaMuestraEnElHistorialDelCliente() throws Exception {
    String cliente = crearCliente(sesionLuis, "Minera Andina");
    String contacto = JsonPath.read(mvc.perform(post("/api/v1/clientes/{id}/contactos", cliente).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombreCompleto\": \"Ana\", \"correo\": \"ana@minera.pe\"}"))
        .andReturn().getResponse().getContentAsString(), "$.id");

    mvc.perform(post("/api/v1/seguimientos/actividades").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"clienteId": "%s", "tipo": "LLAMADA", "resumen": "Interesada en ISO 9001", "contactoId": "%s"}
                """.formatted(cliente, contacto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.direccion").value("SALIENTE"))
        .andExpect(jsonPath("$.registradaPor.id").value(luis.getId().toString()));

    mvc.perform(get("/api/v1/seguimientos/actividades").param("clienteId", cliente).session(sesionLuis))
        .andExpect(jsonPath("$.contenido", hasSize(1)))
        .andExpect(jsonPath("$.contenido[0].resumen").value("Interesada en ISO 9001"));
  }

  @Test
  void rechazaActividadesFuturasYContactosDeOtroCliente() throws Exception {
    String cliente = crearCliente(sesionLuis, "Minera Andina");
    String otro = crearCliente(sesionLuis, "Otra");
    String contactoDeOtro = JsonPath.read(mvc.perform(post("/api/v1/clientes/{id}/contactos", otro).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombreCompleto\": \"X\", \"correo\": \"x@otra.pe\"}"))
        .andReturn().getResponse().getContentAsString(), "$.id");

    mvc.perform(post("/api/v1/seguimientos/actividades").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clienteId\": \"%s\", \"tipo\": \"NOTA\", \"resumen\": \"x\", \"ocurridaEn\": \"%s\"}"
                .formatted(cliente, Instant.now().plus(2, ChronoUnit.DAYS))))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("FECHA_FUTURA"));
    mvc.perform(post("/api/v1/seguimientos/actividades").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clienteId\": \"%s\", \"tipo\": \"LLAMADA\", \"resumen\": \"x\", \"contactoId\": \"%s\"}"
                .formatted(cliente, contactoDeOtro)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONTACTO_INVALIDO"));
  }

  @Test
  void detectaClientesSinMovimientoExcluyendoLosQueTienenActividadOTareasAbiertas() throws Exception {
    String olvidado = crearCliente(sesionLuis, "Cliente olvidado");
    String conActividad = crearCliente(sesionLuis, "Cliente con llamada reciente");
    String conTarea = crearCliente(sesionLuis, "Cliente con tarea");
    crearCliente(sesionLuis, "Cliente nuevo");
    jdbc.update("UPDATE clientes SET creado_en = now() - interval '45 days' WHERE id IN (?::uuid, ?::uuid, ?::uuid)",
        olvidado, conActividad, conTarea);
    mvc.perform(post("/api/v1/seguimientos/actividades").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clienteId\": \"%s\", \"tipo\": \"LLAMADA\", \"resumen\": \"Seguimiento\"}".formatted(conActividad)))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/v1/seguimientos/tareas").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"titulo\": \"Llamar\", \"tipo\": \"LLAMADA\", \"clienteId\": \"%s\", \"fechaLimite\": \"%s\"}"
                .formatted(conTarea, LocalDate.now(ZoneId.of("America/Lima")).plusDays(1))))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/v1/seguimientos/clientes-sin-movimiento").param("dias", "30").session(sesionLuis))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].razonSocial").value("Cliente olvidado"))
        .andExpect(jsonPath("$[0].diasSinMovimiento").value(45));
  }

  @Test
  void unComercialNoConsultaLaCarteraDeOtro() throws Exception {
    mvc.perform(get("/api/v1/seguimientos/clientes-sin-movimiento").param("responsableId", rosa.getId().toString())
            .session(sesionLuis))
        .andExpect(status().isForbidden());
  }

  @Test
  void lasNotificacionesSonPrivadasDeCadaUsuario() throws Exception {
    String id = jdbc.queryForObject("""
        INSERT INTO notificaciones (id, organizacion_id, destinatario_usuario_id, tipo, titulo)
        VALUES (gen_random_uuid(), ?, ?, 'PRUEBA', 'Aviso de prueba') RETURNING id::text
        """, String.class, organizacionId, rosa.getId());

    mvc.perform(post("/api/v1/notificaciones/{id}/lectura", id).session(sesionLuis).with(csrf()))
        .andExpect(status().isNotFound());
    MockHttpSession sesionRosa = iniciarSesion("rosa@iqcperu.com");
    mvc.perform(post("/api/v1/notificaciones/{id}/lectura", id).session(sesionRosa).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.leida").value(true));
    mvc.perform(get("/api/v1/notificaciones/contador").session(sesionRosa)).andExpect(jsonPath("$.noLeidas").value(0));
  }

  private String crearCliente(MockHttpSession sesion, String razonSocial) throws Exception {
    return JsonPath.read(mvc.perform(post("/api/v1/clientes").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"razonSocial\": \"%s\"}".formatted(razonSocial)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString(), "$.id");
  }
}
