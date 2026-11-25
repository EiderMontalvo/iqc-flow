package pe.com.iqc.flow.cotizaciones;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.cotizaciones.AnalistaIaSolicitudes.PropuestaIa;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.padron.ConsultaPadron;
import pe.com.iqc.flow.padron.ConsultaPadron.DatosRuc;

/**análisis de la solicitud de un cliente: solo reglas con la IA apagada, y reglas + IA cuando está encendida*/
class AnalisisSolicitudIT extends PruebaIntegracion {
  private static final String SOLICITUD = """
      Estimados IQC:
      Somos Constructora Andina SAC, RUC 20131312955. Solicitamos cotización para certificar ISO 9001:2015 e ISO 45001
      en nuestras 2 sedes (Lima y Arequipa). Contamos con 120 trabajadores. Contacto: María Pérez, maria.perez@andina.pe, 987 654 321.
      """;

  @MockitoBean private ConsultaPadron padron;
  @MockitoBean private AnalistaIaSolicitudes ia;
  private MockHttpSession comercial;
  private MockHttpSession master;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario("master@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    crearUsuario("operaciones@iqcperu.com", false, CodigosRol.COORDINADOR_AUDITORIAS);
    master = iniciarSesion("master@iqcperu.com");
    comercial = iniciarSesion("comercial@iqcperu.com");
    when(padron.ruc("20131312955")).thenReturn(Optional.of(new DatosRuc("20131312955", "CONSTRUCTORA ANDINA S.A.C.",
        "AV. FICTICIA 123", "MIRAFLORES", "LIMA", "LIMA")));
  }

  @Test
  void conLaIaApagadaUsaSoloReglasYSugiereLoQueFalta() throws Exception {
    when(ia.disponible()).thenReturn(true);
    mvc.perform(multipart("/api/v1/cotizaciones/analisis-solicitud").param("texto", SOLICITUD).session(comercial).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origen").value("LOCAL"))
        .andExpect(jsonPath("$.empresa.ruc").value("20131312955"))
        .andExpect(jsonPath("$.empresa.razonSocial").value("CONSTRUCTORA ANDINA S.A.C."))
        .andExpect(jsonPath("$.empresa.clienteId").doesNotExist())
        .andExpect(jsonPath("$.normas[0].codigo").value("ISO_9001"))
        .andExpect(jsonPath("$.normas[0].edicion").value("2015"))
        .andExpect(jsonPath("$.normas[1].codigo").value("ISO_45001"))
        .andExpect(jsonPath("$.numeroTrabajadores").value(120))
        .andExpect(jsonPath("$.numeroSedes").value(2))
        .andExpect(jsonPath("$.contacto.correo").value("maria.perez@andina.pe"))
        .andExpect(jsonPath("$.contacto.telefono").value("987654321"))
        .andExpect(jsonPath("$.advertencias[0]").value(org.hamcrest.Matchers.containsString("La IA está apagada")));
    // Operaciones no cotiza
    MockHttpSession operaciones = iniciarSesion("operaciones@iqcperu.com");
    mvc.perform(multipart("/api/v1/cotizaciones/analisis-solicitud").param("texto", SOLICITUD).session(operaciones).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void conLaIaEncendidaSumaResumenPuntosYFaltantesYReconoceAlCliente() throws Exception {
    mvc.perform(put("/api/v1/organizacion/parametros/IA_HABILITADA").session(master).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"SI\"}")).andExpect(status().isOk());
    mvc.perform(post("/api/v1/clientes").session(comercial).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"razonSocial\": \"Constructora Andina SAC\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"20131312955\"}"))
        .andExpect(status().isCreated());
    when(ia.disponible()).thenReturn(true);
    when(ia.analizar(anyString(), any())).thenReturn(Optional.of(new PropuestaIa(
        "Constructora pide certificar calidad y seguridad en dos sedes.", List.of("Quiere certificarse antes de marzo", "Dos sedes: Lima y Arequipa"),
        List.of("Trabajadores por sede"), "20131312955", "Constructora Andina SAC", "María Pérez", "Jefa de calidad", "maria.perez@andina.pe",
        "987654321", "CERTIFICACION_INICIAL", List.of("ISO 9001:2015", "ISO 45001:2018", "ISO 14001"), "Construcción de edificaciones",
        "2", "Lima y Arequipa", "120", "Antes de marzo de 2027")));
    MockMultipartFile archivo = new MockMultipartFile("archivo", "solicitud.txt", "text/plain", SOLICITUD.getBytes());
    mvc.perform(multipart("/api/v1/cotizaciones/analisis-solicitud").file(archivo).session(comercial).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origen").value("IA"))
        .andExpect(jsonPath("$.resumen").value("Constructora pide certificar calidad y seguridad en dos sedes."))
        .andExpect(jsonPath("$.puntosClave.length()").value(2))
        .andExpect(jsonPath("$.faltantes[0]").value("Trabajadores por sede"))
        .andExpect(jsonPath("$.empresa.clienteId").exists())
        .andExpect(jsonPath("$.contacto.cargo").value("Jefa de calidad"))
        .andExpect(jsonPath("$.normas.length()").value(3))
        .andExpect(jsonPath("$.normas[2].codigo").value("ISO_14001"))
        .andExpect(jsonPath("$.alcance").value("Construcción de edificaciones"))
        .andExpect(jsonPath("$.plazo").value("Antes de marzo de 2027"));
  }

  @Test
  void sinArchivoNiTextoAvisa() throws Exception {
    mvc.perform(multipart("/api/v1/cotizaciones/analisis-solicitud").session(comercial).with(csrf()))
        .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("SOLICITUD_VACIA"));
  }
}
