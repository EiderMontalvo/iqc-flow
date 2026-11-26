package pe.com.iqc.flow.correos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import pe.com.iqc.flow.correos.CorreosDePrueba.Adjunto;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

/**
 *la bandeja de punta a punta con un buzón y un envío falsos: lee sin duplicar, clasifica, liga al cliente, filtra, cambia, descarga y
 *responde conservando el hilo
 */
@TestPropertySource(properties = {
    "iqc.correo.cuentas.administracion.usuario=administracion@iqcperu.net", "iqc.correo.cuentas.administracion.clave=clave-1",
    "iqc.correo.cuentas.comercial.usuario=comercial@iqcperu.net", "iqc.correo.cuentas.comercial.clave=clave-2"})
@Import(BandejaIT.Falsos.class)
class BandejaIT extends PruebaIntegracion {
  @TestConfiguration
  static class Falsos {
    @Bean @Primary BuzonFalso buzonFalso() { return new BuzonFalso(); }
    @Bean @Primary EnvioFalso envioFalso() { return new EnvioFalso(); }
  }

  private static final byte[] PDF = "%PDF-1.4 solicitud de prueba".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] COTIZACION = CorreosDePrueba.correo("Ana Pérez <ana@mineraandina.pe>", "Solicitud de cotización ISO 9001", "Deseamos certificar nuestro sistema de gestión.",
      "<c1@mineraandina.pe>", null, null, new Adjunto("Solicitud ñ.pdf", "application/pdf", PDF));
  private static final byte[] PAGO = CorreosDePrueba.correo("tesoreria@otrocliente.pe", "Factura F001-23 pagada", "Adjunto el voucher del depósito.", "<p1@otrocliente.pe>");
  private static final byte[] OTRO = CorreosDePrueba.correo("alguien@gmail.com", "Hola", "Buen día, gracias.", "<o1@gmail.com>");

  @Autowired BuzonFalso buzon;
  @Autowired EnvioFalso envio;

  private MockHttpSession master;
  private Usuario usuarioMaster;
  private String clienteId;

  @BeforeEach
  void preparar() throws Exception {
    buzon.vaciar();
    envio.vaciar();
    usuarioMaster = crearUsuario("gerencia@iqcperu.com", false, CodigosRol.MASTER);
    crearUsuario("certificados@iqcperu.com", false, CodigosRol.CERTIFICADOS);
    crearUsuario("comercial@iqcperu.com", false, CodigosRol.COMERCIAL);
    master = iniciarSesion("gerencia@iqcperu.com");
    clienteId = JsonPath.read(json(post("/api/v1/clientes"), master, "{\"razonSocial\": \"Minera Andina SAC\", \"tipoDocumento\": \"RUC\", \"numeroDocumento\": \"20131312955\"}", 201), "$.id");
    json(post("/api/v1/clientes/" + clienteId + "/contactos"), master,
        "{\"nombreCompleto\": \"Ana Pérez\", \"correo\": \"Ana@MineraAndina.pe\", \"roles\": [\"CALIDAD\"], \"esPrincipal\": true}", 201);
  }

  @Test
  void sincronizaSinDuplicarClasificaYLigaAlClientePorElCorreoDeUnContacto() throws Exception {
    cargarTres();
    String primera = json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    assertThat((Boolean) JsonPath.read(primera, "$.enCurso")).isFalse();
    assertThat((Integer) primero(primera, "$.cuentas[?(@.cuenta=='administracion')].nuevos")).isEqualTo(2);
    assertThat((Integer) primero(primera, "$.cuentas[?(@.cuenta=='comercial')].nuevos")).isEqualTo(1);

    String segunda = json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    assertThat((List<Integer>) JsonPath.read(segunda, "$.cuentas[*].nuevos")).containsOnly(0);

    String bandeja = json(get("/api/v1/correos/bandeja"), master, null, 200);
    assertThat((Integer) JsonPath.read(bandeja, "$.totalElementos")).isEqualTo(3);
    assertThat((String) primero(bandeja, "$.contenido[?(@.asunto=='Solicitud de cotización ISO 9001')].tipo")).isEqualTo("COTIZACION");
    assertThat((String) primero(bandeja, "$.contenido[?(@.asunto=='Solicitud de cotización ISO 9001')].origenTipo")).isEqualTo("REGLA");
    assertThat((String) primero(bandeja, "$.contenido[?(@.asunto=='Solicitud de cotización ISO 9001')].clienteId")).isEqualTo(clienteId);
    assertThat((Integer) primero(bandeja, "$.contenido[?(@.asunto=='Solicitud de cotización ISO 9001')].adjuntos")).isEqualTo(1);
    assertThat((String) primero(bandeja, "$.contenido[?(@.asunto=='Factura F001-23 pagada')].tipo")).isEqualTo("PAGO");
    assertThat((String) primero(bandeja, "$.contenido[?(@.asunto=='Hola')].tipo")).isEqualTo("OTRO");
  }

  @Test
  void unCambioDeUidValidityNoDuplicaLosMismosMensajes() throws Exception {
    cargarTres();
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    buzon.renumerar("administracion");
    String respuesta = json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    assertThat((Integer) primero(respuesta, "$.cuentas[?(@.cuenta=='administracion')].nuevos")).isZero();
    assertThat((Integer) JsonPath.read(json(get("/api/v1/correos/bandeja"), master, null, 200), "$.totalElementos")).isEqualTo(3);
    // y lo que llegue después se sigue leyendo
    buzon.agregar("administracion", CorreosDePrueba.correo("nuevo@cliente.pe", "Cotización nueva", "Solicitud de cotización", "<n1@cliente.pe>"));
    assertThat((Integer) primero(json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200), "$.cuentas[?(@.cuenta=='administracion')].nuevos")).isEqualTo(1);
  }

  @Test
  void unaCuentaConErrorNoDetieneALasDemasYElErrorNoTraeDatosDeLaCuenta() throws Exception {
    cargarTres();
    buzon.fallar("comercial");
    String respuesta = json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    assertThat((Integer) primero(respuesta, "$.cuentas[?(@.cuenta=='administracion')].nuevos")).isEqualTo(2);
    assertThat((String) primero(respuesta, "$.cuentas[?(@.cuenta=='comercial')].error")).isEqualTo("El servidor de correo rechazó el usuario o la contraseña.");
    String estado = json(get("/api/v1/correos/bandeja/sincronizacion"), master, null, 200);
    assertThat((String) primero(estado, "$[?(@.cuenta=='comercial')].error")).doesNotContain("clave");
    assertThat((Boolean) primero(estado, "$[?(@.cuenta=='gmail')].sincronizable")).isFalse();

    buzon.repararFallas();
    String despues = json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    assertThat((Integer) primero(despues, "$.cuentas[?(@.cuenta=='comercial')].nuevos")).isEqualTo(1);
    assertThat(json(get("/api/v1/correos/bandeja/sincronizacion"), master, null, 200)).doesNotContain("rechazó");
  }

  @Test
  void unMensajeEnormeSeGuardaSoloConSuEncabezado() throws Exception {
    buzon.agregar("administracion", "From: grande@cliente.pe\r\nSubject: Informe enorme\r\nMessage-ID: <g1@cliente.pe>\r\n\r\n".getBytes(StandardCharsets.US_ASCII), true);
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    String id = JsonPath.read(json(get("/api/v1/correos/bandeja"), master, null, 200), "$.contenido[0].id");
    String detalle = json(get("/api/v1/correos/bandeja/" + id), master, null, 200);
    assertThat((String) JsonPath.read(detalle, "$.asunto")).isEqualTo("Informe enorme");
    assertThat((String) JsonPath.read(detalle, "$.texto")).contains("Mensaje muy grande");
    assertThat((Boolean) JsonPath.read(detalle, "$.textoTruncado")).isTrue();
  }

  @Test
  void soloQuienTienePermisoVeLaBandejaYSoloQuienResponderRevisaYEnvia() throws Exception {
    MockHttpSession sinPermiso = iniciarSesion("certificados@iqcperu.com");
    mvc.perform(get("/api/v1/correos/bandeja").session(sinPermiso)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/correos/bandeja/contadores").session(sinPermiso)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/correos/bandeja/sincronizacion").session(sinPermiso).with(csrf())).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/correos/enviados").session(sinPermiso).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"cuenta\": \"comercial\", \"para\": \"a@b.pe\", \"asunto\": \"x\", \"cuerpo\": \"x\"}")).andExpect(status().isForbidden());
    MockHttpSession comercial = iniciarSesion("comercial@iqcperu.com");
    mvc.perform(get("/api/v1/correos/bandeja").session(comercial)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/correos/bandeja").with(csrf())).andExpect(status().isUnauthorized());
  }

  @Test
  void filtraPorTipoCuentaEstadoTextoYFechasYCuentaLosNuevos() throws Exception {
    cargarTres();
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    assertThat(total("?tipo=PAGO")).isEqualTo(1);
    assertThat(total("?cuenta=comercial")).isEqualTo(1);
    assertThat(total("?cuenta=Administracion")).isEqualTo(2);
    assertThat(total("?estado=NUEVO")).isEqualTo(3);
    assertThat(total("?estado=ATENDIDO")).isZero();
    assertThat(total("?sinAtender=true")).isEqualTo(3);
    assertThat(total("?texto=voucher")).isEqualTo(1);
    assertThat(total("?texto=ana@mineraandina")).isEqualTo(1);
    assertThat(total("?texto=%25")).as("el % se busca como símbolo, no como comodín").isZero();
    LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
    assertThat(total("?desde=" + hoy + "&hasta=" + hoy)).isEqualTo(3);
    assertThat(total("?hasta=" + hoy.minusDays(1))).isZero();
    assertThat(total("?tamano=2&pagina=1")).isEqualTo(3);
    mvc.perform(get("/api/v1/correos/bandeja?tamano=500").session(master)).andExpect(status().isBadRequest());

    String contadores = json(get("/api/v1/correos/bandeja/contadores"), master, null, 200);
    assertThat((Integer) JsonPath.read(contadores, "$.nuevos")).isEqualTo(3);
    assertThat((Integer) JsonPath.read(contadores, "$.nuevosPorCuenta.administracion")).isEqualTo(2);
    assertThat((Integer) JsonPath.read(contadores, "$.nuevosPorTipo.PAGO")).isEqualTo(1);
  }

  @Test
  void cambiaTipoEstadoResponsableYClienteConVersionYLoDejaEnLaBitacora() throws Exception {
    cargarTres();
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    String id = idDe("Hola");
    String detalle = json(get("/api/v1/correos/bandeja/" + id), master, null, 200);
    long version = ((Number) JsonPath.read(detalle, "$.version")).longValue();

    String cambiado = json(patch("/api/v1/correos/bandeja/" + id), master, "{\"tipo\": \"RECLAMO\", \"estado\": \"EN_CURSO\", \"responsableId\": \"" + usuarioMaster.getId()
        + "\", \"clienteId\": \"" + clienteId + "\", \"version\": " + version + "}", 200);
    assertThat((String) JsonPath.read(cambiado, "$.tipo")).isEqualTo("RECLAMO");
    assertThat((String) JsonPath.read(cambiado, "$.origenTipo")).isEqualTo("MANUAL");
    assertThat((String) JsonPath.read(cambiado, "$.estado")).isEqualTo("EN_CURSO");
    assertThat((String) JsonPath.read(cambiado, "$.responsableId")).isEqualTo(usuarioMaster.getId().toString());
    assertThat((String) JsonPath.read(cambiado, "$.cliente.razonSocial")).isEqualTo("Minera Andina SAC");
    assertThat(((Number) JsonPath.read(cambiado, "$.version")).longValue()).isEqualTo(version + 1);

    json(patch("/api/v1/correos/bandeja/" + id), master, "{\"estado\": \"ATENDIDO\", \"version\": " + version + "}", 409);
    json(patch("/api/v1/correos/bandeja/" + id), master, "{\"clienteId\": \"" + UUID.randomUUID() + "\", \"version\": " + (version + 1) + "}", 422);
    json(patch("/api/v1/correos/bandeja/" + id), master, "{\"responsableId\": \"" + UUID.randomUUID() + "\", \"version\": " + (version + 1) + "}", 422);
    String sinCliente = json(patch("/api/v1/correos/bandeja/" + id), master, "{\"quitarCliente\": true, \"quitarResponsable\": true, \"version\": " + (version + 1) + "}", 200);
    assertThat((Object) JsonPath.read(sinCliente, "$.cliente")).isNull();
    assertThat((Object) JsonPath.read(sinCliente, "$.responsableId")).isNull();
    json(patch("/api/v1/correos/bandeja/" + id), master, "{\"estado\": \"ATENDIDO\"}", 400);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'CORREO_ACTUALIZADO'", Integer.class)).isEqualTo(2);
    assertThat(total("?tipo=RECLAMO")).isEqualTo(1);
  }

  @Test
  void descargaUnAdjuntoComoDescargaSegurayElCorreoOriginal() throws Exception {
    cargarTres();
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    String id = idDe("Solicitud de cotización ISO 9001");
    String detalle = json(get("/api/v1/correos/bandeja/" + id), master, null, 200);
    assertThat((String) JsonPath.read(detalle, "$.adjuntos[0].nombre")).isEqualTo("Solicitud ñ.pdf");
    assertThat((Integer) JsonPath.read(detalle, "$.adjuntos[0].indice")).isZero();

    byte[] bytes = mvc.perform(get("/api/v1/correos/bandeja/{id}/adjuntos/0", id).session(master)).andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
        .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))).andReturn().getResponse().getContentAsByteArray();
    assertThat(bytes).isEqualTo(PDF);
    mvc.perform(get("/api/v1/correos/bandeja/{id}/adjuntos/5", id).session(master)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/correos/bandeja/{id}/adjuntos/-1", id).session(master)).andExpect(status().isBadRequest());
    byte[] eml = mvc.perform(get("/api/v1/correos/bandeja/{id}/eml", id).session(master)).andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "message/rfc822")).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
        .andReturn().getResponse().getContentAsByteArray();
    assertThat(eml).isEqualTo(COTIZACION);
    mvc.perform(get("/api/v1/correos/bandeja/{id}", UUID.randomUUID()).session(master)).andExpect(status().isNotFound());
  }

  @Test
  void respondeConservandoElHiloLoGuardaYMarcaAtendidoYSeVeElHiloEnElDetalle() throws Exception {
    buzon.agregar("administracion", COTIZACION);
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    String id = idDe("Solicitud de cotización ISO 9001");

    String respuesta = json(post("/api/v1/correos/bandeja/" + id + "/respuestas"), master, "{\"cuerpo\": \"Gracias, le enviaremos la propuesta.\", \"marcarAtendido\": true}", 201);
    assertThat((String) JsonPath.read(respuesta, "$.cuenta")).isEqualTo("administracion");
    assertThat((String) JsonPath.read(respuesta, "$.para")).isEqualTo("ana@mineraandina.pe");
    assertThat((String) JsonPath.read(respuesta, "$.asunto")).isEqualTo("Re: Solicitud de cotización ISO 9001");
    assertThat(envio.enviados()).hasSize(1);
    var enviado = envio.enviados().get(0);
    assertThat(enviado.mensaje().enRespuestaA()).isEqualTo("<c1@mineraandina.pe>");
    assertThat(enviado.mensaje().texto()).isEqualTo("Gracias, le enviaremos la propuesta.");
    assertThat((String) JsonPath.read(respuesta, "$.messageId")).isEqualTo(enviado.messageId());

    // el cliente contesta: queda en el hilo, en los dos sentidos
    buzon.agregar("administracion", CorreosDePrueba.correo("Ana Pérez <ana@mineraandina.pe>", "Re: Solicitud de cotización ISO 9001", "Perfecto, gracias.", "<c2@mineraandina.pe>",
        "<c1@mineraandina.pe>", "<c1@mineraandina.pe>"));
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    String detalle = json(get("/api/v1/correos/bandeja/" + id), master, null, 200);
    assertThat((String) JsonPath.read(detalle, "$.estado")).isEqualTo("ATENDIDO");
    assertThat((List<?>) JsonPath.read(detalle, "$.respuestas")).hasSize(1);
    assertThat((String) JsonPath.read(detalle, "$.respuestas[0].texto")).startsWith("Gracias");
    assertThat((String) JsonPath.read(detalle, "$.hilo[0].asunto")).isEqualTo("Re: Solicitud de cotización ISO 9001");
    String idRespuesta = idDe("Re: Solicitud de cotización ISO 9001");
    assertThat((String) JsonPath.read(json(get("/api/v1/correos/bandeja/" + idRespuesta), master, null, 200), "$.hilo[0].id")).isEqualTo(id);

    assertThat(jdbc.queryForObject("SELECT estado_nuevo ->> 'destino' FROM bitacora WHERE accion = 'CORREO_RESPONDIDO'", String.class)).isEqualTo("a***@mineraandina.pe");
  }

  @Test
  void respondeDesdeOtraCuentaYRechazaUnaCuentaQueNoExisteSinGuardarNada() throws Exception {
    buzon.agregar("comercial", OTRO);
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    String id = idDe("Hola");
    json(post("/api/v1/correos/bandeja/" + id + "/respuestas"), master, "{\"cuenta\": \"administracion\", \"asunto\": \"Re: Hola\", \"cuerpo\": \"¡Hola!\"}", 201);
    assertThat(envio.enviados().get(0).cuenta()).isEqualTo("administracion");
    assertThat(envio.enviados().get(0).mensaje().asunto()).isEqualTo("Re: Hola");
    json(post("/api/v1/correos/bandeja/" + id + "/respuestas"), master, "{\"cuenta\": \"inventada\", \"cuerpo\": \"x\"}", 422);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM correos_enviados", Integer.class)).isEqualTo(1);
    assertThat((String) JsonPath.read(json(get("/api/v1/correos/bandeja/" + id), master, null, 200), "$.estado")).isEqualTo("NUEVO");
    json(post("/api/v1/correos/bandeja/" + id + "/respuestas"), master, "{\"cuerpo\": \"\"}", 400);
    json(post("/api/v1/correos/bandeja/" + UUID.randomUUID() + "/respuestas"), master, "{\"cuerpo\": \"x\"}", 404);
  }

  @Test
  void enviaUnCorreoNuevoSinHiloYValidaLosDatos() throws Exception {
    String enviado = json(post("/api/v1/correos/enviados"), master, "{\"cuenta\": \"comercial\", \"para\": \"cliente@ejemplo.pe\", \"asunto\": \"Propuesta\", \"cuerpo\": \"Adjuntamos la propuesta.\"}", 201);
    assertThat((Object) JsonPath.read(enviado, "$.correoRecibidoId")).isNull();
    assertThat(envio.enviados()).hasSize(1);
    assertThat(envio.enviados().get(0).mensaje().enRespuestaA()).isNull();
    json(post("/api/v1/correos/enviados"), master, "{\"cuenta\": \"comercial\", \"para\": \"no-es-correo\", \"asunto\": \"x\", \"cuerpo\": \"x\"}", 400);
    json(post("/api/v1/correos/enviados"), master, "{\"cuenta\": \"comercial\", \"para\": \"a@b.pe\", \"asunto\": \"\", \"cuerpo\": \"x\"}", 400);
    json(post("/api/v1/correos/enviados"), master, "{\"cuenta\": \"comercial\", \"para\": \"a@b.pe\", \"asunto\": \"x\", \"cuerpo\": \"" + "x".repeat(20_001) + "\"}", 400);
    assertThat(envio.enviados()).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'CORREO_ENVIADO'", Integer.class)).isEqualTo(1);
  }

  // ---- PUBLICIDAD: no ensucia la bandeja, se descarta en bloque y se reclasifica a pedido ----

  private static byte[] masivo(String asunto, String texto, String messageId) {
    return ("From: Ventas <ventas@promo-ejemplo.pe>\r\nTo: administracion@iqcperu.net\r\nSubject: " + asunto + "\r\nMessage-ID: " + messageId
        + "\r\nList-Unsubscribe: <mailto:baja@promo-ejemplo.pe>\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + texto + "\r\n").getBytes(StandardCharsets.UTF_8);
  }

  private static final byte[] PUBLICIDAD = masivo("Haz crecer tus ventas con nuestra Base de Datos de Empresas por solo S/119", "Pide tu demo gratis. Para darse de baja responda BAJA.", "<pub1@promo-ejemplo.pe>");
  /**sin una sola palabra comercial: solo sus cabeceras de envío masivo lo delatan*/
  private static final byte[] BOLETIN = masivo("Novedades de febrero", "Hola, buen día.", "<pub2@promo-ejemplo.pe>");

  @Test
  void laPublicidadNoSaleEnLaBandejaPorDefectoNiEnLosContadoresPeroSeCuentaAparte() throws Exception {
    buzon.agregar("administracion", COTIZACION);
    buzon.agregar("administracion", PUBLICIDAD);
    buzon.agregar("comercial", BOLETIN);
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);

    assertThat(total("")).as("por defecto no sale la publicidad").isEqualTo(1);
    assertThat(total("?tipo=PUBLICIDAD")).isEqualTo(2);
    assertThat(total("?incluirPublicidad=true")).isEqualTo(3);
    assertThat(total("?incluirPublicidad=false")).isEqualTo(1);
    assertThat(total("?texto=demo")).as("tampoco aparece al buscar").isZero();
    assertThat(total("?sinAtender=true")).isEqualTo(1);
    assertThat(total("?cuenta=comercial")).isZero();
    assertThat(total("?tipo=PUBLICIDAD&cuenta=comercial")).isEqualTo(1);

    String contadores = json(get("/api/v1/correos/bandeja/contadores"), master, null, 200);
    assertThat((Integer) JsonPath.read(contadores, "$.nuevos")).isEqualTo(1);
    assertThat((Integer) JsonPath.read(contadores, "$.publicidad")).isEqualTo(2);
    assertThat((java.util.Map<String, Object>) JsonPath.read(contadores, "$.nuevosPorTipo")).containsOnlyKeys("COTIZACION");
    assertThat((java.util.Map<String, Object>) JsonPath.read(contadores, "$.nuevosPorCuenta")).containsOnlyKeys("administracion");

    String id = idDePublicidad("Haz crecer tus ventas con nuestra Base de Datos de Empresas por solo S/119");
    String detalle = json(get("/api/v1/correos/bandeja/" + id), master, null, 200);
    assertThat((String) JsonPath.read(detalle, "$.tipo")).isEqualTo("PUBLICIDAD");
    assertThat((String) JsonPath.read(detalle, "$.estado")).as("no se marca atendida ni descartada sola").isEqualTo("NUEVO");
    assertThat((String) JsonPath.read(detalle, "$.motivoTipo")).startsWith("Parece publicidad");
  }

  @Test
  void descartaEnBloqueSoloLaPublicidadNuevaYLoDejaEnLaBitacora() throws Exception {
    buzon.agregar("administracion", PUBLICIDAD);
    buzon.agregar("administracion", BOLETIN);
    buzon.agregar("administracion", COTIZACION);
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    String tomada = idDePublicidad("Novedades de febrero");
    long version = ((Number) JsonPath.read(json(get("/api/v1/correos/bandeja/" + tomada), master, null, 200), "$.version")).longValue();
    json(patch("/api/v1/correos/bandeja/" + tomada), master, "{\"estado\": \"EN_CURSO\", \"version\": " + version + "}", 200);

    mvc.perform(post("/api/v1/correos/bandeja/descartar-publicidad").session(iniciarSesion("certificados@iqcperu.com")).with(csrf())).andExpect(status().isForbidden());
    String respuesta = json(post("/api/v1/correos/bandeja/descartar-publicidad"), master, "", 200);
    assertThat((Integer) JsonPath.read(respuesta, "$.descartados")).isEqualTo(1);
    assertThat(total("?tipo=PUBLICIDAD&estado=DESCARTADO")).isEqualTo(1);
    assertThat(total("?tipo=PUBLICIDAD&estado=EN_CURSO")).as("la que alguien ya tomó no se toca").isEqualTo(1);
    assertThat(total("")).as("la cotización sigue ahí").isEqualTo(1);
    assertThat((Integer) JsonPath.read(json(get("/api/v1/correos/bandeja/contadores"), master, null, 200), "$.publicidad")).as("queda la EN_CURSO, sin atender").isEqualTo(1);
    assertThat((Integer) JsonPath.read(json(post("/api/v1/correos/bandeja/descartar-publicidad"), master, "", 200), "$.descartados")).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'PUBLICIDAD_DESCARTADA_EN_BLOQUE'", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT estado_nuevo ->> 'descartados' FROM bitacora WHERE accion = 'PUBLICIDAD_DESCARTADA_EN_BLOQUE' ORDER BY id LIMIT 1", String.class)).isEqualTo("1");
  }

  @Test
  void reclasificaConLasReglasActualesSinPisarLoFijadoAManoNiPorLaIAYUsaLasCabecerasDelEml() throws Exception {
    buzon.agregar("administracion", COTIZACION);
    buzon.agregar("administracion", BOLETIN);
    buzon.agregar("administracion", PAGO);
    buzon.agregar("administracion", CorreosDePrueba.correo("alguien@cliente-ejemplo.pe", "Factura F001-24 pendiente", "Falta el pago.", "<p2@cliente-ejemplo.pe>"));
    json(post("/api/v1/correos/bandeja/sincronizacion"), master, "", 200);
    // lo que había antes de que existieran las reglas nuevas: todo OTRO, de regla; salvo uno fijado a mano y otro por la IA
    jdbc.update("UPDATE correos_recibidos SET tipo = 'OTRO', motivo_tipo = 'Sin palabras que lo indiquen con claridad'");
    jdbc.update("UPDATE correos_recibidos SET origen_tipo = 'MANUAL' WHERE asunto = 'Factura F001-23 pagada'");
    jdbc.update("UPDATE correos_recibidos SET origen_tipo = 'IA' WHERE asunto = 'Factura F001-24 pendiente'");

    mvc.perform(post("/api/v1/correos/bandeja/reclasificacion").session(iniciarSesion("certificados@iqcperu.com")).with(csrf())).andExpect(status().isForbidden());
    String respuesta = json(post("/api/v1/correos/bandeja/reclasificacion"), master, "", 200);
    assertThat((Integer) JsonPath.read(respuesta, "$.revisados")).as("solo los de regla").isEqualTo(2);
    assertThat((Integer) JsonPath.read(respuesta, "$.cambiaron")).isEqualTo(2);
    assertThat(tipoDe("Solicitud de cotización ISO 9001")).isEqualTo("COTIZACION");
    assertThat(tipoDe("Novedades de febrero")).as("solo lo delatan las cabeceras del .eml").isEqualTo("PUBLICIDAD");
    assertThat(tipoDe("Factura F001-23 pagada")).as("fijado a mano").isEqualTo("OTRO");
    assertThat(tipoDe("Factura F001-24 pendiente")).as("fijado por la IA").isEqualTo("OTRO");
    assertThat(jdbc.queryForObject("SELECT estado FROM correos_recibidos WHERE asunto = 'Novedades de febrero'", String.class)).as("no toca el estado").isEqualTo("NUEVO");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'CORREOS_RECLASIFICADOS'", Integer.class)).isEqualTo(1);

    assertThat((Integer) JsonPath.read(json(post("/api/v1/correos/bandeja/reclasificacion"), master, "", 200), "$.cambiaron")).as("una segunda vez no cambia nada").isZero();
  }

  private String tipoDe(String asunto) {
    return jdbc.queryForObject("SELECT tipo FROM correos_recibidos WHERE asunto = ?", String.class, asunto);
  }

  private String idDePublicidad(String asunto) throws Exception {
    return primero(json(get("/api/v1/correos/bandeja?tipo=PUBLICIDAD&tamano=100"), master, null, 200), "$.contenido[?(@.asunto=='" + asunto + "')].id");
  }

  private void cargarTres() {
    buzon.agregar("administracion", COTIZACION);
    buzon.agregar("administracion", PAGO);
    buzon.agregar("comercial", OTRO);
  }

  /**el primer valor de una ruta con filtro (JsonPath las devuelve siempre como lista)*/
  private static <T> T primero(String json, String ruta) {
    List<T> valores = JsonPath.read(json, ruta);
    return valores.get(0);
  }

  private int total(String consulta) throws Exception {
    return JsonPath.read(json(get("/api/v1/correos/bandeja" + consulta), master, null, 200), "$.totalElementos");
  }

  private String idDe(String asunto) throws Exception {
    return primero(json(get("/api/v1/correos/bandeja?tamano=100"), master, null, 200), "$.contenido[?(@.asunto=='" + asunto + "')].id");
  }

  private String json(MockHttpServletRequestBuilder solicitud, MockHttpSession sesion, String cuerpo, int estadoEsperado) throws Exception {
    var s = solicitud.session(sesion).with(csrf());
    if (cuerpo != null) s = s.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    var respuesta = mvc.perform(s).andReturn().getResponse();
    assertThat(respuesta.getStatus()).as(respuesta.getContentAsString()).isEqualTo(estadoEsperado);
    return respuesta.getContentAsString();
  }
}
