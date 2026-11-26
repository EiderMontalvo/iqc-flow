package pe.com.iqc.flow.correos.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.EnvioCorreo.Mensaje;

class EnvioCorreoSmtpTest {
  private static final Session SESION = Session.getInstance(new Properties());

  @Test
  void armaElMensajeConRemitenteDestinoYAcentos() throws Exception {
    MimeMessage m = EnvioCorreoSmtp.construir(SESION, new Mensaje("cliente@ejemplo.pe", "Cotización COT-2026-0001", "Estimado cliente: ñandú"),
        "administracion@iqcperu.net", "IQC Flow");
    assertThat(m.getFrom()[0].toString()).contains("administracion@iqcperu.net");
    assertThat(m.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("cliente@ejemplo.pe");
    assertThat(m.getSubject()).isEqualTo("Cotización COT-2026-0001");
    assertThat(m.getContent()).isEqualTo("Estimado cliente: ñandú");
  }

  @Test
  void rechazaUnAsuntoConSaltosDeLineaQuePodriaInyectarCabeceras() {
    assertThatThrownBy(() -> EnvioCorreoSmtp.construir(SESION, new Mensaje("a@b.pe", "Hola\r\nBcc: otro@x.pe", "x"), "yo@iqcperu.net", "IQC"))
        .isInstanceOf(ReglaNegocioException.class).hasFieldOrPropertyWithValue("codigo", "CORREO_INVALIDO");
  }

  @Test
  void rechazaUnDestinoInvalidoOConVariosDestinatarios() {
    for (String malo : new String[] {"sin-arroba", "a@b.pe, c@d.pe", "a@b.pe\nBcc: c@d.pe"}) {
      assertThatThrownBy(() -> EnvioCorreoSmtp.construir(SESION, new Mensaje(malo, "Asunto", "x"), "yo@iqcperu.net", "IQC"))
          .as(malo).isInstanceOf(ReglaNegocioException.class).hasFieldOrPropertyWithValue("codigo", "CORREO_INVALIDO");
    }
  }

  private static EnvioCorreoSmtp conCuentas() {
    return new EnvioCorreoSmtp(new PropiedadesCorreo(Map.of(
        "Administracion", new PropiedadesCorreo.Cuenta("mail.iqcperu.net", 587, "administracion@iqcperu.net", "clave-super-secreta", "IQC Flow", "mail.iqcperu.net", 993),
        "gmail", new PropiedadesCorreo.Cuenta("smtp.gmail.com", 587, "", "", "IQC Flow", "imap.gmail.com", 993)), null, null));
  }

  @Test
  void sinCredencialesLaCuentaQuedaApagadaYLoDiceClaro() {
    EnvioCorreoSmtp envio = conCuentas();
    assertThatThrownBy(() -> envio.enviar("gmail", new Mensaje("a@b.pe", "Asunto", "x")))
        .isInstanceOf(ReglaNegocioException.class).hasFieldOrPropertyWithValue("codigo", "CORREO_NO_CONFIGURADO");
  }

  @Test
  void unaCuentaQueNoExisteSeRechazaSinIntentarConectar() {
    assertThatThrownBy(() -> conCuentas().enviar("otra", new Mensaje("a@b.pe", "Asunto", "x")))
        .isInstanceOf(ReglaNegocioException.class).hasFieldOrPropertyWithValue("codigo", "CORREO_CUENTA_DESCONOCIDA");
  }

  @Test
  void listaLasCuentasEnOrdenYSoloMuestraDeCadaUnaSuRemitenteYServidor() {
    var estados = conCuentas().estados();
    assertThat(estados).extracting(e -> e.cuenta()).containsExactly("administracion", "gmail");
    assertThat(estados.get(0).configurado()).isTrue();
    assertThat(estados.get(0).remitente()).isEqualTo("administracion@iqcperu.net");
    assertThat(estados.get(0).servidor()).isEqualTo("mail.iqcperu.net:587");
    assertThat(estados.get(1).configurado()).isFalse();
    assertThat(estados.toString()).doesNotContain("clave-super-secreta");
  }

  @Test
  void laClaveNuncaSaleEnElToString() {
    var cuenta = new PropiedadesCorreo.Cuenta("mail.iqcperu.net", 587, "administracion@iqcperu.net", "clave-super-secreta", "IQC Flow", "mail.iqcperu.net", 993);
    assertThat(cuenta.toString()).doesNotContain("clave-super-secreta");
    assertThat(new PropiedadesCorreo(Map.of("administracion", cuenta), null, null).toString()).doesNotContain("clave-super-secreta");
  }

  @Test
  void conservaElHiloConLasCabecerasDeRespuesta() throws Exception {
    MimeMessage m = EnvioCorreoSmtp.construir(SESION, new Mensaje("cliente@ejemplo.pe", "Re: Cotización", "Adjunto la propuesta", "<abc@ejemplo.pe>", "<raiz@ejemplo.pe>"),
        "administracion@iqcperu.net", "IQC Flow");
    assertThat(m.getHeader("In-Reply-To", null)).isEqualTo("<abc@ejemplo.pe>");
    assertThat(m.getHeader("References", null)).isEqualTo("<raiz@ejemplo.pe> <abc@ejemplo.pe>");
  }

  @Test
  void unCorreoNuevoNoLlevaCabecerasDeRespuesta() throws Exception {
    MimeMessage m = EnvioCorreoSmtp.construir(SESION, new Mensaje("cliente@ejemplo.pe", "Hola", "Texto"), "administracion@iqcperu.net", "IQC Flow");
    assertThat(m.getHeader("In-Reply-To")).isNull();
    assertThat(m.getHeader("References")).isNull();
  }

  @Test
  void rechazaCabecerasDeRespuestaConSaltosDeLinea() {
    assertThatThrownBy(() -> EnvioCorreoSmtp.construir(SESION, new Mensaje("a@b.pe", "Re: x", "x", "<a@b.pe>\r\nBcc: otro@x.pe", null), "yo@iqcperu.net", "IQC"))
        .isInstanceOf(ReglaNegocioException.class).hasFieldOrPropertyWithValue("codigo", "CORREO_INVALIDO");
  }
}
