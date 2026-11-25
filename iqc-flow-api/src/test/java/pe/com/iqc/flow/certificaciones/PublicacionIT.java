package pe.com.iqc.flow.certificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.certificaciones.application.PublicadorCertificados;
import pe.com.iqc.flow.certificaciones.application.PublicadorCertificados.ResultadoPublicacion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**marcas por país, QR y publicación a la base pública de cada país (esquemas consultas_pe, _ec... en pruebas)*/
class PublicacionIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));
  private static final byte[] PDF = "%PDF-1.4\n% certificado de prueba\n%%EOF".getBytes(StandardCharsets.US_ASCII);

  @Autowired PublicadorCertificados publicador;

  private MockHttpSession master;
  private MockHttpSession comercial;
  private String clienteEcuador;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    master = iniciarSesion("gerencia@iqcperu.com");
    comercial = iniciarSesion("comercial@iqcperu.com");
    clienteEcuador = JsonPath.read(enviar(post("/api/v1/clientes"), master, """
        {"razonSocial": "Pesquera del Pacífico S.A.", "codigoPais": "EC", "tipoDocumento": "RUC",
         "numeroDocumento": "0991234567001"}""", 201), "$.id");
  }

  @Test
  void lasMarcasPredeterminadasSeparanPeruDeLatamYSoloMasterLasCambia() throws Exception {
    mvc.perform(get("/api/v1/marcas").session(comercial))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].codigoPais").value("PE"))
        .andExpect(jsonPath("$[0].urlConsulta").value("https://consultas.iqcperu.com/"))
        .andExpect(jsonPath("$[?(@.codigoPais == 'EC')].nombre", contains("IQC Ecuador")))
        .andExpect(jsonPath("$[?(@.codigoPais == 'CO')].urlConsulta", contains("https://consultas.iqc-latam.com/")))
        .andExpect(jsonPath("$[?(@.codigoPais == 'PA')].predeterminada", contains(true)));

    String cuerpo = "{\"nombre\": \"IQC Colombia S.A.S.\", \"urlConsulta\": \"https://consultas.iqc-latam.com\", "
        + "\"correo\": \"colombia@iqc-latam.com\"}";
    enviar(put("/api/v1/marcas/CO"), comercial, cuerpo, 403);
    mvc.perform(put("/api/v1/marcas/CO").session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\": \"IQC Colombia\", \"urlConsulta\": \"http://consultas.iqc-latam.com/?x=1\"}"))
        .andExpect(jsonPath("$.codigo").value("URL_CONSULTA_INVALIDA"));
    String definida = enviar(put("/api/v1/marcas/CO"), master, cuerpo, 200);
    assertThat((String) JsonPath.read(definida, "$.urlConsulta")).isEqualTo("https://consultas.iqc-latam.com/");
    assertThat((Boolean) JsonPath.read(definida, "$.predeterminada")).isFalse();
    // Editar con una versión vieja no pisa el cambio de otra persona
    mvc.perform(put("/api/v1/marcas/CO").session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\": \"Otro\", \"urlConsulta\": \"https://consultas.iqc-latam.com/\"}"))
        .andExpect(jsonPath("$.codigo").value("CONFLICTO_VERSION"));
  }

  @Test
  void elQrApuntaALaConsultaDeSuMarcaYLlevaElLogo() throws Exception {
    String certificado = registrar(clienteEcuador, "PE-SAE-00520240211", null);
    String enlace = enviarGet("/api/v1/certificados/" + certificado + "/consulta");
    String token = JsonPath.read(enlace, "$.token");
    assertThat((String) JsonPath.read(enlace, "$.url")).isEqualTo("https://consultas.iqc-latam.com/" + token);
    assertThat((String) JsonPath.read(enlace, "$.marca")).isEqualTo("IQC Ecuador");

    assertThat(leerQr(qr(certificado))).isEqualTo("https://consultas.iqc-latam.com/" + token);

    // Con logo configurado sigue leyéndose
    String logo = subir("LOGO_MARCA", logoPng());
    enviar(put("/api/v1/marcas/EC"), master, "{\"nombre\": \"IQC Ecuador\", \"urlConsulta\": "
        + "\"https://consultas.iqc-latam.com/\", \"logoDocumentoId\": \"%s\"}".formatted(logo), 200);
    assertThat(leerQr(qr(certificado))).isEqualTo("https://consultas.iqc-latam.com/" + token);

    // Un QR ya impreso de la plataforma anterior se conserva
    jdbc.update("INSERT INTO qr_legado (encriptacion_id, id_qr, organizacion_id, certificado_id, activo, registrado_en) "
        + "VALUES ('3f1a-legado-x', '3f1a-legado', ?, ?::uuid, TRUE, now())", organizacionId, certificado);
    mvc.perform(get("/api/v1/certificados/{id}/consulta", certificado).session(master))
        .andExpect(jsonPath("$.url").value("https://consultas.iqc-latam.com/3f1a-legado-x"));
  }

  @Test
  void publicaSoloLoPublicoYVuelveAPublicarCuandoAlgoCambia() throws Exception {
    String pdf = subir("CERTIFICADO_PDF", PDF);
    String certificado = registrar(clienteEcuador, "PE-SAE-00620240211", pdf);
    String token = JsonPath.read(enviarGet("/api/v1/certificados/" + certificado + "/consulta"), "$.token");

    ResultadoPublicacion primera = publicador.publicar(false);
    assertThat(primera.publicados()).isEqualTo(1);
    Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM consultas_latam.certificados_publicos WHERE id = ?::uuid",
        certificado);
    assertThat(fila).containsEntry("marca_nombre", "IQC Ecuador")
        .containsEntry("url_consulta", "https://consultas.iqc-latam.com/")
        .containsEntry("codigo", "PE-SAE-00620240211")
        .containsEntry("norma_designacion", "ISO 9001:2015")
        .containsEntry("organismo_acreditador", "SAE - Servicio de Acreditación Ecuatoriano")
        .containsEntry("titular_razon_social", "Pesquera del Pacífico S.A.")
        .containsEntry("titular_numero_documento", "0991234567001")
        .containsEntry("estado", "EMITIDO")
        .containsEntry("tiene_pdf", true);
    assertThat(jdbc.queryForObject("SELECT origen FROM consultas_latam.qr_publicos WHERE token = ?", String.class, token))
        .isEqualTo("SISTEMA");
    assertThat(jdbc.queryForObject("SELECT contenido FROM consultas_latam.documentos_publicos WHERE certificado_id = ?::uuid",
        byte[].class, certificado)).isEqualTo(PDF);

    // Solo en la base de su país
    assertThat(contar("pe", certificado)).isZero();
    assertThat(contar("pe", certificado)).isZero();

    // Sin cambios no se vuelve a escribir
    assertThat(publicador.publicar(false).sinCambios()).isEqualTo(1);

    // Un cambio del cliente (dato que se muestra) se publica en la siguiente pasada
    String cliente = enviarGet("/api/v1/clientes/" + clienteEcuador);
    int version = JsonPath.read(cliente, "$.version");
    enviar(patch("/api/v1/clientes/" + clienteEcuador), master,
        "{\"razonSocial\": \"Pesquera del Pacífico Sur S.A.\", \"version\": %d}".formatted(version), 200);
    // Y el cambio de marca también
    enviar(put("/api/v1/certificados/" + certificado + "/marca"), master,
        "{\"pais\": \"PE\", \"motivo\": \"Lo atiende IQC Perú\", \"version\": 1}", 200);
    assertThat(publicador.publicar(false).publicados()).isEqualTo(1);
    // Pasa a la base de Perú y sale de la de Ecuador, con su QR y su PDF
    assertThat(contar("latam", certificado)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM consultas_latam.qr_publicos", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM consultas_latam.documentos_publicos", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT certificado_id::text FROM consultas_pe.qr_publicos WHERE token = ?",
        String.class, token)).isEqualTo(certificado);
    assertThat(jdbc.queryForMap("SELECT titular_razon_social, marca_nombre, url_consulta FROM consultas_pe.certificados_publicos"))
        .containsEntry("titular_razon_social", "Pesquera del Pacífico Sur S.A.")
        .containsEntry("marca_nombre", "IQC Perú")
        .containsEntry("url_consulta", "https://consultas.iqcperu.com/");

    // Nada interno llega a la base pública
    assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'consultas_pe' "
        + "AND table_name NOT LIKE 'flyway%' ORDER BY 1", String.class))
        .containsExactly("certificados_publicos", "documentos_personales_publicos", "documentos_publicos", "personales_publicos",
            "qr_publicos", "qr_redirecciones");

    mvc.perform(get("/api/v1/publicacion").session(master))
        .andExpect(jsonPath("$.habilitada").value(true))
        .andExpect(jsonPath("$.paises", contains("CO", "EC", "PA", "PE")))
        .andExpect(jsonPath("$.publicados").value(1));
    enviar(post("/api/v1/publicacion"), comercial, "", 403);
    mvc.perform(post("/api/v1/publicacion").param("forzar", "true").session(master).with(csrf()))
        .andExpect(jsonPath("$.publicados").value(1));
  }

  @Test
  void elQrImpresoConLaConsultaDePeruSeReenviaAlPortalLatam() throws Exception {
    String certificado = registrar(clienteEcuador, "PE-SAE-00720240211", null);
    // El panel anterior imprimió consultas.iqcperu.com también en los QR de Ecuador
    jdbc.update("INSERT INTO qr_legado (encriptacion_id, id_qr, organizacion_id, certificado_id, activo, registrado_en) "
        + "VALUES ('9b2c-legado-q', '9b2c-legado', ?, ?::uuid, TRUE, now())", organizacionId, certificado);

    publicador.publicar(false);
    assertThat(contar("latam", certificado)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT origen FROM consultas_latam.qr_publicos WHERE token = '9b2c-legado-q'",
        String.class)).isEqualTo("LEGADO");
    // La base de Perú no tiene el certificado, solo a dónde reenviar su QR impreso
    assertThat(contar("pe", certificado)).isZero();
    assertThat(jdbc.queryForMap("SELECT marca_pais, url_destino FROM consultas_pe.qr_redirecciones WHERE token = ?",
        "9b2c-legado-q"))
        .containsEntry("marca_pais", "EC")
        .containsEntry("url_destino", "https://consultas.iqc-latam.com/9b2c-legado-q");

    // Si pasa a la marca de Perú, el portal de Perú lo resuelve directamente y la redirección sobra
    int version = JsonPath.read(enviarGet("/api/v1/certificados/" + certificado), "$.version");
    enviar(put("/api/v1/certificados/" + certificado + "/marca"), master,
        "{\"pais\": \"PE\", \"motivo\": \"Lo atiende IQC Perú\", \"version\": %d}".formatted(version), 200);
    publicador.publicar(false);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM consultas_pe.qr_redirecciones", Integer.class)).isZero();
    assertThat(contar("pe", certificado)).isEqualTo(1);
    assertThat(contar("latam", certificado)).isZero();
  }

  @Test
  void cambiarDeMarcaEntrePaisesDeLatamNoLoRetiraDeLaBaseCompartida() throws Exception {
    String certificado = registrar(clienteEcuador, "PE-SAE-00820240211", null);
    publicador.publicar(false);
    assertThat(contar("latam", certificado)).isEqualTo(1);
    int version = JsonPath.read(enviarGet("/api/v1/certificados/" + certificado), "$.version");
    enviar(put("/api/v1/certificados/" + certificado + "/marca"), master,
        "{\"pais\": \"CO\", \"motivo\": \"Lo atiende IQC Colombia\", \"version\": %d}".formatted(version), 200);
    publicador.publicar(false);
    assertThat(contar("latam", certificado)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT marca_pais FROM consultas_latam.certificados_publicos WHERE id = ?::uuid",
        String.class, certificado)).isEqualTo("CO");
    assertThat(jdbc.queryForObject("SELECT pais_publicado FROM publicaciones_certificados WHERE certificado_id = ?::uuid",
        String.class, certificado)).isEqualTo("CO");
  }

  @Test
  void unBorradorNoSePublicaNiTieneQr() throws Exception {
    UUID borrador = UUID.randomUUID();
    mvc.perform(get("/api/v1/certificados/{id}/qr", borrador).session(master))
        .andExpect(jsonPath("$.codigo").value("CERTIFICADO_NO_EMITIDO"));
    assertThat(publicador.publicar(false).revisados()).isZero();
  }

  private String registrar(String cliente, String codigo, String pdf) throws Exception {
    String cuerpo = """
        {"clienteId": "%s", "esquema": "SAE", "alcance": "Procesamiento de pescado", "fechaOtorgamiento": "%s",
         "fechaEmision": "%s", "fechaExpiracion": "%s", "motivo": "Emitido antes del sistema",
         "certificados": [{"norma": "ISO_9001", "edicion": "2015", "codigo": "%s"%s}]}"""
        .formatted(cliente, HOY.minusYears(1), HOY.minusYears(1), HOY.plusYears(2), codigo,
            pdf == null ? "" : ", \"documentoPdfId\": \"" + pdf + "\"");
    return JsonPath.read(enviar(post("/api/v1/certificados/registro-directo"), master, cuerpo, 201), "$.certificados[0].id");
  }

  private int contar(String pais, String certificado) {
    return jdbc.queryForObject("SELECT count(*) FROM consultas_" + pais + ".certificados_publicos WHERE id = ?::uuid",
        Integer.class, certificado);
  }

  private byte[] qr(String certificado) throws Exception {
    return mvc.perform(get("/api/v1/certificados/{id}/qr", certificado).param("tamano", "400").session(master))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();
  }

  private static String leerQr(byte[] png) throws Exception {
    BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
    return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen))))
        .getText();
  }

  private static byte[] logoPng() throws Exception {
    BufferedImage logo = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = logo.createGraphics();
    g.setColor(new Color(0x0F7A6C));
    g.fillRect(0, 0, 120, 60);
    g.dispose();
    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    ImageIO.write(logo, "png", salida);
    return salida.toByteArray();
  }

  private String subir(String categoria, byte[] contenido) throws Exception {
    String respuesta = mvc.perform(multipart("/api/v1/documentos")
            .file(new MockMultipartFile("archivo", "archivo", "application/octet-stream", contenido))
            .param("categoria", categoria).session(master).with(csrf()))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(respuesta, "$.id");
  }

  private String enviarGet(String ruta) throws Exception {
    return mvc.perform(get(ruta).session(master)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  private String enviar(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo,
      int estadoEsperado) throws Exception {
    return mvc.perform(solicitud.session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(estadoEsperado))
        .andReturn().getResponse().getContentAsString();
  }
}
