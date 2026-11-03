package pe.com.iqc.flow.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;

class DatosContactoIT extends PruebaIntegracion {
  private MockHttpSession sesionLuis;
  private String clienteId;

  @BeforeEach
  void crearCliente() throws Exception {
    crearUsuario("luis@iqcperu.com", false, CodigosRol.COMERCIAL);
    crearUsuario("rosa@iqcperu.com", false, CodigosRol.COMERCIAL);
    sesionLuis = iniciarSesion("luis@iqcperu.com");
    String creado = mvc.perform(post("/api/v1/clientes").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"razonSocial\": \"Minera Andina SAC\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    clienteId = JsonPath.read(creado, "$.id");
  }

  @Test
  void alMarcarUnContactoComoPrincipalElAnteriorDejaDeSerlo() throws Exception {
    agregarContacto("""
        {"nombreCompleto": "Ana Pérez", "correo": "ana@minera.pe", "roles": ["CALIDAD"], "esPrincipal": true}
        """);
    agregarContacto("""
        {"nombreCompleto": "Carlos Ruiz", "telefono": "+51 987 654 321", "roles": ["GERENCIA", "REPRESENTANTE_LEGAL"],
         "esPrincipal": true}
        """);

    mvc.perform(get("/api/v1/clientes/{id}", clienteId).session(sesionLuis))
        .andExpect(jsonPath("$.contactos", hasSize(2)))
        .andExpect(jsonPath("$.contactos[0].nombreCompleto").value("Carlos Ruiz"))
        .andExpect(jsonPath("$.contactos[0].esPrincipal").value(true))
        .andExpect(jsonPath("$.contactos[0].roles", hasSize(2)))
        .andExpect(jsonPath("$.contactos[1].esPrincipal").value(false));
  }

  @Test
  void unContactoNecesitaCorreoOTelefono() throws Exception {
    mvc.perform(post("/api/v1/clientes/{id}/contactos", clienteId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombreCompleto\": \"Sin datos\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONTACTO_SIN_MEDIO"));
    mvc.perform(post("/api/v1/clientes/{id}/contactos", clienteId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombreCompleto\": \"X\", \"correo\": \"no-es-correo\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void laBitacoraGuardaElCorreoYElTelefonoEnmascarados() throws Exception {
    agregarContacto("""
        {"nombreCompleto": "Ana Pérez", "correo": "ana.perez@minera.pe", "telefono": "987654321"}
        """);

    String registro = jdbc.queryForObject(
        "SELECT estado_nuevo::text FROM bitacora WHERE accion = 'CONTACTO_AGREGADO'", String.class);
    assertThat(registro).contains("a***@minera.pe").contains("***321")
        .doesNotContain("ana.perez@").doesNotContain("987654321");
    assertThat(jdbc.queryForObject("SELECT entidad_id FROM bitacora WHERE accion = 'CONTACTO_AGREGADO'", String.class))
        .isEqualTo(clienteId);
  }

  @Test
  void modificarUnContactoExigeLaVersionVigente() throws Exception {
    String contactoId = JsonPath.read(agregarContacto("""
        {"nombreCompleto": "Ana Pérez", "correo": "ana@minera.pe"}
        """), "$.id");

    mvc.perform(put("/api/v1/clientes/{id}/contactos/{contactoId}", clienteId, contactoId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombreCompleto\": \"Ana Pérez Díaz\", \"correo\": \"ana@minera.pe\", \"cargo\": \"Jefa de calidad\", \"version\": 0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cargo").value("Jefa de calidad"));
    mvc.perform(put("/api/v1/clientes/{id}/contactos/{contactoId}", clienteId, contactoId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombreCompleto\": \"Ana\", \"correo\": \"ana@minera.pe\", \"version\": 0}"))
        .andExpect(status().isConflict());
  }

  @Test
  void eliminarUnContactoLoQuitaDeLaFicha() throws Exception {
    String contactoId = JsonPath.read(agregarContacto("{\"nombreCompleto\": \"Ana\", \"correo\": \"ana@minera.pe\"}"), "$.id");

    mvc.perform(delete("/api/v1/clientes/{id}/contactos/{contactoId}", clienteId, contactoId).session(sesionLuis).with(csrf()))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/clientes/{id}", clienteId).session(sesionLuis))
        .andExpect(jsonPath("$.contactos", hasSize(0)));
    assertThat(contarBitacora("CONTACTO_ELIMINADO", "EXITO")).isEqualTo(1);
  }

  @Test
  void soloPuedeHaberUnaDireccionFiscal() throws Exception {
    String fiscal = """
        {"tipo": "FISCAL", "direccion": "Av. Javier Prado Este 123", "distrito": "San Isidro",
         "provincia": "Lima", "departamento": "Lima", "ubigeo": "150131"}
        """;
    mvc.perform(post("/api/v1/clientes/{id}/direcciones", clienteId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(fiscal))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.codigoPais").value("PE"));
    mvc.perform(post("/api/v1/clientes/{id}/direcciones", clienteId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tipo\": \"SEDE\", \"nombre\": \"Planta Lurín\", \"direccion\": \"Km 30 Panamericana Sur\"}"))
        .andExpect(status().isCreated());

    mvc.perform(post("/api/v1/clientes/{id}/direcciones", clienteId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(fiscal))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DIRECCION_FISCAL_DUPLICADA"));
    mvc.perform(get("/api/v1/clientes/{id}", clienteId).session(sesionLuis))
        .andExpect(jsonPath("$.direcciones", hasSize(2)));
  }

  @Test
  void elUbigeoDebeTenerSeisDigitos() throws Exception {
    mvc.perform(post("/api/v1/clientes/{id}/direcciones", clienteId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tipo\": \"SEDE\", \"direccion\": \"Calle 1\", \"ubigeo\": \"15013\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void otroComercialNoPuedeModificarLosContactos() throws Exception {
    mvc.perform(post("/api/v1/clientes/{id}/contactos", clienteId).session(iniciarSesion("rosa@iqcperu.com")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombreCompleto\": \"X\", \"correo\": \"x@x.pe\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unContactoDeOtroClienteNoSeEncuentra() throws Exception {
    String otroCliente = JsonPath.read(mvc.perform(post("/api/v1/clientes").session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"razonSocial\": \"Otro\"}"))
        .andReturn().getResponse().getContentAsString(), "$.id");
    String contactoId = JsonPath.read(agregarContacto("{\"nombreCompleto\": \"Ana\", \"correo\": \"ana@minera.pe\"}"), "$.id");

    mvc.perform(delete("/api/v1/clientes/{id}/contactos/{contactoId}", otroCliente, contactoId).session(sesionLuis).with(csrf()))
        .andExpect(status().isNotFound());
  }

  private String agregarContacto(String cuerpo) throws Exception {
    return mvc.perform(post("/api/v1/clientes/{id}/contactos", clienteId).session(sesionLuis).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
  }
}
