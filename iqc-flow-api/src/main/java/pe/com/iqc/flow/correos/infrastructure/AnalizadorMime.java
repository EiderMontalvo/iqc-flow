package pe.com.iqc.flow.correos.infrastructure;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.domain.ConvertidorHtml;
import pe.com.iqc.flow.correos.domain.MensajeLeido;
import pe.com.iqc.flow.correos.domain.MensajeLeido.Adjunto;

/**
 *de los bytes de un correo (.eml) a lo que se guarda: encabezados, texto plano y los adjuntos como datos. Es defensivo porque el
 *correo viene de fuera: recorre un número y una profundidad máximos de partes, limita el texto, no ejecuta ni renderiza nada y, si
 *solo hay HTML, lo convierte a texto. Los adjuntos se sirven después desde el mismo .eml con {@link #adjunto}, en el mismo orden
 */
public final class AnalizadorMime {
  static final int TEXTO_MAXIMO = 100_000;
  static final int ADJUNTO_MAXIMO = 25 * 1024 * 1024;
  private static final int PARTES_MAXIMAS = 200;
  private static final int PROFUNDIDAD_MAXIMA = 6;
  private static final Session SESION = Session.getInstance(new Properties());
  private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]");
  private static final Pattern NOMBRE_NO_SEGURO = Pattern.compile("[\\p{Cntrl}\"<>:|?*]");
  private static final Pattern PRECEDENCIA_MASIVA = Pattern.compile("(?i)\\b(bulk|list|junk)\\b");
  private static final Pattern PROGRAMA_MASIVO = Pattern.compile("(?i)mailchimp|sendgrid|sendinblue|brevo|mailgun|mailerlite|mailjet|constant ?contact|getresponse|campaign|amazon ses|benchmark|mass ?mail|newsletter");
  private static final Pattern CABECERA_MASIVA = Pattern.compile("(?i)^(x-mailgun-|x-mailchimp|x-mc-|x-sg-|x-sendgrid|x-mailjet|x-campaign|x-sib-|x-ses-|x-csa-complaints|feedback-id$)");
  private static final Pattern TIPO_SEGURO = Pattern.compile("[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*");

  private AnalizadorMime() {
  }

  /**contenido de un adjunto y el nombre y tipo con los que se descarga*/
  public record AdjuntoContenido(String nombre, String tipo, byte[] contenido) {
  }

  /**`respaldoFecha` se usa si el correo no trae fecha o trae una imposible (más de un día en el futuro)*/
  public static MensajeLeido leer(byte[] eml, Instant respaldoFecha) {
    try {
      MimeMessage mensaje = new MimeMessage(SESION, new ByteArrayInputStream(eml));
      Partes partes = recorrer(mensaje);
      String texto = partes.planos.stream().map(AnalizadorMime::contenidoTexto).filter(t -> !t.isBlank()).reduce((a, b) -> a + "\n\n" + b).orElse("");
      if (texto.isBlank()) texto = partes.html.stream().map(p -> ConvertidorHtml.aTexto(contenidoTexto(p))).filter(t -> !t.isBlank()).reduce((a, b) -> a + "\n\n" + b).orElse("");
      texto = limpiar(texto);
      boolean truncado = texto.length() > TEXTO_MAXIMO;
      if (truncado) texto = texto.substring(0, TEXTO_MAXIMO);
      InternetAddress de = remitente(mensaje);
      List<Adjunto> adjuntos = new ArrayList<>();
      for (int i = 0; i < partes.adjuntos.size(); i++) {
        Part p = partes.adjuntos.get(i);
        adjuntos.add(new Adjunto(nombreSeguro(p, i), tipoSeguro(p), tamano(p)));
      }
      return new MensajeLeido(recortar(mensaje.getMessageID(), 500), recortar(mensaje.getHeader("In-Reply-To", " "), 500),
          recortar(mensaje.getHeader("References", " "), 4000), de == null || de.getAddress() == null ? "desconocido" : de.getAddress().toLowerCase(Locale.ROOT),
          de == null ? null : recortar(de.getPersonal(), 255), direcciones(mensaje.getRecipients(Message.RecipientType.TO)),
          direcciones(mensaje.getRecipients(Message.RecipientType.CC)), recortar(limpiar(valor(mensaje.getSubject())), 998),
          fecha(mensaje, respaldoFecha), texto, truncado, adjuntos, esMasivo(mensaje));
    } catch (MessagingException | IOException e) {
      throw new IllegalArgumentException("El correo no se pudo leer (" + e.getClass().getSimpleName() + ")", e);
    }
  }

  /**solo el indicador de correo masivo de un correo ya guardado (para reclasificar sin volver a leer todo el mensaje)*/
  public static boolean esMasivo(byte[] eml) {
    try {
      return esMasivo(new MimeMessage(SESION, new ByteArrayInputStream(eml)));
    } catch (MessagingException e) {
      return false;
    }
  }

  /**el adjunto número `indice` (el mismo orden que en {@link #leer}), con su nombre ya saneado*/
  public static AdjuntoContenido adjunto(byte[] eml, int indice) {
    try {
      Partes partes = recorrer(new MimeMessage(SESION, new ByteArrayInputStream(eml)));
      if (indice < 0 || indice >= partes.adjuntos.size()) throw new IllegalArgumentException("No existe el adjunto " + indice);
      Part p = partes.adjuntos.get(indice);
      try (InputStream in = p.getInputStream()) {
        byte[] bytes = in.readNBytes(ADJUNTO_MAXIMO + 1);
        if (bytes.length > ADJUNTO_MAXIMO) {
          throw new ReglaNegocioException("ADJUNTO_DEMASIADO_GRANDE", "El adjunto supera los " + ADJUNTO_MAXIMO / 1024 / 1024 + " MB: descarga el correo original.");
        }
        return new AdjuntoContenido(nombreSeguro(p, indice), tipoSeguro(p), bytes);
      }
    } catch (MessagingException | IOException e) {
      throw new IllegalArgumentException("El adjunto no se pudo leer (" + e.getClass().getSimpleName() + ")", e);
    }
  }

  /**
   *envío masivo (boletines, publicidad, campañas) según sus cabeceras: lista de baja (List-Unsubscribe), lista de correo (List-Id),
   *Precedence bulk/list/junk, o las huellas de las plataformas de envío masivo. Es una señal, no un veredicto: también los sistemas
   *legítimos (alertas automáticas) mandan así, y por eso el clasificador la pesa junto con las demás
   */
  private static boolean esMasivo(MimeMessage mensaje) {
    try {
      if (mensaje.getHeader("List-Unsubscribe") != null || mensaje.getHeader("List-Id") != null) return true;
      String precedencia = mensaje.getHeader("Precedence", null);
      if (precedencia != null && PRECEDENCIA_MASIVA.matcher(precedencia).find()) return true;
      String programa = mensaje.getHeader("X-Mailer", null);
      if (programa != null && PROGRAMA_MASIVO.matcher(programa).find()) return true;
      var cabeceras = mensaje.getAllHeaders();
      while (cabeceras.hasMoreElements()) {
        if (CABECERA_MASIVA.matcher(cabeceras.nextElement().getName()).find()) return true;
      }
      return false;
    } catch (MessagingException e) {
      return false;
    }
  }

  private record Partes(List<Part> planos, List<Part> html, List<Part> adjuntos) {
  }

  private static Partes recorrer(MimeMessage mensaje) throws MessagingException, IOException {
    Partes partes = new Partes(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    visitar(mensaje, partes, 0, new int[] {0});
    return partes;
  }

  private static void visitar(Part parte, Partes partes, int profundidad, int[] contador) throws MessagingException, IOException {
    if (++contador[0] > PARTES_MAXIMAS || profundidad > PROFUNDIDAD_MAXIMA) return;
    if (parte.isMimeType("multipart/*")) {
      Multipart multi = (Multipart) parte.getContent();
      for (int i = 0; i < multi.getCount(); i++) visitar(multi.getBodyPart(i), partes, profundidad + 1, contador);
    } else if (esAdjunto(parte)) {
      partes.adjuntos.add(parte);
    } else if (parte.isMimeType("text/plain")) {
      partes.planos.add(parte);
    } else if (parte.isMimeType("text/html")) {
      partes.html.add(parte);
    } else {
      partes.adjuntos.add(parte);
    }
  }

  private static boolean esAdjunto(Part parte) throws MessagingException {
    return Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition()) || parte.getFileName() != null || parte.isMimeType("message/rfc822")
        || !parte.isMimeType("text/*");
  }

  /**el texto de una parte; si su charset no se conoce, se lee como Latin-1 en vez de perder el correo*/
  private static String contenidoTexto(Part parte) {
    try {
      Object contenido = parte.getContent();
      if (contenido instanceof String s) return s;
    } catch (IOException | MessagingException e) {
      // sigue con la lectura directa
    }
    try (InputStream in = parte.getInputStream()) {
      return new String(in.readNBytes(TEXTO_MAXIMO * 4), StandardCharsets.ISO_8859_1);
    } catch (IOException | MessagingException e) {
      return "";
    }
  }

  private static InternetAddress remitente(MimeMessage mensaje) {
    try {
      Address[] de = mensaje.getFrom();
      return de != null && de.length > 0 && de[0] instanceof InternetAddress a ? a : null;
    } catch (MessagingException e) {
      return null;
    }
  }

  private static String direcciones(Address[] direcciones) {
    if (direcciones == null) return null;
    List<String> texto = new ArrayList<>();
    for (Address a : direcciones) texto.add(a instanceof InternetAddress i ? i.toUnicodeString() : a.toString());
    return recortar(String.join(", ", texto), 2000);
  }

  private static Instant fecha(MimeMessage mensaje, Instant respaldo) throws MessagingException {
    java.util.Date enviada = mensaje.getSentDate();
    if (enviada == null) return respaldo;
    Instant f = enviada.toInstant();
    return f.isAfter(respaldo.plus(Duration.ofDays(1))) || f.isBefore(Instant.parse("2000-01-01T00:00:00Z")) ? respaldo : f;
  }

  private static String nombreSeguro(Part parte, int indice) {
    String nombre = "";
    try {
      String crudo = parte.getFileName();
      if (crudo != null) nombre = MimeUtility.decodeText(crudo);
    } catch (MessagingException | IOException e) {
      // sin nombre legible
    }
    nombre = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
    nombre = NOMBRE_NO_SEGURO.matcher(nombre).replaceAll("").strip();
    if (nombre.length() > 150) nombre = nombre.substring(nombre.length() - 150);
    return nombre.isEmpty() ? "adjunto-" + (indice + 1) : nombre;
  }

  private static String tipoSeguro(Part parte) {
    try {
      String tipo = new ContentType(parte.getContentType()).getBaseType().toLowerCase(Locale.ROOT);
      return TIPO_SEGURO.matcher(tipo).matches() ? tipo : "application/octet-stream";
    } catch (MessagingException e) {
      return "application/octet-stream";
    }
  }

  private static long tamano(Part parte) {
    try (InputStream in = parte.getInputStream()) {
      long n = 0;
      byte[] b = new byte[8192];
      for (int leidos; (leidos = in.read(b)) >= 0 && n <= ADJUNTO_MAXIMO * 4L; ) n += leidos;
      return n;
    } catch (IOException | MessagingException e) {
      return 0;
    }
  }

  private static String limpiar(String texto) {
    return CONTROL.matcher(texto.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("");
  }

  private static String valor(String s) { return s == null ? "" : s; }

  private static String recortar(String s, int maximo) {
    if (s == null) return null;
    String t = s.strip();
    return t.length() > maximo ? t.substring(0, maximo) : t;
  }
}
