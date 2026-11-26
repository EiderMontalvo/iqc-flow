package pe.com.iqc.flow.cartas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.cartas.infrastructure.ConvertidorPdf;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**
 *cartas de punta a punta: firma de la persona, certificados del cliente, vista previa, emisión numerada, PDF y Word.
 *LibreOffice se simula (guarda el Word que recibe y devuelve un PDF mínimo): el llenado de la plantilla sí es real
 */
class CartasIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));
  private static final byte[] PDF = "%PDF-1.4 carta de prueba".getBytes();

  @MockitoBean private ConvertidorPdf convertidor;

  private final List<byte[]> wordsConvertidos = new ArrayList<>();
  private MockHttpSession master;
  private MockHttpSession comercial;
  private String cliente;

  @BeforeEach
  void preparar() throws Exception {
    when(convertidor.aPdf(any())).thenAnswer(i -> { wordsConvertidos.add(i.getArgument(0)); return PDF; });
    when(convertidor.disponible()).thenReturn(true);
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    master = iniciarSesion("gerencia@iqcperu.com");
    comercial = iniciarSesion("comercial@iqcperu.com");
    cliente = JsonPath.read(enviar(post("/api/v1/clientes"), master,
        "{\"razonSocial\": \"Constructora Andina SAC\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"20131312955\"}", 201), "$.id");
  }

  @Test
  void emiteUnaCartaDeSuspensionNumeradaConLaFirmaYLasNormasDelCliente() throws Exception {
    List<String> certs = registrarCertificados();
    String cuerpo = """
        {"tipo": "SUSPENSION_PAGO", "clienteId": "%s", "certificadoIds": ["%s", "%s"], "fecha": "2026-10-26", "firmanteId": %s}""";

    // Comercial solo consulta cartas
    enviar(post("/api/v1/cartas/vista-previa"), comercial, cuerpo.formatted(cliente, certs.get(0), certs.get(1), "null"), 403);
    mvc.perform(get("/api/v1/cartas").session(comercial)).andExpect(status().isOk());

    mvc.perform(get("/api/v1/cartas/clientes/{id}/certificados", cliente).session(master))
        .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[?(@.norma == 'ISO 37001:2025')]").exists());

    // la vista previa acepta la carta sin firmante; emitirla no
    enviar(post("/api/v1/cartas/vista-previa"), master, cuerpo.formatted(cliente, certs.get(0), certs.get(1), "null"), 200);
    enviar(post("/api/v1/cartas"), master, cuerpo.formatted(cliente, certs.get(0), certs.get(1), "null"), 422);

    // la persona sube su firma y aparece como firmante
    String gerencia = jdbc.queryForObject("SELECT id::text FROM usuarios WHERE correo = 'gerencia@iqcperu.com'", String.class);
    mvc.perform(multipart(HttpMethod.PUT, "/api/v1/cartas/mi-firma").file(new MockMultipartFile("archivo", "firma.png", "image/png", png(400, 160)))
        .param("cargo", "Gerente General").param("nombreMostrado", "Fiorella A. Tenorio Alfaro").session(master).with(csrf()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.tieneFirma").value(true));
    mvc.perform(get("/api/v1/cartas/firmantes").session(master))
        .andExpect(jsonPath("$[0].nombre").value("Fiorella A. Tenorio Alfaro")).andExpect(jsonPath("$[0].cargo").value("Gerente General"));

    wordsConvertidos.clear();
    String emitida = enviar(post("/api/v1/cartas"), master, cuerpo.formatted(cliente, certs.get(0), certs.get(1), "\"" + gerencia + "\""), 201);
    assertThat((String) JsonPath.read(emitida, "$.numero")).isEqualTo("CAR-2026-0001");
    assertThat((String) JsonPath.read(emitida, "$.asunto")).isEqualTo("Suspensión de las certificaciones ISO 37001 e ISO 9001 por falta de pago");
    String id = JsonPath.read(emitida, "$.id");

    String texto = textoWord(wordsConvertidos.get(0));
    assertThat(texto).contains("Constructora Andina SAC", "26 de octubre de 2026", "ISO 37001:2025 e ISO 9001:2015",
        "ISO 37001: Sistema de Gestión Antisoborno.", "ISO 9001: Sistema de Gestión de la Calidad.", "Fiorella A. Tenorio Alfaro",
        "Gerente General", "IQC PERÚ S.A.C.").doesNotContain("{{");

    byte[] pdf = mvc.perform(get("/api/v1/cartas/{id}/pdf", id).session(comercial)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();
    assertThat(pdf).isEqualTo(PDF);
    byte[] word = mvc.perform(get("/api/v1/cartas/{id}/word", id).session(master)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();
    assertThat(textoWord(word)).contains("Constructora Andina SAC", "ISO 9001: Sistema de Gestión de la Calidad.");
    mvc.perform(get("/api/v1/cartas/{id}/word", id).session(comercial)).andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/cartas").param("texto", "andina").session(master))
        .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].firmante").value("Fiorella A. Tenorio Alfaro"));

    // la imagen de la firma nunca se descarga por la ruta general de documentos
    String firma = jdbc.queryForObject("SELECT documento_id::text FROM firmas_usuario", String.class);
    mvc.perform(get("/api/v1/documentos/{id}/contenido", firma).session(master)).andExpect(status().isNotFound());
  }

  @Test
  void laCartaLibreConvierteElTextoEnParrafosYValidaLoObligatorio() throws Exception {
    mvc.perform(multipart(HttpMethod.PUT, "/api/v1/cartas/mi-firma").file(new MockMultipartFile("archivo", "f.png", "image/png", png(300, 300)))
        .param("cargo", "Jefe Técnico").session(master).with(csrf())).andExpect(status().isOk());
    String gerencia = jdbc.queryForObject("SELECT id::text FROM usuarios WHERE correo = 'gerencia@iqcperu.com'", String.class);

    enviar(post("/api/v1/cartas"), master, """
        {"tipo": "LIBRE", "clienteId": "%s", "firmanteId": "%s", "asunto": "Recordatorio"}""".formatted(cliente, gerencia), 422);
    // un certificado de otro cliente no se puede mencionar
    enviar(post("/api/v1/cartas/vista-previa"), master, """
        {"tipo": "CONTINUIDAD", "clienteId": "%s", "certificadoIds": ["%s"]}""".formatted(cliente, UUID.randomUUID()), 422);

    wordsConvertidos.clear();
    enviar(post("/api/v1/cartas"), master, """
        {"tipo": "LIBRE", "clienteId": "%s", "firmanteId": "%s", "asunto": "Programación de la auditoría",
         "cuerpo": "Primer párrafo de la carta.\\n\\nSegundo párrafo,\\ncon un salto de línea."}""".formatted(cliente, gerencia), 201);
    String texto = textoWord(wordsConvertidos.get(0));
    assertThat(texto).contains("Programación de la auditoría", "Primer párrafo de la carta.", "Segundo párrafo,", "con un salto de línea.",
        "Jefe Técnico").doesNotContain("{{");
  }

  private List<String> registrarCertificados() throws Exception {
    String r = enviar(post("/api/v1/certificados/registro-directo"), master, """
        {"clienteId": "%s", "esquema": "SAE", "alcance": "Diseño y construcción de obras civiles",
         "fechaOtorgamiento": "%s", "fechaEmision": "%s", "fechaExpiracion": "%s", "motivo": "Prueba",
         "certificados": [{"norma": "ISO_37001", "edicion": "2025", "codigo": "PE-SAE-03820260111A"},
                          {"norma": "ISO_9001", "edicion": "2015", "codigo": "PE-SAE-01020260112"}]}"""
        .formatted(cliente, HOY.minusMonths(1), HOY.minusMonths(1), HOY.minusMonths(1).plusYears(3)), 201);
    return List.of(JsonPath.read(r, "$.certificados[0].id"), JsonPath.read(r, "$.certificados[1].id"));
  }

  private static byte[] png(int ancho, int alto) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB), "png", out);
    return out.toByteArray();
  }

  private static String textoWord(byte[] docx) throws Exception {
    try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor e = new XWPFWordExtractor(d)) {
      return e.getText();
    }
  }

  private String enviar(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo, int estadoEsperado) throws Exception {
    return mvc.perform(solicitud.session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(estadoEsperado)).andReturn().getResponse().getContentAsString();
  }
}
