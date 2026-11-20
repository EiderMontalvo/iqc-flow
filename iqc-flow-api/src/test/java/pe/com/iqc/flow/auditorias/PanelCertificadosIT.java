package pe.com.iqc.flow.auditorias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/**Incremento 8a: panel de certificados (búsqueda, registro directo, PDF con versiones, marca y Excel)*/
class PanelCertificadosIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));
  private static final byte[] PDF = "%PDF-1.4\n% certificado de prueba\n%%EOF".getBytes(StandardCharsets.US_ASCII);

  private MockHttpSession master;
  private MockHttpSession comercial;
  private String clientePeru;
  private String clienteEcuador;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    master = iniciarSesion("gerencia@iqcperu.com");
    comercial = iniciarSesion("comercial@iqcperu.com");
    clientePeru = JsonPath.read(enviar(post("/api/v1/clientes"), master,
        "{\"razonSocial\": \"Constructora Andina SAC\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"20131312955\"}", 201), "$.id");
    clienteEcuador = JsonPath.read(enviar(post("/api/v1/clientes"), master, """
        {"razonSocial": "Pesquera del Pacífico S.A.", "codigoPais": "EC", "tipoDocumento": "RUC",
         "numeroDocumento": "0991234567001"}""", 201), "$.id");
  }

  @Test
  void registroDirectoCreaElExpedienteEntregadoConSusVigilanciasYEvitaCodigosRepetidos() throws Exception {
    String cuerpo = registro(clienteEcuador, "SAE", HOY.minusMonths(2), HOY.minusMonths(2).plusYears(3),
        HOY.plusMonths(10), "PE-SAE-00220250111A", "ISO_37001", "2025", null);
    enviar(post("/api/v1/certificados/registro-directo"), comercial, cuerpo, 403);

    String r = enviar(post("/api/v1/certificados/registro-directo"), master, cuerpo, 201);
    assertThat((Integer) JsonPath.read(r, "$.vigilancias")).isEqualTo(2);
    assertThat((Integer) JsonPath.read(r, "$.tareas")).isEqualTo(2);
    assertThat((String) JsonPath.read(r, "$.certificados[0].estado")).isEqualTo("EMITIDO");
    assertThat((String) JsonPath.read(r, "$.certificados[0].paisMarca")).isEqualTo("EC");
    assertThat((Boolean) JsonPath.read(r, "$.certificados[0].marcaManual")).isFalse();
    String expediente = JsonPath.read(r, "$.expedienteId");
    mvc.perform(get("/api/v1/expedientes/{id}", expediente).session(master))
        .andExpect(jsonPath("$.estado").value("ENTREGADO"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'CERTIFICADO_REGISTRO_DIRECTO'",
        Long.class)).isEqualTo(1);

    // El mismo código para la misma norma no se puede registrar dos veces
    mvc.perform(post("/api/v1/certificados/registro-directo").session(master).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(jsonPath("$.codigo").value("CODIGO_DUPLICADO"));
    // Expiración antes del otorgamiento
    mvc.perform(post("/api/v1/certificados/registro-directo").session(master).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(registro(clientePeru, "INACAL", HOY, HOY.minusDays(1), null,
                "PE-INA-00920250111", "ISO_9001", "2015", null)))
        .andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));
  }

  @Test
  void buscaPorTextoPaisYVigenciaYExportaAExcel() throws Exception {
    enviar(post("/api/v1/certificados/registro-directo"), master, registro(clientePeru, "INACAL", HOY.minusYears(3).minusDays(10),
        HOY.minusDays(10), null, "PE-INA-00120220111", "ISO_9001", "2015", null), 201);
    enviar(post("/api/v1/certificados/registro-directo"), master, registro(clientePeru, "INACAL", HOY.minusYears(2),
        HOY.plusDays(30), null, "PE-INA-00220230511", "ISO_45001", "2018", null), 201);
    enviar(post("/api/v1/certificados/registro-directo"), master, registro(clienteEcuador, "SAE", HOY.minusYears(1),
        HOY.plusYears(2), null, "PE-SAE-00320240211", "ISO_9001", "2015", null), 201);

    mvc.perform(get("/api/v1/certificados").session(master))
        .andExpect(jsonPath("$.totalElementos").value(3));
    // Por razón social sin tildes ni mayúsculas, y por documento
    mvc.perform(get("/api/v1/certificados").param("texto", "pesquera pacifico").session(master))
        .andExpect(jsonPath("$.contenido[*].codigo", contains("PE-SAE-00320240211")));
    mvc.perform(get("/api/v1/certificados").param("texto", "20131312955").session(master))
        .andExpect(jsonPath("$.contenido", hasSize(2)));
    mvc.perform(get("/api/v1/certificados").param("texto", "ina-0022").session(master))
        .andExpect(jsonPath("$.contenido[*].codigo", contains("PE-INA-00220230511")));
    mvc.perform(get("/api/v1/certificados").param("pais", "EC").session(master))
        .andExpect(jsonPath("$.contenido[*].cliente.razonSocial", contains("Pesquera del Pacífico S.A.")));
    mvc.perform(get("/api/v1/certificados").param("vigencia", "VENCIDO").session(master))
        .andExpect(jsonPath("$.contenido[*].codigo", contains("PE-INA-00120220111")));
    mvc.perform(get("/api/v1/certificados").param("vigencia", "POR_VENCER").session(master))
        .andExpect(jsonPath("$.contenido[*].codigo", contains("PE-INA-00220230511")))
        .andExpect(jsonPath("$.contenido[0].vigencia").value("POR_VENCER"));
    mvc.perform(get("/api/v1/certificados").param("vigencia", "VIGENTE").session(master))
        .andExpect(jsonPath("$.contenido[*].codigo", containsInAnyOrder("PE-INA-00220230511", "PE-SAE-00320240211")));
    mvc.perform(get("/api/v1/certificados").param("tamano", "1").param("pagina", "1").session(master))
        .andExpect(jsonPath("$.contenido", hasSize(1)))
        .andExpect(jsonPath("$.totalPaginas").value(3));

    byte[] excel = mvc.perform(get("/api/v1/certificados/exportacion").param("pais", "PE").session(master))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();
    // Un .xlsx es un ZIP
    assertThat(new String(excel, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
    try (var libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel))) {
      assertThat(libro.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(3);
      assertThat(libro.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Código");
    }
  }

  @Test
  void elPdfSeVersionaYLaMarcaSePuedeElegirAMano() throws Exception {
    String r = enviar(post("/api/v1/certificados/registro-directo"), master, registro(clienteEcuador, "SAE",
        HOY.minusYears(1), HOY.plusYears(2), null, "PE-SAE-00420240211", "ISO_9001", "2015", null), 201);
    String id = JsonPath.read(r, "$.certificados[0].id");
    assertThat((Boolean) JsonPath.read(r, "$.certificados[0].tienePdf")).isFalse();

    String otro = subir("OTRO", PDF);
    mvc.perform(put("/api/v1/certificados/{id}/pdf", id).session(master).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"documentoId\": \"%s\", \"version\": 0}".formatted(otro)))
        .andExpect(jsonPath("$.codigo").value("CATEGORIA_DOCUMENTO"));
    String v1 = subir("CERTIFICADO_PDF", PDF);
    String conPdf = enviar(put("/api/v1/certificados/" + id + "/pdf"), master,
        "{\"documentoId\": \"%s\", \"version\": 0}".formatted(v1), 200);
    assertThat((Boolean) JsonPath.read(conPdf, "$.tienePdf")).isTrue();
    // Una imagen no es un certificado en PDF
    String imagen = subir("CERTIFICADO_PDF", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0});
    mvc.perform(put("/api/v1/certificados/{id}/pdf", id).session(master).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"documentoId\": \"%s\", \"version\": 1}".formatted(imagen)))
        .andExpect(jsonPath("$.codigo").value("FORMATO_NO_ADMITIDO"));
    String v2 = subir("CERTIFICADO_PDF", "%PDF-1.4\n% corregido\n%%EOF".getBytes(StandardCharsets.US_ASCII));
    enviar(put("/api/v1/certificados/" + id + "/pdf"), master,
        "{\"documentoId\": \"%s\", \"motivo\": \"Corrección del alcance\", \"version\": 1}".formatted(v2), 200);
    mvc.perform(get("/api/v1/certificados/{id}/pdf/versiones", id).session(master))
        .andExpect(jsonPath("$[*].numero", contains(2, 1)))
        .andExpect(jsonPath("$[*].vigente", contains(true, false)))
        .andExpect(jsonPath("$[0].motivo").value("Corrección del alcance"));

    // Cliente ecuatoriano atendido con la marca de Perú
    enviar(put("/api/v1/certificados/" + id + "/marca"), comercial, "{\"pais\": \"PE\", \"motivo\": \"x\", \"version\": 2}", 403);
    String marca = enviar(put("/api/v1/certificados/" + id + "/marca"), master,
        "{\"pais\": \"PE\", \"motivo\": \"Lo atiende IQC Perú\", \"version\": 2}", 200);
    assertThat((String) JsonPath.read(marca, "$.paisMarca")).isEqualTo("PE");
    assertThat((Boolean) JsonPath.read(marca, "$.marcaManual")).isTrue();
    mvc.perform(get("/api/v1/certificados").param("pais", "PE").session(master))
        .andExpect(jsonPath("$.contenido[*].codigo", contains("PE-SAE-00420240211")));
    String vuelta = enviar(put("/api/v1/certificados/" + id + "/marca"), master,
        "{\"pais\": null, \"motivo\": \"Vuelve a IQC Ecuador\", \"version\": 3}", 200);
    assertThat((String) JsonPath.read(vuelta, "$.paisMarca")).isEqualTo("EC");
  }

  private static String registro(String cliente, String esquema, LocalDate otorgamiento, LocalDate expiracion,
      LocalDate vigilancia1, String codigo, String norma, String edicion, String pdf) {
    String vigilancias = vigilancia1 == null ? ""
        : ", \"limiteVigilancia1\": \"%s\", \"limiteVigilancia2\": \"%s\"".formatted(vigilancia1, vigilancia1.plusYears(1));
    return """
        {"clienteId": "%s", "esquema": "%s", "alcance": "Diseño y construcción", "fechaOtorgamiento": "%s",
         "fechaEmision": "%s", "fechaExpiracion": "%s", "motivo": "Emitido antes del sistema"%s,
         "certificados": [{"norma": "%s", "edicion": "%s", "codigo": "%s"%s}]}"""
        .formatted(cliente, esquema, otorgamiento, otorgamiento, expiracion, vigilancias, norma, edicion, codigo,
            pdf == null ? "" : ", \"documentoPdfId\": \"" + pdf + "\"");
  }

  private String subir(String categoria, byte[] contenido) throws Exception {
    String respuesta = mvc.perform(multipart("/api/v1/documentos")
            .file(new MockMultipartFile("archivo", "certificado.pdf", "application/pdf", contenido))
            .param("categoria", categoria).session(master).with(csrf()))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(respuesta, "$.id");
  }

  private String enviar(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo,
      int estadoEsperado) throws Exception {
    return mvc.perform(solicitud.session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(estadoEsperado))
        .andReturn().getResponse().getContentAsString();
  }
}
