package pe.com.iqc.flow.correos;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.Properties;
import jakarta.activation.DataHandler;

/**arma correos (.eml) de ejemplo para las pruebas*/
public final class CorreosDePrueba {
  private static final Session SESION = Session.getInstance(new Properties());

  private CorreosDePrueba() {
  }

  public record Adjunto(String nombre, String tipo, byte[] contenido) {
  }

  /**texto plano, con adjuntos si se pasan; `messageId`, `enRespuestaA` y `referencias` pueden ser nulos*/
  public static byte[] correo(String de, String asunto, String texto, String messageId, String enRespuestaA, String referencias, Adjunto... adjuntos) {
    try {
      MimeMessage m = nuevo(de, asunto, messageId);
      if (enRespuestaA != null) m.setHeader("In-Reply-To", enRespuestaA);
      if (referencias != null) m.setHeader("References", referencias);
      if (adjuntos.length == 0) {
        m.setText(texto, "UTF-8");
      } else {
        MimeMultipart partes = new MimeMultipart("mixed");
        MimeBodyPart cuerpo = new MimeBodyPart();
        cuerpo.setText(texto, "UTF-8");
        partes.addBodyPart(cuerpo);
        for (Adjunto a : adjuntos) {
          MimeBodyPart parte = new MimeBodyPart();
          parte.setDataHandler(new DataHandler(new ByteArrayDataSource(a.contenido(), a.tipo())));
          parte.setFileName(a.nombre());
          parte.setDisposition("attachment");
          partes.addBodyPart(parte);
        }
        m.setContent(partes);
      }
      return bytes(m);
    } catch (MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  public static byte[] correo(String de, String asunto, String texto, String messageId) { return correo(de, asunto, texto, messageId, null, null); }

  /**un correo que solo trae HTML*/
  public static byte[] soloHtml(String de, String asunto, String html, String messageId) {
    try {
      MimeMessage m = nuevo(de, asunto, messageId);
      m.setContent(html, "text/html; charset=UTF-8");
      return bytes(m);
    } catch (MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static MimeMessage nuevo(String de, String asunto, String messageId) throws MessagingException {
    // el Message-ID que se pide se conserva: saveChanges lo reemplazaría por uno nuevo
    MimeMessage m = new MimeMessage(SESION) {
      @Override
      protected void updateMessageID() throws MessagingException {
        if (getHeader("Message-ID") == null) super.updateMessageID();
      }
    };
    if (de != null) {
      InternetAddress remitente = new InternetAddress(de, true);
      try {
        if (remitente.getPersonal() != null) remitente.setPersonal(remitente.getPersonal(), "UTF-8"); // como lo manda un servidor real: codificado
      } catch (java.io.UnsupportedEncodingException e) {
        throw new IllegalStateException(e);
      }
      m.setFrom(remitente);
    }
    m.setRecipients(jakarta.mail.Message.RecipientType.TO, "administracion@iqcperu.net");
    m.setSubject(asunto, "UTF-8");
    m.setSentDate(new Date());
    if (messageId != null) m.setHeader("Message-ID", messageId);
    return m;
  }

  private static byte[] bytes(MimeMessage m) {
    try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
      m.saveChanges();
      m.writeTo(salida);
      return salida.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
