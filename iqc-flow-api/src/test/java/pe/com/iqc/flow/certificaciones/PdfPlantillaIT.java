package pe.com.iqc.flow.certificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**modo «Desde plantilla»: el PDF del certificado se dibuja con la plantilla y queda como versión vigente*/
class PdfPlantillaIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));

  private MockHttpSession master;
  private MockHttpSession comercial;
  private String cliente;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    master = iniciarSesion("gerencia@iqcperu.com");
    comercial = iniciarSesion("comercial@iqcperu.com");
    cliente = JsonPath.read(enviar(post("/api/v1/clientes"), master,
        "{\"razonSocial\": \"Constructora Andina SAC\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"20131312955\"}", 201), "$.id");
  }

  @Test
  void generaElPdfEmpresarialLoVersionaYSoloLoPideQuienTienePermiso() throws Exception {
    String id = registrarEmpresarial("SAE", "PE-SAE-03820260111A", "ISO_37001", "2025");

    mvc.perform(get("/api/v1/certificados/{id}/vista-previa-plantilla", id).session(comercial)).andExpect(status().isForbidden());

    byte[] vista = mvc.perform(get("/api/v1/certificados/{id}/vista-previa-plantilla", id).session(master))
        .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andReturn().getResponse().getContentAsByteArray();
    String texto = textoDe(vista);
    assertThat(texto).contains("Certificado No.: PE-SAE-03820260111A", "ISO 37001:2025", "Constructora Andina SAC",
        "Sistema de Gestión Antisoborno", "Acreditación N° SAE CSGAS 24-001", "Fiorella Tenorio Alfaro");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM certificados_pdf", Integer.class)).isZero();

    String primero = enviar(post("/api/v1/certificados/" + id + "/pdf-plantilla"), master, "", 200);
    assertThat((String) JsonPath.read(primero, "$.nombreArchivo")).isEqualTo("Certificado_PE-SAE-03820260111A.pdf");
    mvc.perform(get("/api/v1/certificados/{id}", id).session(master)).andExpect(jsonPath("$.tienePdf").value(true));
    enviar(post("/api/v1/certificados/" + id + "/pdf-plantilla"), master, "", 200);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM certificados_pdf", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'CERTIFICADO_PDF_DE_PLANTILLA'", Long.class))
        .isEqualTo(2);
  }

  @Test
  void elCertificadoDeIqcGlobalSeGeneraConSuPropioDiseno() throws Exception {
    String id = registrarEmpresarial("IQC_GLOBAL", "PER/QEO/JAS-C0000/0001", "ISO_9001", "2015");
    byte[] vista = mvc.perform(get("/api/v1/certificados/{id}/vista-previa-plantilla", id).session(master))
        .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
    assertThat(textoDe(vista)).contains("INTEGRATED QUALITY CERTIFICATION PRIVATE LIMITED", "Constructora Andina SAC", "ISO 9001:2015",
        "Sistemas de Gestión de la Calidad", ": PER/QEO/JAS-C0000/0001", "Suma Shankar", "Fecha de emisión inicial");

    String generado = enviar(post("/api/v1/certificados/" + id + "/pdf-plantilla"), master, "", 200);
    assertThat((String) JsonPath.read(generado, "$.nombreArchivo")).isEqualTo("Certificado_PER_QEO_JAS-C0000_0001.pdf");
    mvc.perform(get("/api/v1/certificados/{id}", id).session(master)).andExpect(jsonPath("$.tienePdf").value(true));
  }

  @Test
  void generaElPdfDeUnCertificadoPersonalYLoAdjunta() throws Exception {
    String creado = enviar(post("/api/v1/certificados-personales"), master, """
        {"persona": {"codigoPais": "PE", "tipoDocumento": "DNI", "numeroDocumento": "45678912", "nombres": "José Pérez Quispe"},
         "codigo": "IQC/PE/ABM9002",
         "datos": {"curso": "Curso de auditor líder en base a la 17024:2012", "normaReferencia": "ISO 37001", "normaAnio": "2016",
           "duracionHoras": 16, "modalidad": "Virtual", "fechaInicio": "2026-11-01", "fechaFin": "2026-11-03",
           "fechaEmision": "2026-11-10", "entrenador": "Ana Torres"}}""", 201);
    String id = JsonPath.read(creado, "$.id");
    assertThat((Boolean) JsonPath.read(creado, "$.tienePdf")).isFalse();

    byte[] vista = mvc.perform(get("/api/v1/certificados-personales/{id}/vista-previa-plantilla", id).session(master))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertThat(textoDe(vista)).contains("José Pérez Quispe", "DNI 45678912", "ISO 37001:2016", "16 horas", "IQC/PE/ABM9002");

    String generado = enviar(post("/api/v1/certificados-personales/" + id + "/pdf-plantilla"), master, "", 200);
    assertThat((Boolean) JsonPath.read(generado, "$.tienePdf")).isTrue();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM documentos WHERE categoria = 'CERTIFICADO_PDF'", Integer.class)).isEqualTo(1);
  }

  @Test
  void conElModoPlantillaApagadoNoSeGeneraNiSeVeLaVistaPrevia() throws Exception {
    String id = registrarEmpresarial("SAE", "PE-SAE-03820260111A", "ISO_37001", "2025");
    jdbc.update("INSERT INTO parametros_organizacion (organizacion_id, clave, valor, actualizado_en) VALUES (?, ?, 'NO', now())",
        organizacionId, "CERT_EMPRESARIAL_MODO_PLANTILLA");
    mvc.perform(get("/api/v1/certificados/{id}/vista-previa-plantilla", id).session(master))
        .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("MODO_DESHABILITADO"));
  }

  @Test
  void laVistaPreviaDelBorradorDibujaLoEscritoSinGuardarNada() throws Exception {
    byte[] personal = enviarPdf("/api/v1/certificados-personales/vista-previa-borrador", """
        {"codigoPais": "PE", "tipoDocumento": "DNI", "numeroDocumento": "45678912", "nombres": "José Pérez Quispe",
         "codigo": "IQC/PE/ABM9003", "curso": "Curso de auditor líder", "normaReferencia": "ISO 37001", "normaAnio": "2016"}""");
    assertThat(textoDe(personal)).contains("José Pérez Quispe", "ISO 37001:2016", "IQC/PE/ABM9003");

    // sin nada escrito todavía: el PDF sale con guiones en lugar de fallar
    assertThat(textoDe(enviarPdf("/api/v1/certificados-personales/vista-previa-borrador", "{}"))).contains("—");

    byte[] empresarial = enviarPdf("/api/v1/certificados/vista-previa-borrador", """
        {"clienteId": "%s", "esquema": "SAE", "norma": "ISO_37001", "edicion": "2025", "codigo": "PE-SAE-BORRADOR1",
         "alcance": "Diseño y construcción de obras civiles", "fechaOtorgamiento": "%s", "fechaEmision": "%s", "fechaExpiracion": "%s"}"""
        .formatted(cliente, HOY, HOY, HOY.plusYears(3)));
    assertThat(textoDe(empresarial)).contains("Constructora Andina SAC", "ISO 37001:2025", "PE-SAE-BORRADOR1");

    assertThat(jdbc.queryForObject("SELECT count(*) FROM certificados_personales", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM documentos WHERE categoria = 'CERTIFICADO_PDF'", Integer.class)).isZero();

    byte[] global = enviarPdf("/api/v1/certificados/vista-previa-borrador", """
        {"clienteId": "%s", "esquema": "IQC_GLOBAL", "norma": "ISO_14001", "edicion": "2015", "codigo": "PER/QEO/BORRADOR",
         "fechaOtorgamiento": "%s", "fechaEmision": "%s", "fechaExpiracion": "%s"}""".formatted(cliente, HOY, HOY, HOY.plusYears(3)));
    assertThat(textoDe(global)).contains("Constructora Andina SAC", "ISO 14001:2015", "Sistemas de Gestión Ambiental", ": PER/QEO/BORRADOR");

    mvc.perform(post("/api/v1/certificados-personales/vista-previa-borrador").session(comercial).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/certificados/vista-previa-borrador").session(master).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"esquema\": \"SAE\"}")).andExpect(status().isBadRequest());
  }

  @Test
  void elCertificadoDeIqcGlobalGuardaYImprimeSubdivisionClaseProcesosYAnexos() throws Exception {
    String r = enviar(post("/api/v1/certificados/registro-directo"), master, """
        {"clienteId": "%s", "esquema": "IQC_GLOBAL", "subdivision": "46", "clase": "4610",
         "fechaOtorgamiento": "%s", "fechaEmision": "%s", "fechaExpiracion": "%s", "motivo": "Prueba",
         "certificados": [{"norma": "ISO_9001", "edicion": "2015", "codigo": "PER/QEO/JAS-C0000/0001",
                           "procesosNoAplicables": "8.3", "anexos": "NA"}]}"""
        .formatted(cliente, HOY, HOY, HOY.plusYears(3)), 201);
    String id = JsonPath.read(r, "$.certificados[0].id");
    assertThat((String) JsonPath.read(r, "$.certificados[0].subdivision")).isEqualTo("46");
    assertThat((String) JsonPath.read(r, "$.certificados[0].procesosNoAplicables")).isEqualTo("8.3");

    byte[] pdf = mvc.perform(get("/api/v1/certificados/{id}/vista-previa-plantilla", id).session(master))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertThat(textoDe(pdf)).containsPattern("Subdivisi.n\\s*: 46").containsPattern("Clase\\s*: 4610")
        .containsPattern("Procesos no aplicables\\s*: 8\\.3").containsPattern("Anexos\\s*: NA");

    // el borrador usa lo escrito en el formulario, sin guardar nada
    byte[] borrador = enviarPdf("/api/v1/certificados/vista-previa-borrador", """
        {"clienteId": "%s", "esquema": "IQC_GLOBAL", "norma": "ISO_9001", "edicion": "2015", "codigo": "PER/QEO/BORRADOR/1",
         "subdivision": "70, 59", "clase": "7000, 5910", "procesosNoAplicables": "7.1.5, 8.3", "anexos": "Anexo 1",
         "fechaOtorgamiento": "%s", "fechaEmision": "%s", "fechaExpiracion": "%s"}"""
        .formatted(cliente, HOY, HOY, HOY.plusYears(3)));
    assertThat(textoDe(borrador)).containsPattern("Subdivisi.n\\s*: 70, 59").containsPattern("Procesos no aplicables\\s*: 7\\.1\\.5, 8\\.3")
        .containsPattern("Anexos\\s*: Anexo 1");
  }

  @Test
  void losDatosDeSectorSeValidanYSoloSeGuardanConIqcGlobal() throws Exception {
    String base = """
        {"clienteId": "%s", "esquema": "%s", "subdivision": "%s", "fechaOtorgamiento": "%s", "fechaEmision": "%s",
         "fechaExpiracion": "%s", "motivo": "Prueba", "certificados": [{"norma": "ISO_9001", "edicion": "2015", "codigo": "%s",
         "procesosNoAplicables": "%s"}]}""";
    String ok = base.formatted(cliente, "IQC_GLOBAL", "46", HOY, HOY, HOY.plusYears(3), "PER/X/1", "NA");
    // formato inválido: letras en la subdivisión, o procesos que no son numerales ni NA
    enviar(post("/api/v1/certificados/registro-directo"), master, base.formatted(cliente, "IQC_GLOBAL", "abc", HOY, HOY, HOY.plusYears(3), "PER/X/2", "NA"), 400);
    enviar(post("/api/v1/certificados/registro-directo"), master, base.formatted(cliente, "IQC_GLOBAL", "46", HOY, HOY, HOY.plusYears(3), "PER/X/3", "todos"), 400);
    enviar(post("/api/v1/certificados/registro-directo"), master, ok, 201);
    // con INACAL/SAE los datos de India se descartan
    String sae = enviar(post("/api/v1/certificados/registro-directo"), master, base.formatted(cliente, "SAE", "46", HOY, HOY, HOY.plusYears(3), "PE-SAE-X1", "8.3")
        .replace("ISO_9001", "ISO_37001").replace("2015", "2025"), 201);
    assertThat((Object) JsonPath.read(sae, "$.certificados[0].subdivision")).isNull();
    assertThat((Object) JsonPath.read(sae, "$.certificados[0].procesosNoAplicables")).isNull();
  }

  private byte[] enviarPdf(String ruta, String cuerpo) throws Exception {
    return mvc.perform(post(ruta).session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
  }

  private String registrarEmpresarial(String esquema, String codigo, String norma, String edicion) throws Exception {
    String r = enviar(post("/api/v1/certificados/registro-directo"), master, """
        {"clienteId": "%s", "esquema": "%s", "alcance": "Diseño y construcción de obras civiles",
         "fechaOtorgamiento": "%s", "fechaEmision": "%s", "fechaExpiracion": "%s", "motivo": "Prueba",
         "certificados": [{"norma": "%s", "edicion": "%s", "codigo": "%s"}]}"""
        .formatted(cliente, esquema, HOY.minusMonths(1), HOY.minusMonths(1), HOY.minusMonths(1).plusYears(3), norma, edicion, codigo), 201);
    return JsonPath.read(r, "$.certificados[0].id");
  }

  private static String textoDe(byte[] pdf) throws IOException {
    try (PDDocument documento = Loader.loadPDF(pdf)) {
      assertThat(documento.getNumberOfPages()).isEqualTo(1);
      return new PDFTextStripper().getText(documento);
    }
  }

  private String enviar(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo, int estadoEsperado)
      throws Exception {
    return mvc.perform(solicitud.session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(estadoEsperado)).andReturn().getResponse().getContentAsString();
  }
}
