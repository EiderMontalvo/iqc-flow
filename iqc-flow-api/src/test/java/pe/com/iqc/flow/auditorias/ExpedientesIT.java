package pe.com.iqc.flow.auditorias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

class ExpedientesIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));

  private Usuario operaciones;
  private MockHttpSession sesion;
  private String clienteId;
  private String sedeId;

  @BeforeEach
  void preparar() throws Exception {
    operaciones = crearUsuario("operaciones@iqcperu.com", false, CodigosRol.COORDINADOR_AUDITORIAS);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    crearUsuario("ana@iqcperu.com", false, CodigosRol.MASTER);
    sesion = iniciarSesion("operaciones@iqcperu.com");
    MockHttpSession sesionAna = iniciarSesion("ana@iqcperu.com");
    clienteId = JsonPath.read(enviar(post("/api/v1/clientes"), sesionAna,
        "{\"razonSocial\": \"Constructora Andina SAC\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"20131312955\"}", 201), "$.id");
    sedeId = JsonPath.read(enviar(post("/api/v1/clientes/" + clienteId + "/direcciones"), sesionAna,
        "{\"tipo\": \"SEDE\", \"nombre\": \"Planta Lurín\", \"direccion\": \"Km 30 Panamericana Sur\"}", 201), "$.id");
  }

  @Test
  void unExpedienteIqcGlobalEmpiezaEnSolicitudYUnoInacalEnPlanificacion() throws Exception {
    mvc.perform(post("/api/v1/expedientes").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(expediente("IQC_GLOBAL", "CERTIFICACION_INICIAL")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.codigo", containsString("EXP-")))
        .andExpect(jsonPath("$.estado").value("SOLICITUD_ENVIADA"))
        .andExpect(jsonPath("$.decideIndia").value(true))
        .andExpect(jsonPath("$.normas", hasSize(2)))
        .andExpect(jsonPath("$.normas[0].designacion").value("ISO 9001:2015"))
        .andExpect(jsonPath("$.sedes[0].nombre").value("Planta Lurín"))
        .andExpect(jsonPath("$.responsable.id").value(operaciones.getId().toString()))
        .andExpect(jsonPath("$.transicionesPermitidas", hasItem("PROPOSAL_RECIBIDO")));

    mvc.perform(post("/api/v1/expedientes").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(expediente("INACAL", "CERTIFICACION_INICIAL")))
        .andExpect(jsonPath("$.estado").value("EN_PLANIFICACION"))
        .andExpect(jsonPath("$.decideIndia").value(false));
  }

  @Test
  void rechazaEdicionesNoVigentesYSedesDeOtroCliente() throws Exception {
    String base = "{\"clienteId\": \"%s\", \"esquema\": \"INACAL\", \"tipoServicio\": \"CERTIFICACION_INICIAL\", "
        + "\"normas\": [%s], \"sedes\": [%s]}";
    mvc.perform(post("/api/v1/expedientes").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted(clienteId, "{\"norma\": \"ISO_IEC_27001\", \"edicion\": \"2013\"}", "")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("EDICION_NO_VIGENTE"));
    mvc.perform(post("/api/v1/expedientes").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(base.formatted(clienteId, "{\"norma\": \"ISO_9001\", \"edicion\": \"2015\"}",
                "\"" + java.util.UUID.randomUUID() + "\"")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("SEDE_INVALIDA"));
  }

  @Test
  void unComercialConsultaPeroNoCreaExpedientes() throws Exception {
    MockHttpSession comercial = iniciarSesion("comercial@iqcperu.com");
    enviar(post("/api/v1/expedientes"), comercial, expediente("INACAL", "CERTIFICACION_INICIAL"), 403);
    mvc.perform(get("/api/v1/expedientes").session(comercial)).andExpect(status().isOk());
  }

  @Test
  void elFlujoConIndiaAvanzaPasoAPasoYRegistraHistorial() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/expedientes"), sesion, expediente("IQC_GLOBAL", "CERTIFICACION_INICIAL"), 201), "$.id");

    String[] pasos = {"PROPOSAL_RECIBIDO", "PROPOSAL_FIRMADO", "PAQUETE_FASE1_RECIBIDO", "PAQUETE_FASE1_ENVIADO",
        "PAQUETE_FASE2_RECIBIDO", "PAQUETE_FASE2_ENVIADO", "OBSERVADO", "PAQUETE_FASE2_ENVIADO", "CERTIFICADO_RECIBIDO"};
    for (int i = 0; i < pasos.length; i++) {
      enviar(post("/api/v1/expedientes/" + id + "/transicion"), sesion,
          "{\"estado\": \"%s\", \"version\": %d}".formatted(pasos[i], i), 200);
    }

    mvc.perform(get("/api/v1/expedientes/{id}", id).session(sesion))
        .andExpect(jsonPath("$.estado").value("CERTIFICADO_RECIBIDO"))
        .andExpect(jsonPath("$.historial", hasSize(10)))
        .andExpect(jsonPath("$.transicionesPermitidas", hasItem("TRADUCIDO")));
  }

  @Test
  void unaTransicionFueraDelFlujoSeRechazaYCancelarExigeMotivo() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/expedientes"), sesion, expediente("INACAL", "CERTIFICACION_INICIAL"), 201), "$.id");

    mvc.perform(post("/api/v1/expedientes/{id}/transicion", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"EMITIDO\", \"version\": 0}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    mvc.perform(post("/api/v1/expedientes/{id}/transicion", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"CANCELADO\", \"version\": 0}"))
        .andExpect(jsonPath("$.codigo").value("MOTIVO_REQUERIDO"));
    enviar(post("/api/v1/expedientes/" + id + "/transicion"), sesion,
        "{\"estado\": \"CANCELADO\", \"motivo\": \"El cliente desistió\", \"version\": 0}", 200);
  }

  @Test
  void registrarLaCertificacionCreaLasVigilanciasYSusTareasSinDuplicarlas() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/expedientes"), sesion, expediente("INACAL", "CERTIFICACION_INICIAL"), 201), "$.id");
    String[] pasos = {"ETAPA1_REALIZADA", "ETAPA2_REALIZADA", "EXPEDIENTE_VERIFICADO", "EN_DECISION", "EMITIDO"};
    for (int i = 0; i < pasos.length; i++) {
      enviar(post("/api/v1/expedientes/" + id + "/transicion"), sesion,
          "{\"estado\": \"%s\", \"version\": %d}".formatted(pasos[i], i), 200);
    }
    LocalDate certificacion = HOY.minusMonths(11);

    mvc.perform(post("/api/v1/expedientes/{id}/certificacion", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fechaCertificacion\": \"%s\", \"version\": 5}".formatted(certificacion)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fechaExpiracion").value(certificacion.plusYears(3).toString()))
        .andExpect(jsonPath("$.auditorias", hasSize(2)))
        .andExpect(jsonPath("$.auditorias[0].tipo").value("VIGILANCIA_1"))
        .andExpect(jsonPath("$.auditorias[0].fechaLimite").value(certificacion.plusYears(1).toString()))
        .andExpect(jsonPath("$.auditorias[0].advertencias[0]").value("Sin equipo auditor asignado."));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM tareas_seguimiento WHERE origen = 'AUTOMATIZACION'", Long.class))
        .isEqualTo(3);
    // La 1ª vigilancia vence en un mes: su tarea "Programar" queda para hoy (no en el pasado)
    assertThat(jdbc.queryForObject("SELECT fecha_limite FROM tareas_seguimiento WHERE clave_deduplicacion LIKE '%vigilancia:1'",
        LocalDate.class)).isEqualTo(HOY);
    mvc.perform(post("/api/v1/expedientes/{id}/certificacion", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fechaCertificacion\": \"%s\", \"version\": 6}".formatted(certificacion)))
        .andExpect(jsonPath("$.codigo").value("CERTIFICACION_YA_REGISTRADA"));

    mvc.perform(get("/api/v1/expedientes/vigilancias").param("dias", "60").session(sesion))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].cliente.razonSocial").value("Constructora Andina SAC"));
    mvc.perform(get("/api/v1/expedientes/resumen").session(sesion))
        .andExpect(jsonPath("$.porEstado.EMITIDO").value(1))
        .andExpect(jsonPath("$.vigilanciasProximas30Dias").value(1))
        .andExpect(jsonPath("$.auditoriasSinEquipo").value(2));
  }

  @Test
  void noSeRegistraLaCertificacionSinCertificadoEmitido() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/expedientes"), sesion, expediente("INACAL", "CERTIFICACION_INICIAL"), 201), "$.id");
    mvc.perform(post("/api/v1/expedientes/{id}/certificacion", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"fechaCertificacion\": \"%s\", \"version\": 0}".formatted(HOY)))
        .andExpect(jsonPath("$.codigo").value("SIN_CERTIFICADO"));
  }

  @Test
  void elEquipoRespetaLosRolesDeCadaAuditorYAvisaDeCompetencias() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/expedientes"), sesion, expediente("INACAL", "CERTIFICACION_INICIAL"), 201), "$.id");
    String etapa2 = JsonPath.read(enviar(post("/api/v1/expedientes/" + id + "/auditorias"), sesion,
        "{\"tipo\": \"ETAPA_2\", \"fechaInicioPlan\": \"%s\", \"fechaFinPlan\": \"%s\", \"diasAuditor\": 2.5, \"modalidad\": \"PRESENCIAL\"}"
            .formatted(HOY.plusDays(10), HOY.plusDays(11)), 201), "$.id");
    String lider = auditor("Julio Líder", "[\"LIDER\", \"AUDITOR\"]", "[\"ISO_9001\"]");
    String experto = auditor("Paula Experta", "[\"EXPERTO_TECNICO\"]", "[]");

    enviar(put("/api/v1/expedientes/" + id + "/auditorias/" + etapa2 + "/equipo"), sesion,
        "{\"miembros\": [{\"auditorId\": \"%s\", \"rol\": \"LIDER\"}], \"version\": 0}".formatted(experto), 422);
    mvc.perform(put("/api/v1/expedientes/{id}/auditorias/{a}/equipo", id, etapa2).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"miembros\": [{\"auditorId\": \"%s\", \"rol\": \"LIDER\"}, {\"auditorId\": \"%s\", \"rol\": \"EXPERTO_TECNICO\"}], \"version\": 0}"
                .formatted(lider, experto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
        .andExpect(jsonPath("$.equipo", hasSize(2)))
        .andExpect(jsonPath("$.equipo[0].rol").value("LIDER"))
        .andExpect(jsonPath("$.advertencias", hasItem("Julio Líder no tiene registrada competencia en ISO 45001.")));
  }

  @Test
  void laEtapa1SoloAplicaACertificacionesInicialesYNoSeDuplicanAuditorias() throws Exception {
    String rc = JsonPath.read(enviar(post("/api/v1/expedientes"), sesion, expediente("SAE", "RECERTIFICACION"), 201), "$.id");
    enviar(post("/api/v1/expedientes/" + rc + "/auditorias"), sesion, "{\"tipo\": \"ETAPA_1\"}", 422);
    enviar(post("/api/v1/expedientes/" + rc + "/auditorias"), sesion, "{\"tipo\": \"ETAPA_2\"}", 201);
    enviar(post("/api/v1/expedientes/" + rc + "/auditorias"), sesion, "{\"tipo\": \"ETAPA_2\"}", 409);
  }

  @Test
  void elEstadoDelPaqueteSoloAplicaAIqcGlobal() throws Exception {
    String inacal = JsonPath.read(enviar(post("/api/v1/expedientes"), sesion, expediente("INACAL", "RECERTIFICACION"), 201), "$.id");
    String auditoria = JsonPath.read(enviar(post("/api/v1/expedientes/" + inacal + "/auditorias"), sesion, "{\"tipo\": \"ETAPA_2\"}", 201), "$.id");
    mvc.perform(patch("/api/v1/expedientes/{id}/auditorias/{a}", inacal, auditoria).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"estadoPaquete\": \"ENVIADO_A_INDIA\", \"version\": 0}"))
        .andExpect(jsonPath("$.codigo").value("PAQUETE_NO_APLICA"));

    String global = JsonPath.read(enviar(post("/api/v1/expedientes"), sesion, expediente("IQC_GLOBAL", "RECERTIFICACION"), 201), "$.id");
    String vigilancia = JsonPath.read(enviar(post("/api/v1/expedientes/" + global + "/auditorias"), sesion, "{\"tipo\": \"EXTRAORDINARIA\"}", 201), "$.id");
    mvc.perform(patch("/api/v1/expedientes/{id}/auditorias/{a}", global, vigilancia).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"estadoPaquete\": \"ENVIADO_A_INDIA\", \"fechaRealInicio\": \"%s\", \"version\": 0}".formatted(HOY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estadoPaquete").value("ENVIADO_A_INDIA"))
        .andExpect(jsonPath("$.estado").value("EJECUTADA"));
  }

  @Test
  void elAliadoAportaSuComisionPorDefecto() throws Exception {
    String aliado = JsonPath.read(enviar(post("/api/v1/aliados-comerciales"), sesion,
        "{\"codigo\": \"jc\", \"nombre\": \"Aliado de prueba\", \"comisionPorcentaje\": 10}", 201), "$.id");
    enviar(post("/api/v1/aliados-comerciales"), sesion, "{\"codigo\": \"JC\", \"nombre\": \"Otro\"}", 409);

    String cuerpo = "{\"clienteId\": \"%s\", \"esquema\": \"SAE\", \"tipoServicio\": \"AMPLIACION\", "
        + "\"normas\": [{\"norma\": \"ISO_9001\", \"edicion\": \"2015\"}], \"aliadoId\": \"%s\"}";
    mvc.perform(post("/api/v1/expedientes").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo.formatted(clienteId, aliado)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.aliado.codigo").value("JC"))
        .andExpect(jsonPath("$.comisionPorcentaje").value(10.0));
  }

  private String expediente(String esquema, String tipo) {
    return """
        {"clienteId": "%s", "esquema": "%s", "tipoServicio": "%s",
         "normas": [{"norma": "ISO_45001", "edicion": "2018"}, {"norma": "ISO_9001", "edicion": "2015"}],
         "alcance": "Construcción de obras civiles", "sedes": ["%s"], "numeroContrato": "001-2026"}
        """.formatted(clienteId, esquema, tipo, sedeId);
  }

  private String auditor(String nombre, String roles, String normas) throws Exception {
    return JsonPath.read(enviar(post("/api/v1/auditores"), sesion,
        "{\"nombreCompleto\": \"%s\", \"roles\": %s, \"normas\": %s}".formatted(nombre, roles, normas), 201), "$.id");
  }

  private String enviar(MockHttpServletRequestBuilder solicitud, MockHttpSession sesionUsada, String cuerpo,
      int estadoEsperado) throws Exception {
    return mvc.perform(solicitud.session(sesionUsada).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(estadoEsperado))
        .andReturn().getResponse().getContentAsString();
  }
}
