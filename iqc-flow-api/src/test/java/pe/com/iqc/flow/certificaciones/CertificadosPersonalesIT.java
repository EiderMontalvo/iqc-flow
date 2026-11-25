package pe.com.iqc.flow.certificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**espacio de Certificados: rol propio y certificados de personas*/
class CertificadosPersonalesIT extends PruebaIntegracion {
  private MockHttpSession certificados;
  private MockHttpSession comercial;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("certificados@iqcperu.com", false, CodigosRol.CERTIFICADOS);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    certificados = iniciarSesion("certificados@iqcperu.com");
    comercial = iniciarSesion("comercial@iqcperu.com");
  }

  @Test
  void elRolCertificadosVeCertificadosPeroNoExpedientesNiSeguimientos() throws Exception {
    mvc.perform(get("/api/v1/certificados").session(certificados)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/certificados/resumen").session(certificados))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].codigoPais", hasItem("PE")))
        .andExpect(jsonPath("$[*].codigoPais", hasItem("EC")));
    mvc.perform(get("/api/v1/certificados-personales").session(certificados)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/publicacion").session(certificados)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/clientes").session(certificados)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/expedientes").session(certificados)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/seguimientos/tareas").session(certificados)).andExpect(status().isForbidden());
    // Un comercial lee, pero no registra
    mvc.perform(get("/api/v1/certificados-personales").session(comercial)).andExpect(status().isOk());
    enviar(post("/api/v1/certificados-personales"), comercial, registro("IQC/PE/ABM9001", "PE", "DNI", "12345678"), 403);
  }

  @Test
  void registraBuscaCorrigeYAnulaUnCertificadoPersonal() throws Exception {
    String creado = enviar(post("/api/v1/certificados-personales"), certificados,
        registro("iqc/pe/abm9002", "PE", "DNI", "45678912"), 201);
    String id = JsonPath.read(creado, "$.id");
    assertThat((String) JsonPath.read(creado, "$.codigo")).isEqualTo("IQC/PE/ABM9002");
    assertThat((String) JsonPath.read(creado, "$.estado")).isEqualTo("EMITIDO");
    assertThat((String) JsonPath.read(creado, "$.urlConsulta")).startsWith("https://consultas.iqcperu.com/personal-pdf/");

    // Mismo código: conflicto. Misma persona: se reutiliza
    enviar(post("/api/v1/certificados-personales"), certificados, registro("IQC/PE/ABM9002", "PE", "DNI", "45678912"), 409);
    enviar(post("/api/v1/certificados-personales"), certificados, registro("IQC/PE/ABM9003", "PE", "DNI", "45678912"), 201);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM personas_certificadas", Integer.class)).isEqualTo(1);

    // Búsqueda por documento, por nombre sin tildes y por código
    mvc.perform(get("/api/v1/certificados-personales").param("texto", "45678912").session(certificados))
        .andExpect(jsonPath("$.totalElementos").value(2));
    mvc.perform(get("/api/v1/certificados-personales").param("texto", "jose perez").session(certificados))
        .andExpect(jsonPath("$.totalElementos").value(2));
    mvc.perform(get("/api/v1/certificados-personales").param("texto", "abm9003").session(certificados))
        .andExpect(jsonPath("$.totalElementos").value(1));

    // Corrección con motivo y anulación
    String corregido = enviar(put("/api/v1/certificados-personales/" + id), certificados, """
        {"datos": {"curso": "Auditor interno ISO 37001", "normaReferencia": "ISO 37001", "normaAnio": "2016",
         "duracionHoras": 24, "modalidad": "Virtual", "fechaEmision": "2026-11-10"},
         "motivo": "Corrección del número de horas", "version": 0}""", 200);
    assertThat(((Number) JsonPath.read(corregido, "$.duracionHoras")).doubleValue()).isEqualTo(24.0);
    enviar(post("/api/v1/certificados-personales/" + id + "/anulacion"), certificados,
        "{\"motivo\": \"Emitido por error\", \"version\": 1}", 200);
    mvc.perform(get("/api/v1/certificados-personales/resumen").session(certificados))
        .andExpect(jsonPath("$[0].codigoPais").value("PE"))
        .andExpect(jsonPath("$[0].emitidos").value(1))
        .andExpect(jsonPath("$[0].anulados").value(1));
    // Un anulado ya no se corrige
    enviar(post("/api/v1/certificados-personales/" + id + "/anulacion"), certificados,
        "{\"motivo\": \"Otra vez\", \"version\": 2}", 422);
  }

  @Test
  void elDocumentoSeValidaSegunElPais() throws Exception {
    mvc.perform(post("/api/v1/certificados-personales").session(certificados).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(registro("IQC/PE/ABM9004", "PE", "DNI", "1234")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DOCUMENTO_INVALIDO"));
    enviar(post("/api/v1/certificados-personales"), certificados, registro("IQC/EC/ABM9005", "EC", "CEDULA", "0912345678"), 201);
    mvc.perform(get("/api/v1/certificados-personales").param("pais", "EC").session(certificados))
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].urlConsulta", org.hamcrest.Matchers.startsWith("https://consultas.iqc-latam.com/")));
    mvc.perform(get("/api/v1/certificados-personales/tipos-documento").session(certificados))
        .andExpect(jsonPath("$.PE[0].codigo").value("DNI"));
  }

  private static String registro(String codigo, String pais, String tipo, String numero) {
    return """
        {"persona": {"codigoPais": "%s", "tipoDocumento": "%s", "numeroDocumento": "%s", "nombres": "José Pérez Quispe"},
         "codigo": "%s",
         "datos": {"curso": "Auditor interno ISO 37001", "normaReferencia": "ISO 37001", "normaAnio": "2016",
           "duracionHoras": 16, "modalidad": "Virtual", "fechaInicio": "2026-11-01", "fechaFin": "2026-11-03",
           "fechaEmision": "2026-11-10", "entrenador": "Ana Torres"}}""".formatted(pais, tipo, numero, codigo);
  }

  private String enviar(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo, int esperado)
      throws Exception {
    return mvc.perform(solicitud.session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(esperado))
        .andReturn().getResponse().getContentAsString();
  }
}
