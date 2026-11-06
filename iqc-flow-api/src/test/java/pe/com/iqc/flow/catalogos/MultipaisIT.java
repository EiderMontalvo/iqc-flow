package pe.com.iqc.flow.catalogos;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

class MultipaisIT extends PruebaIntegracion {
  @Autowired private ServicioCatalogos catalogos;

  private MockHttpSession sesion;

  @BeforeEach
  void iniciar() throws Exception {
    crearUsuario("luis@iqcperu.com", false, CodigosRol.COMERCIAL);
    sesion = iniciarSesion("luis@iqcperu.com");
  }

  @Test
  void losCatalogosRequierenSesion() throws Exception {
    mvc.perform(get("/api/v1/catalogos/paises")).andExpect(status().isUnauthorized());
  }

  @Test
  void cadaPaisTieneSuMonedaImpuestoYNivelesDeDireccion() throws Exception {
    mvc.perform(get("/api/v1/catalogos/paises").session(sesion))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[?(@.codigo == 'PE')].moneda", hasItem("PEN")))
        .andExpect(jsonPath("$[?(@.codigo == 'PE')].nombreImpuesto", hasItem("IGV")))
        .andExpect(jsonPath("$[?(@.codigo == 'EC')].moneda", hasItem("USD")))
        .andExpect(jsonPath("$[?(@.codigo == 'EC')].tasaImpuesto", hasItem(15.0)))
        .andExpect(jsonPath("$[?(@.codigo == 'EC')].etiquetasDireccion.nivel2", hasItem("Cantón")));
  }

  @Test
  void losEsquemasIndicanElPrefijoDelCodigoDeCertificado() throws Exception {
    mvc.perform(get("/api/v1/catalogos/esquemas-acreditacion").session(sesion))
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[?(@.codigo == 'SAE')].prefijoCertificado", hasItem("SAE")))
        .andExpect(jsonPath("$[?(@.codigo == 'INACAL')].prefijoCertificado", hasItem("INA")));
  }

  @Test
  void lasNormasTienenCodigoDeSistemaYVariasEdiciones() throws Exception {
    mvc.perform(get("/api/v1/catalogos/normas").session(sesion))
        .andExpect(jsonPath("$", hasSize(5)))
        .andExpect(jsonPath("$[0].nombreCorto").value("ISO 9001"))
        .andExpect(jsonPath("$[0].codigoSistema").value("02"))
        .andExpect(jsonPath("$[0].ediciones[0].designacion").value("ISO 9001:2015"))
        .andExpect(jsonPath("$[?(@.codigo == 'ISO_IEC_27001')].ediciones[*].edicion", hasItem("2022")));

    org.assertj.core.api.Assertions.assertThat(catalogos.normaPorCodigoOperaciones("45"))
        .get().extracting(ServicioCatalogos.NormaCatalogo::codigoSistema).isEqualTo("05");
  }

  @Test
  void registraUnClienteEcuatorianoConSuRucYUnaSedeConCodigoDpa() throws Exception {
    String creado = mvc.perform(post("/api/v1/clientes").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"razonSocial": "Constructora Andina del Ecuador S.A.", "codigoPais": "EC",
                 "tipoDocumento": "RUC", "numeroDocumento": "1790011674001", "estado": "ACTIVO"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.codigoPais").value("EC"))
        .andExpect(jsonPath("$.numeroDocumento").value("1790011674001"))
        .andReturn().getResponse().getContentAsString();

    mvc.perform(post("/api/v1/clientes/{id}/direcciones", (String) JsonPath.read(creado, "$.id")).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"tipo": "FISCAL", "direccion": "Av. Amazonas N34-120", "departamento": "Pichincha",
                 "provincia": "Quito", "distrito": "Iñaquito", "ubigeo": "170109"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.codigoPais").value("EC"))
        .andExpect(jsonPath("$.ubigeo").value("170109"));
  }

  @Test
  void laVerificacionAdvierteUnDigitoVerificadorDudosoSinBloquear() throws Exception {
    mvc.perform(get("/api/v1/clientes/verificacion-documento").session(sesion)
            .param("codigoPais", "EC").param("tipoDocumento", "RUC").param("numeroDocumento", "1790011675001"))
        .andExpect(jsonPath("$.valido").value(true))
        .andExpect(jsonPath("$.advertencias[0]", containsString("SRI")));
    mvc.perform(get("/api/v1/clientes/verificacion-documento").session(sesion)
            .param("codigoPais", "EC").param("tipoDocumento", "RUC").param("numeroDocumento", "2510034065001"))
        .andExpect(jsonPath("$.valido").value(false))
        .andExpect(jsonPath("$.mensaje", containsString("provincia")));
  }

  @Test
  void elCodigoDeUbicacionNoAplicaFueraDePeruYEcuador() throws Exception {
    String creado = mvc.perform(post("/api/v1/clientes").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"razonSocial\": \"Empresa Colombiana\", \"codigoPais\": \"CO\"}"))
        .andReturn().getResponse().getContentAsString();

    mvc.perform(post("/api/v1/clientes/{id}/direcciones", (String) JsonPath.read(creado, "$.id")).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tipo\": \"FISCAL\", \"direccion\": \"Calle 100\", \"ubigeo\": \"110111\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("UBIGEO_NO_APLICA"));
  }
}
