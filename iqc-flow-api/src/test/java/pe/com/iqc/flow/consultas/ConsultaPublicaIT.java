package pe.com.iqc.flow.consultas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.certificaciones.application.PublicadorCertificados;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**portal público de consulta (8b): sin sesión, lee solo las bases públicas de Perú y LATAM*/
class ConsultaPublicaIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));
  private static final byte[] PDF = "%PDF-1.4\n% personal de prueba\n%%EOF".getBytes(StandardCharsets.US_ASCII);

  @Autowired PublicadorCertificados publicador;
  private MockHttpSession master;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    master = iniciarSesion("gerencia@iqcperu.com");
  }

  @Test
  void verificaUnEmpresarialSoloEnElPortalDeSuMarcaYReenviaSuQrAntiguo() throws Exception {
    String cliente = JsonPath.read(enviar(post("/api/v1/clientes"), """
        {"razonSocial": "Pesquera Ficticia S.A.", "codigoPais": "EC", "tipoDocumento": "RUC", "numeroDocumento": "0991234567001"}""", 201), "$.id");
    String certificado = JsonPath.read(enviar(post("/api/v1/certificados/registro-directo"), """
        {"clienteId": "%s", "esquema": "SAE", "alcance": "Pesca", "fechaOtorgamiento": "%s", "fechaEmision": "%s",
         "fechaExpiracion": "%s", "motivo": "Prueba", "certificados": [{"norma": "ISO_9001", "edicion": "2015", "codigo": "PE-SAE-00920240211"}]}"""
        .formatted(cliente, HOY.minusYears(1), HOY.minusYears(1), HOY.plusYears(2)), 201), "$.certificados[0].id");
    jdbc.update("INSERT INTO qr_legado (encriptacion_id, id_qr, organizacion_id, certificado_id, activo, registrado_en) "
        + "VALUES ('abc-legado-1', 'abc-legado', ?, ?::uuid, TRUE, now())", organizacionId, certificado);
    publicador.publicar(false);

    mvc.perform(get("/api/v1/publico/consultas/LATAM/verificacion").param("documento", "0991234567001").param("codigo", "pe-sae-00920240211"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tipo").value("EMPRESA"))
        .andExpect(jsonPath("$.empresarial.titular").value("Pesquera Ficticia S.A."))
        .andExpect(jsonPath("$.empresarial.situacion").value("VIGENTE"))
        .andExpect(jsonPath("$.empresarial.marca").value("IQC Ecuador"));
    // Con el documento equivocado no se revela nada
    mvc.perform(get("/api/v1/publico/consultas/LATAM/verificacion").param("documento", "20100000001").param("codigo", "PE-SAE-00920240211"))
        .andExpect(status().isNotFound());
    // El portal de Perú no tiene los certificados de Ecuador
    mvc.perform(get("/api/v1/publico/consultas/PE/verificacion").param("documento", "0991234567001").param("codigo", "PE-SAE-00920240211"))
        .andExpect(status().isNotFound());
    // El QR antiguo, impreso con la dirección de Perú, se reenvía al portal LATAM
    mvc.perform(get("/api/v1/publico/consultas/PE/qr/abc-legado-1"))
        .andExpect(jsonPath("$.redireccion").value("https://consultas.iqc-latam.com/abc-legado-1"));
    mvc.perform(get("/api/v1/publico/consultas/LATAM/qr/abc-legado-1"))
        .andExpect(jsonPath("$.tipo").value("EMPRESA"))
        .andExpect(jsonPath("$.empresarial.codigo").value("PE-SAE-00920240211"));
  }

  @Test
  void verificaUnPersonalYEntregaSuPdfHastaQueSeAnula() throws Exception {
    String documento = JsonPath.read(mvc.perform(multipart("/api/v1/documentos")
            .file(new MockMultipartFile("archivo", "p.pdf", "application/pdf", PDF)).param("categoria", "CERTIFICADO_PDF")
            .session(master).with(csrf())).andReturn().getResponse().getContentAsString(), "$.id");
    String creado = enviar(post("/api/v1/certificados-personales"), """
        {"persona": {"codigoPais": "PE", "tipoDocumento": "DNI", "numeroDocumento": "45678912", "nombres": "Ana Prueba"},
         "codigo": "IQC/PE/ABM9100", "documentoPdfId": "%s",
         "datos": {"curso": "Auditor interno", "normaReferencia": "ISO 37001", "normaAnio": "2016", "fechaEmision": "2026-11-10"}}"""
        .formatted(documento), 201);
    String id = JsonPath.read(creado, "$.id");
    String token = ((String) JsonPath.read(creado, "$.urlConsulta")).replaceAll(".*/personal-pdf/", "");
    publicador.publicar(false);

    mvc.perform(get("/api/v1/publico/consultas/PE/verificacion").param("documento", "45678912").param("codigo", "IQC/PE/ABM9100"))
        .andExpect(jsonPath("$.tipo").value("PERSONA"))
        .andExpect(jsonPath("$.personal.nombres").value("Ana Prueba"))
        .andExpect(jsonPath("$.personal.norma").value("ISO 37001:2016"))
        .andExpect(jsonPath("$.personal.estado").value("EMITIDO"));
    byte[] pdf = mvc.perform(get("/api/v1/publico/consultas/PE/personal-pdf/" + token))
        .andExpect(status().isOk())
        // El portal de consultas (otro dominio) puede mostrar el PDF embebido; el resto de la API no se enmarca
        .andExpect(header().doesNotExist("X-Frame-Options"))
        .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.startsWith("frame-ancestors 'self'")))
        .andReturn().getResponse().getContentAsByteArray();
    mvc.perform(get("/api/v1/publico/consultas/PE/verificacion").param("documento", "45678912").param("codigo", "IQC/PE/ABM9100"))
        .andExpect(header().string("X-Frame-Options", "DENY"));
    assertThat(pdf).isEqualTo(PDF);

    enviar(post("/api/v1/certificados-personales/" + id + "/anulacion"), "{\"motivo\": \"Emitido por error\", \"version\": 0}", 200);
    publicador.publicar(false);
    mvc.perform(get("/api/v1/publico/consultas/PE/verificacion").param("documento", "45678912").param("codigo", "IQC/PE/ABM9100"))
        .andExpect(jsonPath("$.personal.estado").value("ANULADO"));
    mvc.perform(get("/api/v1/publico/consultas/PE/personal-pdf/" + token)).andExpect(status().isNotFound());
  }

  @Test
  void rechazaDatosIncompletosYPortalesInexistentes() throws Exception {
    mvc.perform(get("/api/v1/publico/consultas/PE/verificacion").param("documento", "12").param("codigo", "X"))
        .andExpect(status().isUnprocessableContent());
    mvc.perform(get("/api/v1/publico/consultas/MARTE/qr/abc")).andExpect(status().isNotFound());
  }

  private String enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud, String cuerpo, int esperado)
      throws Exception {
    return mvc.perform(solicitud.session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
  }
}
