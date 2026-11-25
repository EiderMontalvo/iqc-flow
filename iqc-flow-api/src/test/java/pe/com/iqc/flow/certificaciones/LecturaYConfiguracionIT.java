package pe.com.iqc.flow.certificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**configuración global de MASTER, herramientas por rol y lectura de PDF (modo automatizado)*/
class LecturaYConfiguracionIT extends PruebaIntegracion {
  private MockHttpSession master;
  private MockHttpSession administracion;
  private MockHttpSession comercial;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("master@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("administracion@iqcperu.com", false, CodigosRol.ADMINISTRACION_LOGISTICA);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    master = iniciarSesion("master@iqcperu.com");
    administracion = iniciarSesion("administracion@iqcperu.com");
    comercial = iniciarSesion("comercial@iqcperu.com");
  }

  /**los permisos de los roles del sistema no se borran entre pruebas: se restablece lo que cambian los parámetros*/
  @AfterEach
  void restablecer() {
    jdbc.update("""
        INSERT INTO roles_permisos (rol_id, permiso_codigo)
        SELECT r.id, p FROM roles r, unnest(ARRAY['certificados:autocompletar', 'certificados:generar-pdf']) p
        WHERE r.codigo = 'ADMINISTRACION_LOGISTICA' AND r.organizacion_id IS NULL
        ON CONFLICT DO NOTHING""");
  }

  @Test
  void masterTieneTodosLosPermisos() {
    List<String> faltan = jdbc.queryForList("""
        SELECT p.codigo FROM permisos p WHERE NOT EXISTS (SELECT 1 FROM roles_permisos rp JOIN roles r ON r.id = rp.rol_id
          WHERE r.codigo = 'MASTER' AND rp.permiso_codigo = p.codigo)""", String.class);
    assertThat(faltan).isEmpty();
  }

  @Test
  void masterApagaElAutocompletadoDeAdministracionYElEfectoEsInmediato() throws Exception {
    mvc.perform(get("/api/v1/certificados/herramientas").session(administracion))
        .andExpect(jsonPath("$.autocompletado").value(true))
        .andExpect(jsonPath("$.modosPersonal", contains("MANUAL", "AUTOMATIZADO", "PLANTILLA")));
    // Solo MASTER configura
    mvc.perform(put("/api/v1/organizacion/parametros/ADMINISTRACION_AUTOCOMPLETADO").session(administracion).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"NO\"}")).andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/organizacion/parametros/ADMINISTRACION_AUTOCOMPLETADO").session(master).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"NO\"}")).andExpect(status().isOk());
    // Misma sesión: los permisos se recalculan en cada solicitud
    mvc.perform(get("/api/v1/certificados/herramientas").session(administracion))
        .andExpect(jsonPath("$.autocompletado").value(false))
        .andExpect(jsonPath("$.generacionPdf").value(true))
        .andExpect(jsonPath("$.modosPersonal", contains("MANUAL", "PLANTILLA")));
    String pdf = subir(pdfPersonal());
    enviarLectura(administracion, "PERSONAL", pdf).andExpect(status().isForbidden());
    // MASTER no se ve afectado
    enviarLectura(master, "PERSONAL", pdf).andExpect(status().isOk());
  }

  @Test
  void masterApagaUnModoAUnaPersonaSinAfectarAlResto() throws Exception {
    String idAdministracion = jdbc.queryForObject("SELECT id::text FROM usuarios WHERE correo = 'administracion@iqcperu.com'", String.class);
    String ruta = "/api/v1/organizacion/parametros/CERT_PERSONAL_MODO_AUTOMATIZADO/personas/" + idAdministracion;
    // Solo MASTER
    mvc.perform(put(ruta).session(comercial).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"habilitado\": false}")).andExpect(status().isForbidden());
    mvc.perform(put(ruta).session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"habilitado\": false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valor").value("SI"))
        .andExpect(jsonPath("$.porPersona").value(true))
        .andExpect(jsonPath("$.desactivadoPara", contains(idAdministracion)));
    // Para esa persona desaparece el modo y la lectura se rechaza; MASTER sigue con los tres
    mvc.perform(get("/api/v1/certificados/herramientas").session(administracion))
        .andExpect(jsonPath("$.modosPersonal", contains("MANUAL", "PLANTILLA")))
        .andExpect(jsonPath("$.modosEmpresarial", contains("MANUAL", "AUTOMATIZADO", "PLANTILLA")));
    mvc.perform(get("/api/v1/certificados/herramientas").session(master))
        .andExpect(jsonPath("$.modosPersonal", contains("MANUAL", "AUTOMATIZADO", "PLANTILLA")));
    String pdf = subir(pdfPersonal());
    enviarLectura(administracion, "PERSONAL", pdf).andExpect(jsonPath("$.codigo").value("MODO_DESHABILITADO"));
    enviarLectura(master, "PERSONAL", pdf).andExpect(status().isOk());
    assertThat(contarBitacora("PARAMETRO_PERSONA_CAMBIADO", "EXITO")).isEqualTo(1);
    // Se devuelve
    mvc.perform(put(ruta).session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"habilitado\": true}")).andExpect(jsonPath("$.desactivadoPara").isEmpty());
    mvc.perform(get("/api/v1/certificados/herramientas").session(administracion))
        .andExpect(jsonPath("$.modosPersonal", contains("MANUAL", "AUTOMATIZADO", "PLANTILLA")));
    // Las opciones de rol y la política de firma no se apagan por persona; tampoco a alguien de otra organización
    mvc.perform(put("/api/v1/organizacion/parametros/ADMINISTRACION_AUTOCOMPLETADO/personas/" + idAdministracion)
        .session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"habilitado\": false}"))
        .andExpect(jsonPath("$.codigo").value("PARAMETRO_NO_POR_PERSONA"));
    mvc.perform(put("/api/v1/organizacion/parametros/IA_HABILITADA/personas/" + java.util.UUID.randomUUID())
        .session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"habilitado\": false}"))
        .andExpect(jsonPath("$.codigo").value("USUARIO_NO_ENCONTRADO"));
  }

  @Test
  void leeUnPdfPersonalYProponeLosCampos() throws Exception {
    String pdf = subir(pdfPersonal());
    enviarLectura(administracion, "PERSONAL", pdf)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formato").value("PERSONAL"))
        .andExpect(jsonPath("$.origen").value("LOCAL"))
        .andExpect(jsonPath("$.personal.codigo").value("IQC/PE/ABM0001"))
        .andExpect(jsonPath("$.personal.numeroDocumento").value("12345678"))
        .andExpect(jsonPath("$.personal.nombres").value("ANA PRUEBA FICTICIA"))
        .andExpect(jsonPath("$.personal.fechaEmision").value("2026-07-19"));
    // Un PDF que no es certificado: aviso de IA apagada
    String otro = subir(pdf("Factura electronica 0001-313", "Total S/ 3540.00"));
    enviarLectura(administracion, "EMPRESARIAL", otro)
        .andExpect(jsonPath("$.formato").value("DESCONOCIDO"))
        .andExpect(jsonPath("$.advertencias", hasItems("La IA está apagada: completa los datos a mano.")));
  }

  @Test
  void conElModoAutomatizadoDesactivadoNoSeLee() throws Exception {
    mvc.perform(put("/api/v1/organizacion/parametros/CERT_PERSONAL_MODO_AUTOMATIZADO").session(master).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"NO\"}")).andExpect(status().isOk());
    enviarLectura(master, "PERSONAL", subir(pdfPersonal()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("MODO_DESHABILITADO"));
    mvc.perform(get("/api/v1/certificados/herramientas").session(master))
        .andExpect(jsonPath("$.modosPersonal", contains("MANUAL", "PLANTILLA")))
        .andExpect(jsonPath("$.modosEmpresarial", contains("MANUAL", "AUTOMATIZADO", "PLANTILLA")));
    // Un comercial no registra certificados: solo el modo manual figura, sin herramientas
    mvc.perform(get("/api/v1/certificados/herramientas").session(comercial))
        .andExpect(jsonPath("$.autocompletado").value(false))
        .andExpect(jsonPath("$.generacionPdf").value(false));
  }

  private org.springframework.test.web.servlet.ResultActions enviarLectura(MockHttpSession sesion, String tipo, String documento)
      throws Exception {
    return mvc.perform(post("/api/v1/certificados/lectura").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"tipo\": \"%s\", \"documentoId\": \"%s\"}".formatted(tipo, documento)));
  }

  private String subir(byte[] contenido) throws Exception {
    String r = mvc.perform(multipart("/api/v1/documentos").file(new MockMultipartFile("archivo", "c.pdf", "application/pdf", contenido))
            .param("categoria", "CERTIFICADO_PDF").session(master).with(csrf()))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JsonPath.read(r, "$.id");
  }

  private static byte[] pdfPersonal() throws Exception {
    return pdf("CERTIFICADO", "CURSO DE AUDITOR INTERNO", "Por la presente se certifica que", "ANA PRUEBA FICTICIA",
        "DNI: 12345678", "ISO 37001:2016", "Sistema de Gestion Antisoborno", "Curso de interpretacion",
        "16 horas Virtual 05 al 07 de julio de 2026", "Entrenador: Juan Ejemplo", "Fecha de emision: 19 de julio de 2026",
        "Codigo de verificacion: IQC/PE/ABM0001");
  }

  /**PDF de una página con una línea por texto (fuente estándar, sin tildes)*/
  private static byte[] pdf(String... lineas) throws Exception {
    try (PDDocument d = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
      PDPage pagina = new PDPage();
      d.addPage(pagina);
      try (PDPageContentStream c = new PDPageContentStream(d, pagina)) {
        c.beginText();
        c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        c.newLineAtOffset(50, 740);
        for (String l : lineas) {
          c.showText(l);
          c.newLineAtOffset(0, -18);
        }
        c.endText();
      }
      d.save(salida);
      return salida.toByteArray();
    }
  }
}
