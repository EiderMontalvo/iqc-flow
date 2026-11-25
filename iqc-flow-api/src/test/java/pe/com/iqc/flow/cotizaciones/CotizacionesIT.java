package pe.com.iqc.flow.cotizaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.cotizaciones.infrastructure.DetectorCotizacionesSinRespuesta;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**cotización: borrador con número correlativo e IGV, envío, alerta sin respuesta, respuesta del cliente y permisos*/
class CotizacionesIT extends PruebaIntegracion {
  @Autowired private DetectorCotizacionesSinRespuesta detector;
  private MockHttpSession comercial;
  private MockHttpSession operaciones;
  private String clienteId;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    crearUsuario("operaciones@iqcperu.com", false, CodigosRol.COORDINADOR_AUDITORIAS);
    comercial = iniciarSesion("comercial@iqcperu.com");
    operaciones = iniciarSesion("operaciones@iqcperu.com");
    clienteId = JsonPath.read(enviar(post("/api/v1/clientes"), comercial,
        "{\"razonSocial\": \"Constructora Andina SAC\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"20131312955\"}", 201), "$.id");
  }

  private String cuerpo(String monto1, Long version) {
    return """
        {"clienteId": "%s", "tipoServicio": "CERTIFICACION_INICIAL", "alcance": "Diseño y construcción de obras civiles",
         "numeroSedes": 2, "tarifaDia": 1200, "validezDias": 30, "condicionesPago": "50 %% al firmar, 50 %% antes de la etapa 2",
         "items": [
           {"normaCodigo": "ISO_9001", "edicion": "2015", "descripcion": "Certificación ISO 9001:2015", "diasAuditor": 4.5, "monto": %s},
           {"normaCodigo": "ISO_14001", "edicion": "2015", "descripcion": "Certificación ISO 14001:2015", "diasAuditor": 3, "monto": 3600}
         ]%s}""".formatted(clienteId, monto1, version == null ? "" : ", \"version\": " + version);
  }

  @Test
  void flujoCompletoDeUnaCotizacion() throws Exception {
    // Borrador numerado, con el IGV de Perú (18 %) y los días auditor sumados
    String creada = enviar(post("/api/v1/cotizaciones"), comercial, cuerpo("5400", null), 201);
    String id = JsonPath.read(creada, "$.id");
    int anio = LocalDate.now().getYear();
    assertThat((String) JsonPath.read(creada, "$.numero")).isEqualTo("COT-" + anio + "-0001");
    assertThat(JsonPath.<Double>read(creada, "$.subtotal")).isEqualTo(9000.0);
    assertThat(JsonPath.<Double>read(creada, "$.impuesto")).isEqualTo(1620.0);
    assertThat(JsonPath.<Double>read(creada, "$.total")).isEqualTo(10620.0);
    assertThat(JsonPath.<Double>read(creada, "$.diasAuditorTotal")).isEqualTo(7.5);
    assertThat((String) JsonPath.read(creada, "$.moneda")).isEqualTo("PEN");

    // La segunda lleva el número siguiente
    String otra = enviar(post("/api/v1/cotizaciones"), comercial, cuerpo("1000", null), 201);
    assertThat((String) JsonPath.read(otra, "$.numero")).isEqualTo("COT-" + anio + "-0002");

    // Se edita en borrador; Operaciones no puede crear ni editar
    String editada = enviar(put("/api/v1/cotizaciones/" + id), comercial, cuerpo("6000", 0L), 200);
    assertThat(JsonPath.<Double>read(editada, "$.total")).isEqualTo(11328.0);
    enviar(post("/api/v1/cotizaciones"), operaciones, cuerpo("1", null), 403);

    // Enviada hace 10 días: no se edita y el detector crea una sola tarea de seguimiento
    String hace10 = LocalDate.now().minusDays(10).toString();
    enviar(post("/api/v1/cotizaciones/" + id + "/envio"), comercial,
        "{\"fecha\": \"" + hace10 + "\", \"destinatarios\": \"gerencia@andina.pe\", \"version\": 1}", 200);
    enviar(put("/api/v1/cotizaciones/" + id), comercial, cuerpo("7000", 2L), 422);
    assertThat(detector.revisar()).isEqualTo(1);
    assertThat(detector.revisar()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM tareas_seguimiento WHERE sujeto_tipo = 'COTIZACION' AND estado = 'PENDIENTE'", Long.class)).isEqualTo(1);
    mvc.perform(get("/api/v1/cotizaciones/resumen").session(comercial))
        .andExpect(jsonPath("$.enviadas").value(1)).andExpect(jsonPath("$.enviadasSinRespuesta").value(1)).andExpect(jsonPath("$.borradores").value(1));

    // Rechazo sin motivo: no; aceptación: cierra la tarea automática y cuenta en la conversión
    enviar(post("/api/v1/cotizaciones/" + id + "/respuesta"), comercial,
        "{\"aceptada\": false, \"fecha\": \"" + LocalDate.now() + "\", \"version\": 3}", 422);
    enviar(post("/api/v1/cotizaciones/" + id + "/respuesta"), comercial,
        "{\"aceptada\": true, \"fecha\": \"" + LocalDate.now() + "\", \"version\": 3}", 200);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM tareas_seguimiento WHERE sujeto_tipo = 'COTIZACION' AND estado = 'CANCELADA'", Long.class)).isEqualTo(1);
    mvc.perform(get("/api/v1/cotizaciones/resumen").session(operaciones)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/cotizaciones/resumen").session(comercial))
        .andExpect(jsonPath("$.aceptadasMes").value(1)).andExpect(jsonPath("$.conversion12Meses").value(100));
    // Búsqueda por razón social y por número
    mvc.perform(get("/api/v1/cotizaciones").param("texto", "andina").session(comercial)).andExpect(jsonPath("$.totalElementos").value(2));
    mvc.perform(get("/api/v1/cotizaciones").param("texto", "0002").session(comercial)).andExpect(jsonPath("$.totalElementos").value(1));
    assertThat(contarBitacora("COTIZACION_ACEPTADA", "EXITO")).isEqualTo(1);
  }

  private String enviar(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo, int esperado) throws Exception {
    return mvc.perform(solicitud.session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
  }
}
