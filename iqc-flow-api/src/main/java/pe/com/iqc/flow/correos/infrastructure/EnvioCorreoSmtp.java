package pe.com.iqc.flow.correos.infrastructure;

import jakarta.mail.Authenticator;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.EnvioCorreo;
import pe.com.iqc.flow.correos.infrastructure.PropiedadesCorreo.Cuenta;

/**
 *envío por SMTP autenticado con STARTTLS obligatorio y comprobación del nombre del certificado: si el servidor no ofrece TLS
 *válido, no se envía (la contraseña nunca viaja sin cifrar). El registro del servidor no incluye la clave
 */
@Component
class EnvioCorreoSmtp implements EnvioCorreo {
  private static final Logger LOG = LoggerFactory.getLogger(EnvioCorreoSmtp.class);
  private static final int TIEMPO_MS = 15_000;

  private final PropiedadesCorreo propiedades;

  EnvioCorreoSmtp(PropiedadesCorreo propiedades) { this.propiedades = propiedades; }

  @Override
  public List<Estado> estados() {
    return propiedades.cuentas().entrySet().stream().map(e -> e.getValue().configurado()
        ? new Estado(e.getKey(), true, e.getValue().usuario(), e.getValue().host() + ":" + e.getValue().puerto())
        : new Estado(e.getKey(), false, null, null)).toList();
  }

  @Override
  public String enviar(String nombre, Mensaje mensaje) {
    String id = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
    Cuenta cuenta = propiedades.cuentas().get(id);
    if (cuenta == null) throw new ReglaNegocioException("CORREO_CUENTA_DESCONOCIDA", "No existe la cuenta de correo «" + id + "».");
    if (!cuenta.configurado()) {
      throw new ReglaNegocioException("CORREO_NO_CONFIGURADO", "La cuenta «" + id + "» no tiene usuario y clave configurados en el servidor.");
    }
    try {
      MimeMessage correo = construir(sesion(cuenta), mensaje, cuenta.usuario(), cuenta.nombreRemitente());
      Transport.send(correo);
      return correo.getMessageID();
    } catch (AuthenticationFailedException e) {
      LOG.warn("El servidor de correo rechazó el usuario o la contraseña de la cuenta {} ({})", id, cuenta.usuario());
      throw new ReglaNegocioException("CORREO_NO_ENVIADO", "El servidor de correo rechazó el usuario o la contraseña de la cuenta «" + id + "».");
    } catch (MessagingException e) {
      LOG.warn("No se pudo enviar el correo de la cuenta {} por {}:{}: {}", id, cuenta.host(), cuenta.puerto(), e.getClass().getSimpleName());
      throw new ReglaNegocioException("CORREO_NO_ENVIADO", "No se pudo enviar el correo: el servidor de correo no respondió o lo rechazó.");
    }
  }

  private static Session sesion(Cuenta cuenta) {
    Properties p = new Properties();
    p.put("mail.smtp.host", cuenta.host());
    p.put("mail.smtp.port", String.valueOf(cuenta.puerto()));
    p.put("mail.smtp.auth", "true");
    p.put("mail.from", cuenta.usuario()); // el Message-ID lleva el dominio de la cuenta, no el nombre de este equipo
    p.put("mail.smtp.starttls.enable", "true");
    p.put("mail.smtp.starttls.required", "true");
    p.put("mail.smtp.ssl.checkserveridentity", "true");
    p.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
    p.put("mail.smtp.connectiontimeout", String.valueOf(TIEMPO_MS));
    p.put("mail.smtp.timeout", String.valueOf(TIEMPO_MS));
    p.put("mail.smtp.writetimeout", String.valueOf(TIEMPO_MS));
    return Session.getInstance(p, new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() { return new PasswordAuthentication(cuenta.usuario(), cuenta.clave()); }
    });
  }

  /**un valor de cabecera con saltos de línea podría inyectar otras cabeceras*/
  private static String cabeceraSegura(String valor) {
    if (valor.contains("\r") || valor.contains("\n")) throw new ReglaNegocioException("CORREO_INVALIDO", "Las cabeceras no pueden tener saltos de línea.");
    return valor;
  }

  /**arma el mensaje sin enviarlo; el destinatario y el asunto se validan para que no puedan inyectar otras cabeceras*/
  static MimeMessage construir(Session sesion, Mensaje m, String remitente, String nombreRemitente) throws MessagingException {
    if (m.asunto().contains("\r") || m.asunto().contains("\n")) {
      throw new ReglaNegocioException("CORREO_INVALIDO", "El asunto no puede tener saltos de línea.");
    }
    InternetAddress destino;
    try {
      destino = new InternetAddress(m.para(), true);
      destino.validate();
    } catch (AddressException e) {
      throw new ReglaNegocioException("CORREO_INVALIDO", "La dirección de destino no es válida.");
    }
    MimeMessage correo = new MimeMessage(sesion);
    try {
      correo.setFrom(new InternetAddress(remitente, nombreRemitente, StandardCharsets.UTF_8.name()));
    } catch (UnsupportedEncodingException e) {
      throw new MessagingException("Nombre de remitente no codificable", e);
    }
    correo.setRecipient(Message.RecipientType.TO, destino);
    correo.setSubject(m.asunto(), StandardCharsets.UTF_8.name());
    correo.setSentDate(new Date());
    correo.setText(m.texto(), StandardCharsets.UTF_8.name());
    if (m.enRespuestaA() != null) {
      correo.setHeader("In-Reply-To", cabeceraSegura(m.enRespuestaA()));
      String referencias = m.referencias() == null || m.referencias().isBlank() ? m.enRespuestaA() : m.referencias() + " " + m.enRespuestaA();
      correo.setHeader("References", cabeceraSegura(referencias));
    }
    return correo;
  }
}
