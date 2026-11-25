package pe.com.iqc.flow.indicadores;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

/**indicadores para los gráficos del panel: series completas por mes y secciones según permisos*/
class IndicadoresIT extends PruebaIntegracion {
  private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));
  private MockHttpSession master;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    master = iniciarSesion("gerencia@iqcperu.com");
  }

  @Test
  void cuentaVencimientosEmisionesYCarteraEnSeriesDeDoceMeses() throws Exception {
    String cliente = JsonPath.read(enviar(post("/api/v1/clientes"), """
        {"razonSocial": "Constructora Ficticia S.A.C.", "codigoPais": "PE", "tipoDocumento": "RUC",
         "numeroDocumento": "20131312955", "estado": "ACTIVO"}""", 201), "$.id");
    // Emitido hace un mes y vence dentro de tres: suma una emisión y un vencimiento
    enviar(post("/api/v1/certificados/registro-directo"), """
        {"clienteId": "%s", "esquema": "SAE", "alcance": "Obras", "fechaOtorgamiento": "%s", "fechaEmision": "%s",
         "fechaExpiracion": "%s", "motivo": "Prueba", "certificados": [{"norma": "ISO_9001", "edicion": "2015", "codigo": "PE-SAE-09920260111"}]}"""
        .formatted(cliente, HOY.minusMonths(1), HOY.minusMonths(1), HOY.plusMonths(3)), 201);

    String mesVence = YearMonth.from(HOY.plusMonths(3)).toString(), mesEmite = YearMonth.from(HOY.minusMonths(1)).toString();
    mvc.perform(get("/api/v1/indicadores").session(master))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.certificados.vencimientosPorMes", hasSize(12)))
        .andExpect(jsonPath("$.certificados.vencimientosPorMes[0].clave").value(YearMonth.from(HOY).toString()))
        .andExpect(jsonPath("$.certificados.vencimientosPorMes[?(@.clave == '%s')].cantidad".formatted(mesVence)).value(1))
        .andExpect(jsonPath("$.certificados.emisionesPorMes", hasSize(12)))
        .andExpect(jsonPath("$.certificados.emisionesPorMes[11].clave").value(YearMonth.from(HOY).toString()))
        .andExpect(jsonPath("$.certificados.emisionesPorMes[?(@.clave == '%s')].cantidad".formatted(mesEmite)).value(1))
        .andExpect(jsonPath("$.certificados.vigentesPorNorma[0].clave").value("ISO_9001"))
        .andExpect(jsonPath("$.certificados.vigentesPorNorma[0].cantidad").value(1))
        .andExpect(jsonPath("$.certificados.vigentesPorEsquema[0].clave").value("SAE"))
        .andExpect(jsonPath("$.clientes.porEstado[0].clave").value("ACTIVO"))
        .andExpect(jsonPath("$.clientes.porEstado[0].cantidad").value(1))
        .andExpect(jsonPath("$.clientes.altasPorMes", hasSize(12)))
        .andExpect(jsonPath("$.clientes.altasPorMes[11].cantidad").value(1))
        .andExpect(jsonPath("$.seguimientos.deTodos").value(true))
        .andExpect(jsonPath("$.seguimientos.completadasPorSemana", hasSize(8)))
        .andExpect(jsonPath("$.seguimientos.abiertasPorPrioridad", hasSize(4)));
  }

  @Test
  void entregaSoloLasSeccionesQuePermiteElRol() throws Exception {
    // CERTIFICADOS lee certificados y clientes, pero no seguimientos
    crearUsuario("certificados@iqcperu.com", false, CodigosRol.CERTIFICADOS);
    mvc.perform(get("/api/v1/indicadores").session(iniciarSesion("certificados@iqcperu.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.certificados.vencimientosPorMes", hasSize(12)))
        .andExpect(jsonPath("$.clientes.porEstado", hasSize(3)))
        .andExpect(jsonPath("$.seguimientos").value(nullValue()));
    mvc.perform(get("/api/v1/indicadores")).andExpect(status().isUnauthorized());
  }

  private String enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud, String cuerpo, int esperado)
      throws Exception {
    return mvc.perform(solicitud.session(master).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
  }
}
