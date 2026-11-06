package pe.com.iqc.flow.seguimientos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;
import pe.com.iqc.flow.seguimientos.infrastructure.DetectorTareasVencidas;

class SeguimientosIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));

  @Autowired private DetectorTareasVencidas detector;

  private Usuario luis;
  private Usuario rosa;
  private MockHttpSession sesionLuis;
  private String clienteLuis;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("ana@iqcperu.com", false, CodigosRol.MASTER);
    luis = crearUsuario("luis@iqcperu.com", false, CodigosRol.COMERCIAL);
    rosa = crearUsuario("rosa@iqcperu.com", false, CodigosRol.COMERCIAL);
    crearUsuario("coordinador@iqcperu.com", false, CodigosRol.COORDINADOR_AUDITORIAS);
    sesionLuis = iniciarSesion("luis@iqcperu.com");
    clienteLuis = JsonPath.read(enviar(post("/api/v1/clientes"), sesionLuis, "{\"razonSocial\": \"Minera Andina\"}", 201),
        "$.id");
  }

  @Test
  void unComercialCreaUnaTareaParaSuClienteConLaPrioridadDelTipo() throws Exception {
    mvc.perform(post("/api/v1/seguimientos/tareas").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(tarea("Recordar cotización", "RECORDATORIO_COTIZACION", HOY.plusDays(2))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.responsable.id").value(luis.getId().toString()))
        .andExpect(jsonPath("$.prioridad").value("ALTA"))
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.cliente.razonSocial").value("Minera Andina"))
        .andExpect(jsonPath("$.historial[0].evento").value("CREADA"))
        .andExpect(jsonPath("$.puedeGestionar").value(true));
    assertThat(contarBitacora("TAREA_CREADA", "EXITO")).isEqualTo(1);
  }

  @Test
  void rechazaFechasPasadasYTiposDesconocidos() throws Exception {
    mvc.perform(post("/api/v1/seguimientos/tareas").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(tarea("X", "LLAMADA", HOY.minusDays(1))))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("FECHA_PASADA"));
    mvc.perform(post("/api/v1/seguimientos/tareas").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(tarea("X", "INVENTADO", HOY)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TIPO_TAREA_INVALIDO"));
  }

  @Test
  void soloQuienPuedeAsignarCreaTareasParaOtrosYElAsignadoRecibeUnAviso() throws Exception {
    String paraLuis = """
        {"titulo": "Programar etapa 1", "tipo": "PROGRAMAR_AUDITORIA", "clienteId": "%s",
         "fechaLimite": "%s", "responsableId": "%s"}
        """.formatted(clienteLuis, HOY.plusDays(1), luis.getId());

    enviar(post("/api/v1/seguimientos/tareas"), iniciarSesion("rosa@iqcperu.com"), paraLuis, 403);
    enviar(post("/api/v1/seguimientos/tareas"), iniciarSesion("coordinador@iqcperu.com"), paraLuis, 201);

    mvc.perform(get("/api/v1/notificaciones/contador").session(sesionLuis))
        .andExpect(jsonPath("$.noLeidas").value(1));
    mvc.perform(get("/api/v1/notificaciones").session(sesionLuis))
        .andExpect(jsonPath("$.contenido[0].tipo").value("TAREA_ASIGNADA"));
  }

  @Test
  void elResponsableDelClienteVeLasTareasAjenasDeSuClientePeroNoLasModifica() throws Exception {
    MockHttpSession sesionRosa = iniciarSesion("rosa@iqcperu.com");
    String deCliente = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionRosa,
        tarea("Llamar a Minera", "LLAMADA", HOY), 201), "$.id");
    String sinCliente = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionRosa,
        "{\"titulo\": \"Tarea interna\", \"tipo\": \"OTRO\", \"fechaLimite\": \"%s\"}".formatted(HOY), 201), "$.id");

    mvc.perform(get("/api/v1/seguimientos/tareas/{id}", deCliente).session(sesionLuis))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.puedeGestionar").value(false));
    mvc.perform(get("/api/v1/seguimientos/tareas/{id}", sinCliente).session(sesionLuis))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/seguimientos/tareas").session(sesionLuis))
        .andExpect(jsonPath("$.totalElementos").value(1));
    enviar(patch("/api/v1/seguimientos/tareas/" + deCliente), sesionLuis, "{\"prioridad\": \"URGENTE\", \"version\": 0}", 403);

    mvc.perform(get("/api/v1/seguimientos/tareas").session(iniciarSesion("ana@iqcperu.com")))
        .andExpect(jsonPath("$.totalElementos").value(2));
  }

  @Test
  void completarExigeResultadoYPuedeCrearLaSiguienteTarea() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Llamar", "LLAMADA", HOY), 201), "$.id");

    enviar(post("/api/v1/seguimientos/tareas/" + id + "/completado"), sesionLuis, "{\"version\": 0}", 400);
    mvc.perform(post("/api/v1/seguimientos/tareas/{id}/completado", id).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"resultado": "SIN_RESPUESTA", "notas": "No contestó", "version": 0,
                 "siguiente": {"titulo": "Volver a llamar", "tipo": "LLAMADA", "fechaLimite": "%s"}}
                """.formatted(HOY.plusDays(2))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("COMPLETADA"))
        .andExpect(jsonPath("$.resultado").value("SIN_RESPUESTA"))
        .andExpect(jsonPath("$.cerradaPor.id").value(luis.getId().toString()));

    mvc.perform(get("/api/v1/seguimientos/tareas").session(sesionLuis))
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].titulo").value("Volver a llamar"))
        .andExpect(jsonPath("$.contenido[0].cliente.id").value(clienteLuis));
    String version = String.valueOf(((Number) JsonPath.read(mvc.perform(get("/api/v1/seguimientos/tareas/{id}", id)
        .session(sesionLuis)).andReturn().getResponse().getContentAsString(), "$.version")).longValue());
    mvc.perform(post("/api/v1/seguimientos/tareas/{id}/cancelacion", id).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"x\", \"version\": " + version + "}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TAREA_CERRADA"));
  }

  @Test
  void reprogramarExigeMotivoYQuedaEnElHistorial() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Llamar", "LLAMADA", HOY), 201), "$.id");

    enviar(post("/api/v1/seguimientos/tareas/" + id + "/reprogramacion"), sesionLuis,
        "{\"fechaLimite\": \"%s\", \"version\": 0}".formatted(HOY.plusDays(3)), 400);
    mvc.perform(post("/api/v1/seguimientos/tareas/{id}/reprogramacion", id).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fechaLimite\": \"%s\", \"horaLimite\": \"10:30\", \"motivo\": \"El cliente pidió la próxima semana\", \"version\": 0}"
                .formatted(HOY.plusDays(3))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reprogramaciones").value(1))
        .andExpect(jsonPath("$.horaLimite").value("10:30:00"))
        .andExpect(jsonPath("$.historial[1].evento").value("REPROGRAMADA"))
        .andExpect(jsonPath("$.historial[1].motivo").value("El cliente pidió la próxima semana"));
  }

  @Test
  void lasVistasYElResumenSeparanHoyAtrasadasProximasYEnEspera() throws Exception {
    enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Hoy", "LLAMADA", HOY), 201);
    enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Mañana", "LLAMADA", HOY.plusDays(1)), 201);
    enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("En 3 días", "LLAMADA", HOY.plusDays(3)), 201);
    String atrasada = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Atrasada", "LLAMADA", HOY), 201), "$.id");
    jdbc.update("UPDATE tareas_seguimiento SET fecha_limite = ? WHERE id = ?::uuid", HOY.minusDays(2), atrasada);
    String espera = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Esperando", "SEGUIMIENTO_COMERCIAL", HOY.plusDays(20)), 201), "$.id");
    enviar(patch("/api/v1/seguimientos/tareas/" + espera), sesionLuis, "{\"estado\": \"EN_ESPERA\", \"version\": 0}", 200);

    mvc.perform(get("/api/v1/seguimientos/tareas").param("vista", "HOY").session(sesionLuis))
        .andExpect(jsonPath("$.totalElementos").value(1));
    mvc.perform(get("/api/v1/seguimientos/tareas").param("vista", "ATRASADAS").session(sesionLuis))
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].vencida").value(true));
    mvc.perform(get("/api/v1/seguimientos/tareas").param("vista", "PROXIMAS").param("dias", "7").session(sesionLuis))
        .andExpect(jsonPath("$.totalElementos").value(2));
    mvc.perform(get("/api/v1/seguimientos/resumen").session(sesionLuis))
        .andExpect(jsonPath("$.hoy").value(1))
        .andExpect(jsonPath("$.atrasadas").value(1))
        .andExpect(jsonPath("$.proximas").value(2))
        .andExpect(jsonPath("$.enEspera").value(1));
  }

  @Test
  void elDetectorAvisaUnaSolaVezYVuelveAAvisarSiSeReprogramaYVence() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Llamar", "LLAMADA", HOY), 201), "$.id");
    jdbc.update("UPDATE tareas_seguimiento SET fecha_limite = ? WHERE id = ?::uuid", HOY.minusDays(1), id);

    assertThat(detector.revisar()).isEqualTo(1);
    assertThat(detector.revisar()).isZero();
    mvc.perform(get("/api/v1/notificaciones").session(sesionLuis))
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].tipo").value("TAREA_VENCIDA"))
        .andExpect(jsonPath("$.contenido[0].sujetoId").value(id));

    long version = jdbc.queryForObject("SELECT version FROM tareas_seguimiento WHERE id = ?::uuid", Long.class, id);
    enviar(post("/api/v1/seguimientos/tareas/" + id + "/reprogramacion"), sesionLuis,
        "{\"fechaLimite\": \"%s\", \"motivo\": \"Nueva fecha\", \"version\": %d}".formatted(HOY, version), 200);
    jdbc.update("UPDATE tareas_seguimiento SET fecha_limite = ? WHERE id = ?::uuid", HOY.minusDays(1), id);

    assertThat(detector.revisar()).isEqualTo(1);
    mvc.perform(get("/api/v1/notificaciones/contador").session(sesionLuis)).andExpect(jsonPath("$.noLeidas").value(2));
  }

  @Test
  void reasignarRequierePermisoYAvisaAlNuevoResponsable() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Llamar", "LLAMADA", HOY), 201), "$.id");
    String cuerpo = "{\"responsableId\": \"%s\", \"version\": 0}".formatted(rosa.getId());

    enviar(put("/api/v1/seguimientos/tareas/" + id + "/responsable"), sesionLuis, cuerpo, 403);
    mvc.perform(put("/api/v1/seguimientos/tareas/{id}/responsable", id).session(iniciarSesion("ana@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.responsable.id").value(rosa.getId().toString()))
        .andExpect(jsonPath("$.historial[*].evento", hasItem("REASIGNADA")));

    mvc.perform(get("/api/v1/notificaciones").session(iniciarSesion("rosa@iqcperu.com")))
        .andExpect(jsonPath("$.contenido[0].tipo").value("TAREA_ASIGNADA"));
  }

  @Test
  void cancelarExigeMotivoYLaTareaPasaACerradas() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Llamar", "LLAMADA", HOY), 201), "$.id");

    enviar(post("/api/v1/seguimientos/tareas/" + id + "/cancelacion"), sesionLuis,
        "{\"motivo\": \"El cliente desistió\", \"version\": 0}", 200);

    mvc.perform(get("/api/v1/seguimientos/tareas").param("vista", "CERRADAS").session(sesionLuis))
        .andExpect(jsonPath("$.contenido[0].estado").value("CANCELADA"));
    mvc.perform(get("/api/v1/seguimientos/tareas").session(sesionLuis))
        .andExpect(jsonPath("$.totalElementos").value(0));
  }

  @Test
  void unaVersionDesactualizadaDevuelve409() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/seguimientos/tareas"), sesionLuis, tarea("Llamar", "LLAMADA", HOY), 201), "$.id");
    mvc.perform(patch("/api/v1/seguimientos/tareas/{id}", id).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"prioridad\": \"ALTA\", \"version\": 5}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CONFLICTO_VERSION"));
  }

  private String tarea(String titulo, String tipo, LocalDate fecha) {
    return """
        {"titulo": "%s", "tipo": "%s", "clienteId": "%s", "fechaLimite": "%s"}
        """.formatted(titulo, tipo, clienteLuis, fecha);
  }

  private String enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud,
      MockHttpSession sesion, String cuerpo, int estadoEsperado) throws Exception {
    return mvc.perform(solicitud.session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(estadoEsperado))
        .andReturn().getResponse().getContentAsString();
  }
}
