package pe.com.iqc.flow.auditorias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

/**Incremento 6: imparcialidad, decisiones, certificados, firma, entrega y documentos adjuntos*/
class CertificacionIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));
  private static final byte[] PDF = "%PDF-1.4\n% acta de prueba\n%%EOF".getBytes(StandardCharsets.US_ASCII);

  private MockHttpSession operaciones;
  private MockHttpSession jefeTecnico;
  private MockHttpSession gerencia;
  private MockHttpSession logistica;
  private Usuario usuarioJefeTecnico;
  private String clienteId;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("operaciones@iqcperu.com", false, CodigosRol.COORDINADOR_AUDITORIAS);
    usuarioJefeTecnico = crearUsuario("jefe.tecnico@iqcperu.com", false, CodigosRol.RESPONSABLE_CERTIFICACIONES);
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("logistica@iqcperu.com", false, CodigosRol.ADMINISTRACION_LOGISTICA);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    operaciones = iniciarSesion("operaciones@iqcperu.com");
    jefeTecnico = iniciarSesion("jefe.tecnico@iqcperu.com");
    gerencia = iniciarSesion("gerencia@iqcperu.com");
    logistica = iniciarSesion("logistica@iqcperu.com");
    clienteId = JsonPath.read(enviar(post("/api/v1/clientes"), gerencia,
        "{\"razonSocial\": \"Constructora Andina SAC\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"20131312955\"}", 201), "$.id");
  }

  @Test
  void otorgamientoCompletoHastaLaEntrega() throws Exception {
    String id = expedienteEnDecision("INACAL");

    // La decisión no es una transición manual
    mvc.perform(post("/api/v1/expedientes/{id}/transicion", id).session(operaciones).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"EN_EMISION\", \"version\": 4}"))
        .andExpect(jsonPath("$.codigo").value("TRANSICION_AUTOMATICA"));
    // Con la revisión observada no se otorga
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(decision("OTORGAMIENTO", "OBSERVADO", 4)))
        .andExpect(jsonPath("$.codigo").value("REVISION_OBSERVADA"));
    // Comercial no decide
    enviar(post("/api/v1/expedientes/" + id + "/decisiones"), iniciarSesion("comercial@iqcperu.com"),
        decision("OTORGAMIENTO", "CONFORME", 4), 403);

    int anio = HOY.getYear();
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(decision("OTORGAMIENTO", "ACEPTABLE_CON_OBSERVACIONES", 4)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estadoExpediente").value("EN_EMISION"))
        .andExpect(jsonPath("$.decision.decisor.id").value(usuarioJefeTecnico.getId().toString()))
        .andExpect(jsonPath("$.certificados", hasSize(2)))
        .andExpect(jsonPath("$.certificados[*].codigo", contains("PE-INA-001" + anio + "0511", "PE-INA-001" + anio + "0211")))
        .andExpect(jsonPath("$.certificados[0].estado").value("BORRADOR"))
        .andExpect(jsonPath("$.certificados[0].firmasPendientes", contains("GERENCIA")));

    String certificados = enviarGet("/api/v1/expedientes/" + id + "/certificados", operaciones);
    String c1 = JsonPath.read(certificados, "$[0].id");
    String c2 = JsonPath.read(certificados, "$[1].id");

    // Firmar exige el checklist de emisión primero
    mvc.perform(post("/api/v1/expedientes/{id}/certificados/{c}/firma", id, c1).session(gerencia).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\": \"GERENCIA\", \"version\": 0}"))
        .andExpect(jsonPath("$.codigo").value("ESTADO_CERTIFICADO"));
    enviar(post("/api/v1/expedientes/" + id + "/certificados/" + c1 + "/revision"), jefeTecnico,
        "{\"checklistConforme\": true, \"version\": 0}", 200);
    // El Jefe Técnico no puede firmar como Gerencia
    enviar(post("/api/v1/expedientes/" + id + "/certificados/" + c1 + "/firma"), jefeTecnico,
        "{\"tipo\": \"GERENCIA\", \"version\": 1}", 403);
    // Sin la fecha de certificación no se puede emitir: el certificado mostraría un ciclo vacío
    mvc.perform(post("/api/v1/expedientes/{id}/certificados/{c}/firma", id, c1).session(gerencia).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\": \"GERENCIA\", \"version\": 1}"))
        .andExpect(jsonPath("$.codigo").value("FECHAS_CICLO_REQUERIDAS"));
    enviar(post("/api/v1/expedientes/" + id + "/certificacion"), operaciones,
        "{\"fechaCertificacion\": \"%s\", \"version\": 5}".formatted(HOY), 200);

    mvc.perform(post("/api/v1/expedientes/{id}/certificados/{c}/firma", id, c1).session(gerencia).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\": \"GERENCIA\", \"version\": 1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EMITIDO"))
        .andExpect(jsonPath("$.fechaExpiracion").value(HOY.plusYears(3).toString()))
        .andExpect(jsonPath("$.versionDocumento").value("V.01 - " + HOY.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
    mvc.perform(get("/api/v1/expedientes/{id}", id).session(operaciones)).andExpect(jsonPath("$.estado").value("EN_EMISION"));

    enviar(post("/api/v1/expedientes/" + id + "/certificados/" + c2 + "/revision"), jefeTecnico,
        "{\"checklistConforme\": true, \"version\": 0}", 200);
    enviar(post("/api/v1/expedientes/" + id + "/certificados/" + c2 + "/firma"), gerencia,
        "{\"tipo\": \"GERENCIA\", \"version\": 1}", 200);
    String ficha = enviarGet("/api/v1/expedientes/" + id, operaciones);
    assertThat((String) JsonPath.read(ficha, "$.estado")).isEqualTo("EMITIDO");
    int version = JsonPath.read(ficha, "$.version");

    // Entrega bloqueada hasta que Administración registre pagos conformes y la encuesta
    mvc.perform(post("/api/v1/expedientes/{id}/entrega", id).session(logistica).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"medio\": \"DIGITAL\", \"version\": %d}".formatted(version)))
        .andExpect(jsonPath("$.codigo").value("ENTREGA_BLOQUEADA"));
    enviar(put("/api/v1/expedientes/" + id + "/condiciones-entrega"), operaciones,
        "{\"pagosConformes\": true, \"encuestaAplicada\": true, \"version\": %d}".formatted(version), 403);
    mvc.perform(put("/api/v1/expedientes/{id}/condiciones-entrega", id).session(logistica).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"pagosConformes\": true, \"encuestaAplicada\": false, \"version\": %d}".formatted(version)))
        .andExpect(jsonPath("$.puedeEntregarse").value(false))
        .andExpect(jsonPath("$.pendientes", contains("falta aplicar la encuesta de satisfacción")));
    enviar(put("/api/v1/expedientes/" + id + "/condiciones-entrega"), logistica,
        "{\"pagosConformes\": true, \"encuestaAplicada\": true, \"version\": %d}".formatted(version + 1), 200);
    mvc.perform(post("/api/v1/expedientes/{id}/entrega", id).session(logistica).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"medio\": \"DIGITAL\", \"version\": %d}".formatted(version + 2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].medioEntrega").value("DIGITAL"));
    mvc.perform(get("/api/v1/expedientes/{id}", id).session(operaciones))
        .andExpect(jsonPath("$.estado").value("ENTREGADO"))
        .andExpect(jsonPath("$.historial[*].hacia", hasItem("EMITIDO")));
    assertThat(contarBitacora("DECISION_REGISTRADA", "EXITO")).isEqualTo(1);
    assertThat(contarBitacora("CERTIFICADO_EMITIDO", "EXITO")).isEqualTo(2);
  }

  @Test
  void quienAuditoAlClienteNoPuedeDecidirYSeDerivaAlComite() throws Exception {
    String id = expedienteEnDecision("INACAL");
    // El Jefe Técnico también es auditor y participó como experto técnico en una auditoría de este cliente
    String auditorJefe = auditor("Jefe Técnico", "[\"EXPERTO_TECNICO\"]", usuarioJefeTecnico.getId().toString());
    String auditoria = JsonPath.read(enviar(post("/api/v1/expedientes/" + id + "/auditorias"), operaciones,
        "{\"tipo\": \"EXTRAORDINARIA\"}", 201), "$.id");
    enviar(put("/api/v1/expedientes/" + id + "/auditorias/" + auditoria + "/equipo"), operaciones,
        "{\"miembros\": [{\"auditorId\": \"%s\", \"rol\": \"EXPERTO_TECNICO\"}], \"version\": 0}".formatted(auditorJefe), 200);

    mvc.perform(get("/api/v1/expedientes/{id}/decisores", id).session(operaciones))
        .andExpect(jsonPath("$[?(@.nombre == 'Usuario jefe.tecnico@iqcperu.com')].habilitado", contains(false)))
        .andExpect(jsonPath("$[?(@.nombre == 'Usuario gerencia@iqcperu.com')].habilitado", contains(true)));
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(decision("OTORGAMIENTO", "CONFORME", 4)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONFLICTO_IMPARCIALIDAD"));

    String sinActa = decision("OTORGAMIENTO", "CONFORME", 4).replace("\"DECISOR\"", "\"COMITE_IMPARCIALIDAD\"");
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(sinActa))
        .andExpect(jsonPath("$.codigo").value("ACTA_REQUERIDA"));
    // Un documento de otra categoría no sirve como acta
    String declaracion = subir("DECLARACION_CONFLICTO", PDF, jefeTecnico);
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(conActa(sinActa, declaracion)))
        .andExpect(jsonPath("$.codigo").value("CATEGORIA_DOCUMENTO"));

    String acta = subir("ACTA_COMITE_IMPARCIALIDAD", PDF, jefeTecnico);
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(conActa(sinActa, acta)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.decision.via").value("COMITE_IMPARCIALIDAD"))
        .andExpect(jsonPath("$.decision.decisor").isEmpty())
        .andExpect(jsonPath("$.decision.actaDocumentoId").value(acta))
        .andExpect(jsonPath("$.estadoExpediente").value("EN_EMISION"));
  }

  @Test
  void laAuditoriaNoSeEjecutaSinLasDeclaracionesDeConflicto() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/expedientes"), operaciones, expediente("INACAL"), 201), "$.id");
    String externo = auditor("Auditora Externa", "[\"LIDER\", \"AUDITOR\"]", null);
    String interno = auditor("Jefe Técnico", "[\"AUDITOR\"]", usuarioJefeTecnico.getId().toString());
    String auditoria = JsonPath.read(enviar(post("/api/v1/expedientes/" + id + "/auditorias"), operaciones,
        "{\"tipo\": \"ETAPA_1\"}", 201), "$.id");
    enviar(put("/api/v1/expedientes/" + id + "/auditorias/" + auditoria + "/equipo"), operaciones,
        "{\"miembros\": [{\"auditorId\": \"%s\", \"rol\": \"LIDER\"}, {\"auditorId\": \"%s\", \"rol\": \"AUDITOR\"}], \"version\": 0}"
            .formatted(externo, interno), 200);
    String ruta = "/api/v1/expedientes/" + id + "/auditorias/" + auditoria;

    mvc.perform(patch(ruta, id).session(operaciones).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fechaRealInicio\": \"%s\", \"version\": 1}".formatted(HOY)))
        .andExpect(jsonPath("$.codigo").value("DECLARACIONES_PENDIENTES"));
    mvc.perform(get(ruta + "/declaraciones").session(operaciones)).andExpect(jsonPath("$.pendientes", hasSize(2)));

    // El coordinador declara por la auditora externa: necesita la declaración firmada
    mvc.perform(put(ruta + "/declaraciones/" + externo).session(operaciones).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tieneConflicto\": false}"))
        .andExpect(jsonPath("$.codigo").value("DECLARACION_FIRMADA_REQUERIDA"));
    String firmada = subir("DECLARACION_CONFLICTO", PDF, operaciones);
    mvc.perform(put(ruta + "/declaraciones/" + externo).session(operaciones).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tieneConflicto\": false, \"documentoId\": \"%s\"}".formatted(firmada)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modo").value("EN_NOMBRE_DEL_AUDITOR"));
    // Otro usuario sin permiso de gestión no puede declarar por un auditor
    enviar(put(ruta + "/declaraciones/" + externo), logistica, "{\"tieneConflicto\": false, \"version\": 0}", 403);

    // El Jefe Técnico firma su propia declaración desde su usuario y declara conflicto
    mvc.perform(put(ruta + "/declaraciones/" + interno).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tieneConflicto\": true, \"detalle\": \"Asesoré al cliente en 2025\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modo").value("FIRMA_PROPIA"));
    mvc.perform(get("/api/v1/expedientes/{id}", id).session(operaciones))
        .andExpect(jsonPath("$.auditorias[0].advertencias", hasItem("Jefe Técnico declaró conflicto de interés: retíralo del equipo.")));
    mvc.perform(patch(ruta, id).session(operaciones).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fechaRealInicio\": \"%s\", \"version\": 1}".formatted(HOY)))
        .andExpect(jsonPath("$.codigo").value("CONFLICTO_DECLARADO"));
    mvc.perform(put(ruta + "/equipo").session(operaciones).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"miembros\": [{\"auditorId\": \"%s\", \"rol\": \"LIDER\"}, {\"auditorId\": \"%s\", \"rol\": \"AUDITOR\"}], \"version\": 1}"
                .formatted(externo, interno)))
        .andExpect(jsonPath("$.codigo").value("AUDITOR_CON_CONFLICTO"));

    mvc.perform(put(ruta + "/equipo").session(operaciones).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"miembros\": [{\"auditorId\": \"%s\", \"rol\": \"LIDER\"}], \"version\": 1}".formatted(externo)))
        .andExpect(status().isOk());
    mvc.perform(patch(ruta, id).session(operaciones).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fechaRealInicio\": \"%s\", \"version\": 2}".formatted(HOY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EJECUTADA"));
  }

  @Test
  void laPoliticaDeFirmaPuedeExigirGerenciaYJefeTecnico() throws Exception {
    enviar(put("/api/v1/organizacion/parametros/POLITICA_FIRMA_CERTIFICADOS"), jefeTecnico,
        "{\"valor\": \"FIRMA_GERENCIA_Y_JEFE_TECNICO\"}", 403);
    mvc.perform(put("/api/v1/organizacion/parametros/POLITICA_FIRMA_CERTIFICADOS").session(gerencia).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"CUALQUIERA\"}"))
        .andExpect(jsonPath("$.codigo").value("VALOR_NO_ADMITIDO"));
    enviar(put("/api/v1/organizacion/parametros/POLITICA_FIRMA_CERTIFICADOS"), gerencia,
        "{\"valor\": \"FIRMA_GERENCIA_Y_JEFE_TECNICO\"}", 200);
    assertThat(contarBitacora("PARAMETRO_CAMBIADO", "EXITO")).isEqualTo(1);

    String id = expedienteEnDecision("SAE");
    String registrada = enviar(post("/api/v1/expedientes/" + id + "/decisiones"), jefeTecnico,
        decision("OTORGAMIENTO", "CONFORME", 4), 201);
    String c1 = JsonPath.read(registrada, "$.certificados[0].id");
    assertThat((String) JsonPath.read(registrada, "$.certificados[0].codigo")).startsWith("PE-SAE-001");
    enviar(post("/api/v1/expedientes/" + id + "/certificacion"), operaciones,
        "{\"fechaCertificacion\": \"%s\", \"version\": 5}".formatted(HOY), 200);
    enviar(post("/api/v1/expedientes/" + id + "/certificados/" + c1 + "/revision"), jefeTecnico,
        "{\"checklistConforme\": true, \"version\": 0}", 200);

    mvc.perform(post("/api/v1/expedientes/{id}/certificados/{c}/firma", id, c1).session(gerencia).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\": \"GERENCIA\", \"version\": 1}"))
        .andExpect(jsonPath("$.estado").value("REVISADO"))
        .andExpect(jsonPath("$.firmasPendientes", contains("JEFE_TECNICO")));
    mvc.perform(post("/api/v1/expedientes/{id}/certificados/{c}/firma", id, c1).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\": \"JEFE_TECNICO\", \"version\": 2}"))
        .andExpect(jsonPath("$.estado").value("EMITIDO"))
        .andExpect(jsonPath("$.firmaGerencia.nombre").value("Usuario gerencia@iqcperu.com"))
        .andExpect(jsonPath("$.firmaJefeTecnico.nombre").value("Usuario jefe.tecnico@iqcperu.com"));
  }

  @Test
  void laSuspensionExigeElChecklistYProgramaSusPlazos() throws Exception {
    String id = expedienteEmitido();
    int version = JsonPath.read(enviarGet("/api/v1/expedientes/" + id, operaciones), "$.version");
    String suspension = """
        {"tipo": "SUSPENSION", "clasificacion": "CONFORME", "via": "DECISOR", "fechaDecision": "%s",
         "fundamento": "No se realizó la vigilancia", "normas": ["ISO_9001"], "version": %d}""".formatted(HOY, version);
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(suspension))
        .andExpect(jsonPath("$.codigo").value("CHECKLIST_REQUERIDO"));
    String checklist = subir("CHECKLIST_SUSPENSION", PDF, jefeTecnico);
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(suspension.replace("\"normas\"", "\"checklistDocumentoId\": \"" + checklist + "\", \"normas\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.certificados", hasSize(1)))
        .andExpect(jsonPath("$.certificados[0].estado").value("SUSPENDIDO"));
    assertThat(jdbc.queryForList("SELECT fecha_limite FROM tareas_seguimiento WHERE tipo_codigo = 'SEGUIMIENTO_SUSPENSION' AND estado = 'PENDIENTE' ORDER BY fecha_limite",
        LocalDate.class)).containsExactly(HOY.plusMonths(6), HOY.plusYears(1));

    // El mantenimiento no aplica a un certificado suspendido; la restauración sí, y cierra los plazos
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(suspension.replace("SUSPENSION", "MANTENIMIENTO")))
        .andExpect(jsonPath("$.codigo").value("CERTIFICADO_SUSPENDIDO"));
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(suspension.replace("SUSPENSION", "RESTAURACION")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.certificados[0].estado").value("EMITIDO"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM tareas_seguimiento WHERE tipo_codigo = 'SEGUIMIENTO_SUSPENSION' AND estado = 'CANCELADA'",
        Long.class)).isEqualTo(2);
  }

  @Test
  void elRechazoCierraElExpediente() throws Exception {
    String id = expedienteEnDecision("INACAL");
    enviar(post("/api/v1/expedientes/" + id + "/decisiones"), jefeTecnico, decision("RECHAZO", "OBSERVADO", 4), 201);
    mvc.perform(get("/api/v1/expedientes/{id}", id).session(operaciones))
        .andExpect(jsonPath("$.estado").value("RECHAZADO"))
        .andExpect(jsonPath("$.transicionesPermitidas", hasSize(0)));
    mvc.perform(post("/api/v1/expedientes/{id}/auditorias", id).session(operaciones).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\": \"EXTRAORDINARIA\"}"))
        .andExpect(jsonPath("$.codigo").value("EXPEDIENTE_CERRADO"));
  }

  @Test
  void enIqcGlobalSeRegistraElCertificadoQueEnviaIndia() throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/expedientes"), operaciones, expediente("IQC_GLOBAL"), 201), "$.id");
    mvc.perform(post("/api/v1/expedientes/{id}/decisiones", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(decision("OTORGAMIENTO", "CONFORME", 0)))
        .andExpect(jsonPath("$.codigo").value("DECISION_INDIA"));
    String[] pasos = {"PROPOSAL_RECIBIDO", "PROPOSAL_FIRMADO", "PAQUETE_FASE1_RECIBIDO", "PAQUETE_FASE1_ENVIADO",
        "PAQUETE_FASE2_RECIBIDO", "PAQUETE_FASE2_ENVIADO", "CERTIFICADO_RECIBIDO", "TRADUCIDO"};
    for (int i = 0; i < pasos.length; i++) {
      enviar(post("/api/v1/expedientes/" + id + "/transicion"), operaciones,
          "{\"estado\": \"%s\", \"version\": %d}".formatted(pasos[i], i), 200);
    }
    String traduccion = subir("TRADUCCION_CERTIFICADO", PDF, jefeTecnico);
    String cuerpo = """
        {"norma": "ISO_9001", "codigo": "qa-26-0045", "fechaEmision": "%s", "fechaExpiracion": "%s",
         "traduccionDocumentoId": "%s"}""".formatted(HOY, HOY.plusYears(3), traduccion);
    mvc.perform(post("/api/v1/expedientes/{id}/certificados/externos", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.codigo").value("QA-26-0045"))
        .andExpect(jsonPath("$.emitidoPorIndia").value(true))
        .andExpect(jsonPath("$.estado").value("EMITIDO"));
    mvc.perform(post("/api/v1/expedientes/{id}/certificados/externos", id).session(jefeTecnico).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(jsonPath("$.codigo").value("CERTIFICADO_DUPLICADO"));
    mvc.perform(get("/api/v1/expedientes/{id}/condiciones-entrega", id).session(logistica))
        .andExpect(jsonPath("$.pendientes", hasItem("faltan certificados emitidos de ISO_45001")))
        .andExpect(jsonPath("$.pendientes", not(hasItem("el expediente debe estar en TRADUCIDO (está en TRADUCIDO)"))));
  }

  @Test
  void losDocumentosSeValidanPorSuContenidoYSeDescarganIntactos() throws Exception {
    mvc.perform(multipart("/api/v1/documentos").file(new MockMultipartFile("archivo", "acta.pdf", "application/pdf",
            "<html>no es un pdf</html>".getBytes(StandardCharsets.UTF_8))).param("categoria", "OTRO")
            .session(operaciones).with(csrf()))
        .andExpect(jsonPath("$.codigo").value("FORMATO_NO_ADMITIDO"));
    mvc.perform(multipart("/api/v1/documentos").file(new MockMultipartFile("archivo", "acta.pdf", "application/pdf", PDF))
            .param("categoria", "OTRO").session(iniciarSesion("comercial@iqcperu.com")).with(csrf()))
        .andExpect(status().isForbidden());

    String id = subir("ACTA_COMITE_IMPARCIALIDAD", PDF, operaciones);
    mvc.perform(get("/api/v1/documentos/{id}", id).session(jefeTecnico))
        .andExpect(jsonPath("$.tipoContenido").value("application/pdf"))
        .andExpect(jsonPath("$.tamanoBytes").value(PDF.length))
        .andExpect(jsonPath("$.sha256").isNotEmpty());
    mvc.perform(get("/api/v1/documentos/{id}/contenido", id).session(jefeTecnico))
        .andExpect(status().isOk())
        .andExpect(content().bytes(PDF));
  }

  /**expediente con ISO 9001 y 45001, llevado hasta EN_DECISION (versión 4)*/
  private String expedienteEnDecision(String esquema) throws Exception {
    String id = JsonPath.read(enviar(post("/api/v1/expedientes"), operaciones, expediente(esquema), 201), "$.id");
    String[] pasos = {"ETAPA1_REALIZADA", "ETAPA2_REALIZADA", "EXPEDIENTE_VERIFICADO", "EN_DECISION"};
    for (int i = 0; i < pasos.length; i++) {
      enviar(post("/api/v1/expedientes/" + id + "/transicion"), operaciones,
          "{\"estado\": \"%s\", \"version\": %d}".formatted(pasos[i], i), 200);
    }
    return id;
  }

  /**expediente INACAL con sus dos certificados emitidos (política por defecto: firma de Gerencia)*/
  private String expedienteEmitido() throws Exception {
    String id = expedienteEnDecision("INACAL");
    String registrada = enviar(post("/api/v1/expedientes/" + id + "/decisiones"), jefeTecnico,
        decision("OTORGAMIENTO", "CONFORME", 4), 201);
    enviar(post("/api/v1/expedientes/" + id + "/certificacion"), operaciones,
        "{\"fechaCertificacion\": \"%s\", \"version\": 5}".formatted(HOY), 200);
    for (int i = 0; i < 2; i++) {
      String c = JsonPath.read(registrada, "$.certificados[" + i + "].id");
      enviar(post("/api/v1/expedientes/" + id + "/certificados/" + c + "/revision"), jefeTecnico,
          "{\"checklistConforme\": true, \"version\": 0}", 200);
      enviar(post("/api/v1/expedientes/" + id + "/certificados/" + c + "/firma"), gerencia,
          "{\"tipo\": \"GERENCIA\", \"version\": 1}", 200);
    }
    return id;
  }

  private String expediente(String esquema) {
    return """
        {"clienteId": "%s", "esquema": "%s", "tipoServicio": "CERTIFICACION_INICIAL",
         "normas": [{"norma": "ISO_45001", "edicion": "2018"}, {"norma": "ISO_9001", "edicion": "2015"}],
         "alcance": "Construcción de obras civiles", "numeroContrato": "001-2026"}
        """.formatted(clienteId, esquema);
  }

  private String decision(String tipo, String clasificacion, int version) {
    return """
        {"tipo": "%s", "clasificacion": "%s", "via": "DECISOR", "fechaDecision": "%s",
         "fundamento": "Revisión técnica del expediente", "version": %d}""".formatted(tipo, clasificacion, HOY, version);
  }

  private static String conActa(String cuerpo, String acta) {
    return cuerpo.replace("\"version\"", "\"actaDocumentoId\": \"" + acta + "\", \"expertoImparcialidad\": \"Experta externa\", \"version\"");
  }

  private String auditor(String nombre, String roles, String usuarioId) throws Exception {
    String usuario = usuarioId == null ? "" : ", \"usuarioId\": \"" + usuarioId + "\"";
    return JsonPath.read(enviar(post("/api/v1/auditores"), operaciones,
        "{\"nombreCompleto\": \"%s\", \"roles\": %s, \"normas\": [\"ISO_9001\", \"ISO_45001\"]%s}".formatted(nombre, roles, usuario), 201), "$.id");
  }

  private String subir(String categoria, byte[] contenido, MockHttpSession sesion) throws Exception {
    String respuesta = mvc.perform(multipart("/api/v1/documentos")
            .file(new MockMultipartFile("archivo", "documento.pdf", "application/pdf", contenido))
            .param("categoria", categoria).session(sesion).with(csrf()))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(respuesta, "$.id");
  }

  private String enviarGet(String ruta, MockHttpSession sesion) throws Exception {
    return mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  private String enviar(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo,
      int estadoEsperado) throws Exception {
    return mvc.perform(solicitud.session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(estadoEsperado))
        .andReturn().getResponse().getContentAsString();
  }
}
