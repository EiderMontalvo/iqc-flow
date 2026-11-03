package pe.com.iqc.flow.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

class ClientesIT extends PruebaIntegracion {
  private static final String RUC_SUNAT = "20131312955";
  private static final String RUC_BCP = "20100047218";

  private Usuario luis;
  private Usuario rosa;
  private Usuario coordinador;

  @BeforeEach
  void crearUsuarios() {
    crearUsuario("ana@iqcperu.com", false, CodigosRol.MASTER);
    luis = crearUsuario("luis@iqcperu.com", false, CodigosRol.COMERCIAL);
    rosa = crearUsuario("rosa@iqcperu.com", false, CodigosRol.COMERCIAL);
    coordinador = crearUsuario("coordinador@iqcperu.com", false, CodigosRol.COORDINADOR_AUDITORIAS);
  }

  @Test
  void unComercialRegistraUnProspectoSinDocumentoYQuedaComoResponsable() throws Exception {
    MockHttpSession sesion = iniciarSesion("luis@iqcperu.com");

    mvc.perform(post("/api/v1/clientes").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"razonSocial": "  Minera Andina SAC  ", "origen": "REFERIDO", "sector": "Minería"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.codigo", matchesPattern("CLI-\\d{6}")))
        .andExpect(jsonPath("$.razonSocial").value("Minera Andina SAC"))
        .andExpect(jsonPath("$.estado").value("PROSPECTO"))
        .andExpect(jsonPath("$.codigoPais").value("PE"))
        .andExpect(jsonPath("$.responsable.id").value(luis.getId().toString()))
        .andExpect(jsonPath("$.responsable.nombre").value(luis.getNombre()))
        .andExpect(jsonPath("$.puedeEditar").value(true));

    assertThat(contarBitacora("CLIENTE_CREADO", "EXITO")).isEqualTo(1);
  }

  @Test
  void registraUnClienteActivoConElRucNormalizado() throws Exception {
    mvc.perform(post("/api/v1/clientes").session(iniciarSesion("luis@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"razonSocial": "Superintendencia Nacional de Aduanas", "tipoDocumento": "RUC",
                 "numeroDocumento": " 2013 1312955 ", "estado": "ACTIVO"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.numeroDocumento").value(RUC_SUNAT))
        .andExpect(jsonPath("$.estado").value("ACTIVO"));
  }

  @Test
  void rechazaUnRucConDigitoVerificadorIncorrecto() throws Exception {
    mvc.perform(post("/api/v1/clientes").session(iniciarSesion("luis@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"razonSocial": "Empresa X", "tipoDocumento": "RUC", "numeroDocumento": "20131312954"}
                """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DOCUMENTO_INVALIDO"));
  }

  @Test
  void unClienteActivoNecesitaDocumento() throws Exception {
    mvc.perform(post("/api/v1/clientes").session(iniciarSesion("luis@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"razonSocial": "Empresa X", "estado": "ACTIVO"}
                """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DOCUMENTO_REQUERIDO"));
  }

  @Test
  void unRucDuplicadoIndicaQueClienteLoTiene() throws Exception {
    MockHttpSession sesion = iniciarSesion("luis@iqcperu.com");
    String codigo = JsonPath.read(crearCliente(sesion, "SUNAT", RUC_SUNAT), "$.codigo");

    mvc.perform(post("/api/v1/clientes").session(iniciarSesion("rosa@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoCliente("Otra razón social", RUC_SUNAT)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("DOCUMENTO_DUPLICADO"))
        .andExpect(jsonPath("$.detail", containsString(codigo)));
  }

  @Test
  void unComercialVeLosClientesDeOtrosPeroNoPuedeEditarlos() throws Exception {
    String id = JsonPath.read(crearCliente(iniciarSesion("luis@iqcperu.com"), "Cliente de Luis", null), "$.id");
    MockHttpSession sesionRosa = iniciarSesion("rosa@iqcperu.com");

    mvc.perform(get("/api/v1/clientes/{id}", id).session(sesionRosa))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.puedeEditar").value(false));
    mvc.perform(patch("/api/v1/clientes/{id}", id).session(sesionRosa).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"sector": "Pesca", "version": 0}
                """))
        .andExpect(status().isForbidden());

    assertThat(contarBitacora("ACCESO_DENEGADO", "DENEGADO")).isEqualTo(1);
  }

  @Test
  void unComercialNoPuedeAsignarOtroResponsable() throws Exception {
    mvc.perform(post("/api/v1/clientes").session(iniciarSesion("luis@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"razonSocial": "Empresa X", "responsableId": "%s"}
                """.formatted(rosa.getId())))
        .andExpect(status().isForbidden());
  }

  @Test
  void alReasignarElNuevoResponsablePuedeEditarYElAnteriorNo() throws Exception {
    String creado = crearCliente(iniciarSesion("luis@iqcperu.com"), "Cliente reasignable", null);
    String id = JsonPath.read(creado, "$.id");

    mvc.perform(put("/api/v1/clientes/{id}/responsable", id).session(iniciarSesion("ana@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"responsableId": "%s", "version": 0}
                """.formatted(rosa.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.responsable.id").value(rosa.getId().toString()))
        .andExpect(jsonPath("$.version").value(1));

    mvc.perform(patch("/api/v1/clientes/{id}", id).session(iniciarSesion("rosa@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"sector": "Pesca", "version": 1}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sector").value("Pesca"));
    mvc.perform(patch("/api/v1/clientes/{id}", id).session(iniciarSesion("luis@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"sector": "Agro", "version": 2}
                """))
        .andExpect(status().isForbidden());
    assertThat(contarBitacora("CLIENTE_REASIGNADO", "EXITO")).isEqualTo(1);
  }

  @Test
  void elResponsableDebePoderGestionarClientes() throws Exception {
    String id = JsonPath.read(crearCliente(iniciarSesion("luis@iqcperu.com"), "Cliente", null), "$.id");

    mvc.perform(put("/api/v1/clientes/{id}/responsable", id).session(iniciarSesion("ana@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"responsableId": "%s", "version": 0}
                """.formatted(coordinador.getId())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("RESPONSABLE_SIN_PERMISO"));
  }

  @Test
  void laBusquedaIgnoraTildesYMayusculasYFiltraPorEstado() throws Exception {
    MockHttpSession sesion = iniciarSesion("luis@iqcperu.com");
    crearCliente(sesion, "Compañía Minera del Perú S.A.", null);
    crearCliente(sesion, "Agroindustrias Ñuñoa", RUC_SUNAT);
    String activo = crearCliente(sesion, "Banco de Crédito", RUC_BCP);
    mvc.perform(patch("/api/v1/clientes/{id}", (String) JsonPath.read(activo, "$.id")).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"estado": "ACTIVO", "version": 0}
                """))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/clientes").param("texto", "MINERA DEL PERU").session(sesion))
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].razonSocial").value("Compañía Minera del Perú S.A."));
    mvc.perform(get("/api/v1/clientes").param("texto", "nunoa").session(sesion))
        .andExpect(jsonPath("$.totalElementos").value(1));
    mvc.perform(get("/api/v1/clientes").param("texto", "2013131").session(sesion))
        .andExpect(jsonPath("$.contenido[0].numeroDocumento").value(RUC_SUNAT));
    mvc.perform(get("/api/v1/clientes").param("estado", "ACTIVO").session(sesion))
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].responsable.nombre").value(luis.getNombre()));
    mvc.perform(get("/api/v1/clientes").param("responsableId", rosa.getId().toString()).session(sesion))
        .andExpect(jsonPath("$.totalElementos").value(0));
  }

  @Test
  void elCoordinadorDeAuditoriasSoloConsulta() throws Exception {
    MockHttpSession sesion = iniciarSesion("coordinador@iqcperu.com");

    mvc.perform(get("/api/v1/clientes").session(sesion)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/clientes").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoCliente("Empresa X", null)))
        .andExpect(status().isForbidden());
  }

  @Test
  void archivarOcultaElClienteYLiberaSuDocumentoHastaRestaurarlo() throws Exception {
    MockHttpSession sesion = iniciarSesion("ana@iqcperu.com");
    String id = JsonPath.read(crearCliente(sesion, "Cliente antiguo", RUC_SUNAT), "$.id");

    mvc.perform(post("/api/v1/clientes/{id}/archivo", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"version": 0, "motivo": "Registro duplicado"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivadoEn").exists())
        .andExpect(jsonPath("$.puedeEditar").value(false));
    mvc.perform(get("/api/v1/clientes").session(sesion)).andExpect(jsonPath("$.totalElementos").value(0));
    mvc.perform(get("/api/v1/clientes").param("incluirArchivados", "true").session(sesion))
        .andExpect(jsonPath("$.totalElementos").value(1));

    crearCliente(sesion, "Cliente nuevo", RUC_SUNAT);

    mvc.perform(post("/api/v1/clientes/{id}/restauracion", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"version": 1}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("DOCUMENTO_DUPLICADO"));
    assertThat(jdbc.queryForObject("SELECT motivo FROM bitacora WHERE accion = 'CLIENTE_ARCHIVADO'", String.class))
        .isEqualTo("Registro duplicado");
  }

  @Test
  void unComercialNoPuedeArchivarNiEditarUnClienteArchivado() throws Exception {
    MockHttpSession sesionLuis = iniciarSesion("luis@iqcperu.com");
    String id = JsonPath.read(crearCliente(sesionLuis, "Cliente", null), "$.id");

    mvc.perform(post("/api/v1/clientes/{id}/archivo", id).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0}"))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/v1/clientes/{id}/archivo", id).session(iniciarSesion("ana@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0}"))
        .andExpect(status().isOk());
    mvc.perform(patch("/api/v1/clientes/{id}", id).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"sector\": \"Pesca\", \"version\": 1}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CLIENTE_ARCHIVADO"));
  }

  @Test
  void unaVersionDesactualizadaDevuelve409() throws Exception {
    MockHttpSession sesion = iniciarSesion("luis@iqcperu.com");
    String id = JsonPath.read(crearCliente(sesion, "Cliente", null), "$.id");

    mvc.perform(patch("/api/v1/clientes/{id}", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"sector\": \"Pesca\", \"version\": 7}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CONFLICTO_VERSION"));
  }

  @Test
  void enLaEdicionUnTextoVacioBorraElCampoYLaBitacoraGuardaAmbosEstados() throws Exception {
    MockHttpSession sesion = iniciarSesion("luis@iqcperu.com");
    String id = JsonPath.read(crearCliente(sesion, "Cliente", null), "$.id");
    mvc.perform(patch("/api/v1/clientes/{id}", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombreComercial\": \"Marca\", \"version\": 0}"))
        .andExpect(jsonPath("$.nombreComercial").value("Marca"));

    mvc.perform(patch("/api/v1/clientes/{id}", id).session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombreComercial\": \"\", \"version\": 1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombreComercial").value(nullValue()));

    String anterior = jdbc.queryForObject("SELECT estado_anterior::text FROM bitacora WHERE accion = "
        + "'CLIENTE_ACTUALIZADO' ORDER BY ocurrido_en DESC LIMIT 1", String.class);
    assertThat(anterior).contains("\"nombreComercial\": \"Marca\"");
  }

  @Test
  void verificaUnDocumentoAntesDeRegistrar() throws Exception {
    MockHttpSession sesion = iniciarSesion("rosa@iqcperu.com");
    String codigo = JsonPath.read(crearCliente(iniciarSesion("luis@iqcperu.com"), "SUNAT", RUC_SUNAT), "$.codigo");

    mvc.perform(get("/api/v1/clientes/verificacion-documento").session(sesion)
            .param("tipoDocumento", "RUC").param("numeroDocumento", "20131312954"))
        .andExpect(jsonPath("$.valido").value(false))
        .andExpect(jsonPath("$.mensaje", containsString("verificador")));
    mvc.perform(get("/api/v1/clientes/verificacion-documento").session(sesion)
            .param("tipoDocumento", "RUC").param("numeroDocumento", RUC_BCP))
        .andExpect(jsonPath("$.valido").value(true))
        .andExpect(jsonPath("$.existente").value(nullValue()));
    mvc.perform(get("/api/v1/clientes/verificacion-documento").session(sesion)
            .param("tipoDocumento", "RUC").param("numeroDocumento", RUC_SUNAT))
        .andExpect(jsonPath("$.existente.codigo").value(codigo));
  }

  @Test
  void losClientesDeOtraOrganizacionNoSonVisibles() throws Exception {
    UUID otra = organizaciones.asegurarExistencia("OTRA", "Otra organización").id();
    crearUsuarioEn(otra, "ajeno@otra.com", false, CodigosRol.MASTER);
    String id = JsonPath.read(crearCliente(iniciarSesion("ajeno@otra.com"), "Cliente ajeno", RUC_SUNAT), "$.id");
    MockHttpSession sesion = iniciarSesion("ana@iqcperu.com");

    mvc.perform(get("/api/v1/clientes/{id}", id).session(sesion))
        .andExpect(status().isNotFound());
    // El mismo RUC puede existir en otra organización
    crearCliente(sesion, "SUNAT", RUC_SUNAT);
  }

  private String crearCliente(MockHttpSession sesion, String razonSocial, String ruc) throws Exception {
    return mvc.perform(post("/api/v1/clientes").session(sesion).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoCliente(razonSocial, ruc)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
  }

  private static String cuerpoCliente(String razonSocial, String ruc) {
    return ruc == null
        ? "{\"razonSocial\": \"%s\"}".formatted(razonSocial)
        : "{\"razonSocial\": \"%s\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"%s\"}".formatted(razonSocial, ruc);
  }
}
