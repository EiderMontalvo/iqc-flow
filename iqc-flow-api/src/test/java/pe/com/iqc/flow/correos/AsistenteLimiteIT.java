package pe.com.iqc.flow.correos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**cada llamada al proveedor cuesta: una persona tiene un tope por minuto, y lo ya guardado (el resumen) no gasta de ese tope*/
@TestPropertySource(properties = {
    "iqc.correo.cuentas.administracion.usuario=administracion@iqcperu.net", "iqc.correo.cuentas.administracion.clave=clave-1",
    "iqc.correo.asistente.llamadas-por-minuto=2"})
@Import(AsistenteLimiteIT.Falsos.class)
class AsistenteLimiteIT extends PruebaIntegracion {
  @TestConfiguration
  static class Falsos {
    @Bean @Primary BuzonFalso buzonFalso() { return new BuzonFalso(); }
    @Bean @Primary AsistenteFalso asistenteFalso() { return new AsistenteFalso(); }
  }

  @Autowired BuzonFalso buzon;
  @Autowired AsistenteFalso asistente;

  @Test
  void despuesDelTopeSeRespondeIaLimiteYLoGuardadoNoCuenta() throws Exception {
    buzon.vaciar();
    asistente.restablecer();
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    MockHttpSession master = iniciarSesion("gerencia@iqcperu.com");
    buzon.agregar("administracion", CorreosDePrueba.correo("ana@mineraandina.pe", "Solicitud de cotización", "Deseamos certificar.", "<c1@mineraandina.pe>"));
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    jdbc.update("INSERT INTO parametros_organizacion (organizacion_id, clave, valor, actualizado_en) VALUES (?, 'IA_HABILITADA', 'SI', now())", organizacionId);
    List<String> ids = JsonPath.read(json(get("/api/v1/correos/bandeja"), master, null, 200), "$.contenido[*].id");
    String id = ids.get(0);

    json(post("/api/v1/correos/bandeja/" + id + "/resumen"), master, "", 200);       // 1 de 2
    json(post("/api/v1/correos/bandeja/" + id + "/resumen"), master, "", 200);       // sale de lo guardado: no cuenta
    json(post("/api/v1/correos/bandeja/" + id + "/borrador-respuesta"), master, "{}", 200); // 2 de 2
    String limite = json(post("/api/v1/correos/bandeja/" + id + "/borrador-respuesta"), master, "{}", 422);
    assertThat((String) JsonPath.read(limite, "$.codigo")).isEqualTo("IA_LIMITE");
    assertThat(asistente.llamadas).containsExactly("resumir", "redactar");
  }

  private String json(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo, int estadoEsperado) throws Exception {
    var s = solicitud.session(sesion).with(csrf());
    if (cuerpo != null) s = s.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    var respuesta = mvc.perform(s).andReturn().getResponse();
    assertThat(respuesta.getStatus()).as(respuesta.getContentAsString()).isEqualTo(estadoEsperado);
    return respuesta.getContentAsString();
  }
}
