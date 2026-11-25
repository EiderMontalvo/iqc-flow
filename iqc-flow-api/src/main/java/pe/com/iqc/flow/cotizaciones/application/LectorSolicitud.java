package pe.com.iqc.flow.cotizaciones.application;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *saca el texto de la solicitud del cliente: PDF, Word (.docx), correo guardado (.eml de Gmail u Outlook web, .msg de
 *Outlook) o texto plano. El tipo se reconoce por los primeros bytes, no por la extensión. De un correo se toman
 *remitente, fecha, asunto, cuerpo y el texto de sus adjuntos PDF, Word o de texto (y de un correo reenviado como
 *adjunto); el remitente va en una línea «Correo de: Nombre <correo>» que el análisis usa como contacto
 */
final class LectorSolicitud {
  static final int PAGINAS_MAXIMAS = 20;
  static final int CARACTERES_MAXIMOS = 60_000;
  private static final int ADJUNTOS_MAXIMOS = 6;
  private static final int PROFUNDIDAD_MAXIMA = 2;
  private static final Pattern CABECERA = Pattern.compile(
      "(?im)^(received|return-path|from|to|subject|date|mime-version|message-id|delivered-to|content-type|x-[\\w-]+)\\s*:");
  private static final Pattern CORREO_EN_CABECERA = Pattern.compile("(?im)^From:\\s*(.+)$");
  private static final TimeZone LIMA = TimeZone.getTimeZone("America/Lima");

  private LectorSolicitud() {
  }

  static boolean esPdf(byte[] b) { return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F'; }

  private static boolean esZip(byte[] b) { return b.length > 3 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4; }

  /** Documento OLE2 de Office: así se guardan los correos de Outlook (.msg) */
  private static boolean esOle2(byte[] b) {
    return b.length > 8 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0;
  }

  /** Un .eml empieza con cabeceras («From:», «Received:», …) antes de la primera línea en blanco */
  static boolean esCorreo(byte[] b) {
    String inicio = new String(b, 0, Math.min(b.length, 8192), StandardCharsets.ISO_8859_1);
    int fin = inicio.indexOf("\n\n") >= 0 ? inicio.indexOf("\n\n") : inicio.indexOf("\r\n\r\n");
    String cabeceras = fin > 0 ? inicio.substring(0, fin) : inicio;
    if (!CABECERA.matcher(cabeceras.lines().findFirst().orElse("")).find()) return false;
    Matcher m = CABECERA.matcher(cabeceras);
    int n = 0;
    while (m.find()) n++;
    return n >= 3;
  }

  static String texto(byte[] archivo) {
    if (archivo == null || archivo.length == 0) return "";
    return recortar(leer(archivo, 0));
  }

  private static String leer(byte[] archivo, int profundidad) {
    if (esPdf(archivo)) return pdf(archivo);
    if (esZip(archivo)) return word(archivo);
    if (esOle2(archivo)) return outlook(archivo, profundidad);
    if (esCorreo(archivo)) return eml(archivo, profundidad);
    String texto = new String(archivo, StandardCharsets.UTF_8);
    if (texto.chars().limit(2000).filter(c -> c == 0xFFFD || c == 0).count() > 20) {
      throw new ReglaNegocioException("SOLICITUD_FORMATO", "Formato no admitido: sube un PDF, un Word (.docx), el correo guardado (.eml o .msg) o pega el texto.");
    }
    return texto;
  }

  private static String pdf(byte[] archivo) {
    try (PDDocument d = Loader.loadPDF(archivo)) {
      PDFTextStripper lector = new PDFTextStripper();
      lector.setSortByPosition(true);
      lector.setEndPage(Math.min(d.getNumberOfPages(), PAGINAS_MAXIMAS));
      return lector.getText(d);
    } catch (IOException e) {
      throw new ReglaNegocioException("SOLICITUD_ILEGIBLE", "No se pudo leer el PDF: puede estar dañado o protegido.");
    }
  }

  private static String word(byte[] archivo) {
    try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(archivo)); XWPFWordExtractor e = new XWPFWordExtractor(d)) {
      return e.getText();
    } catch (IOException | RuntimeException e) {
      throw new ReglaNegocioException("SOLICITUD_ILEGIBLE", "No se pudo leer el archivo: solo se aceptan PDF, Word (.docx), correos (.eml o .msg) o texto.");
    }
  }

  // ---------------------------------------------------------------- .eml

  private static String eml(byte[] archivo, int profundidad) {
    try {
      MimeMessage m = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(archivo));
      return mensaje(m, profundidad);
    } catch (MessagingException | IOException e) {
      throw new ReglaNegocioException("SOLICITUD_ILEGIBLE", "No se pudo leer el correo guardado: puede estar incompleto.");
    }
  }

  private static String mensaje(MimeMessage m, int profundidad) throws MessagingException, IOException {
    StringBuilder cuerpo = new StringBuilder();
    List<String> adjuntos = new ArrayList<>();
    recorrer(m, cuerpo, adjuntos, profundidad);
    Address[] de = m.getFrom();
    String remitente = de == null || de.length == 0 ? null : de[0] instanceof InternetAddress d ? persona(d.getPersonal(), d.getAddress()) : de[0].toString();
    return encabezado(remitente, m.getSentDate(), m.getSubject()) + cuerpo.toString().trim() + String.join("", adjuntos);
  }

  /** Cuerpo: el texto plano si lo hay (si no, el HTML sin etiquetas); adjuntos: su texto, hasta {@link #ADJUNTOS_MAXIMOS} */
  private static void recorrer(Part p, StringBuilder cuerpo, List<String> adjuntos, int profundidad) throws MessagingException, IOException {
    String nombre = nombreArchivo(p);
    if (p.isMimeType("multipart/alternative")) {
      Multipart mp = (Multipart) p.getContent();
      Part elegido = null;
      for (int i = 0; i < mp.getCount(); i++) {
        BodyPart b = mp.getBodyPart(i);
        if (b.isMimeType("text/plain")) { elegido = b; break; }
        if (elegido == null || b.isMimeType("multipart/*")) elegido = b;
      }
      if (elegido != null) recorrer(elegido, cuerpo, adjuntos, profundidad);
    } else if (p.isMimeType("multipart/*")) {
      Multipart mp = (Multipart) p.getContent();
      for (int i = 0; i < mp.getCount(); i++) recorrer(mp.getBodyPart(i), cuerpo, adjuntos, profundidad);
    } else if (p.isMimeType("message/rfc822")) {
      if (profundidad < PROFUNDIDAD_MAXIMA && adjuntos.size() < ADJUNTOS_MAXIMOS && p.getContent() instanceof MimeMessage reenviado) {
        adjuntos.add("\n\nCorreo adjunto:\n" + mensaje(reenviado, profundidad + 1));
      }
    } else if (nombre == null && !Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition()) && p.isMimeType("text/plain")) {
      cuerpo.append(p.getContent()).append("\n");
    } else if (nombre == null && !Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition()) && p.isMimeType("text/html")) {
      cuerpo.append(sinHtml(String.valueOf(p.getContent()))).append("\n");
    } else if (nombre != null && adjuntos.size() < ADJUNTOS_MAXIMOS && !p.isMimeType("image/*")) {
      byte[] datos;
      try (var in = p.getInputStream()) { datos = in.readNBytes(15 * 1024 * 1024); }
      adjunto(nombre, datos, profundidad).ifPresent(adjuntos::add);
    }
  }

  private static String nombreArchivo(Part p) throws MessagingException {
    String n = p.getFileName();
    if (n == null) return null;
    try { return MimeUtility.decodeText(n); } catch (IOException e) { return n; }
  }

  // ---------------------------------------------------------------- .msg

  private static String outlook(byte[] archivo, int profundidad) {
    try (MAPIMessage m = new MAPIMessage(new ByteArrayInputStream(archivo))) {
      m.setReturnNullOnMissingChunk(true);
      String nombre = m.getDisplayFrom();
      String correo = correoDeCabeceras(m);
      Calendar fecha = null;
      try { fecha = m.getMessageDate(); } catch (ChunkNotFoundException e) { /* sin fecha */ }
      String cuerpo = m.getTextBody();
      if (cuerpo == null || cuerpo.isBlank()) {
        String html = m.getHtmlBody();
        cuerpo = html == null ? "" : sinHtml(html);
      }
      List<String> adjuntos = new ArrayList<>();
      for (AttachmentChunks a : m.getAttachmentFiles()) {
        if (adjuntos.size() >= ADJUNTOS_MAXIMOS) break;
        if (a.getEmbeddedMessage() != null && profundidad < PROFUNDIDAD_MAXIMA) {
          adjuntos.add("\n\nCorreo adjunto:\n" + cuerpoOutlook(a.getEmbeddedMessage()));
        } else if (a.getAttachData() != null) {
          String n = a.getAttachLongFileName() != null ? a.getAttachLongFileName().getValue()
              : a.getAttachFileName() != null ? a.getAttachFileName().getValue() : "adjunto";
          adjunto(n, a.getAttachData().getValue(), profundidad).ifPresent(adjuntos::add);
        }
      }
      return encabezado(persona(nombre, correo), fecha == null ? null : fecha.getTime(), m.getSubject()) + cuerpo.trim() + String.join("", adjuntos);
    } catch (IOException | RuntimeException | ChunkNotFoundException e) {
      throw new ReglaNegocioException("SOLICITUD_ILEGIBLE", "No se pudo leer el correo de Outlook (.msg): puede estar dañado.");
    }
  }

  private static String cuerpoOutlook(MAPIMessage m) throws ChunkNotFoundException {
    m.setReturnNullOnMissingChunk(true);
    String cuerpo = m.getTextBody() != null ? m.getTextBody() : m.getHtmlBody() != null ? sinHtml(m.getHtmlBody()) : "";
    return encabezado(persona(m.getDisplayFrom(), correoDeCabeceras(m)), null, m.getSubject()) + cuerpo.trim();
  }

  /** El .msg guarda el nombre del remitente aparte; su correo solo viene en las cabeceras de Internet, si las trae */
  private static String correoDeCabeceras(MAPIMessage m) {
    try {
      String[] cabeceras = m.getHeaders();
      if (cabeceras == null) return null;
      for (String c : cabeceras) {
        Matcher x = CORREO_EN_CABECERA.matcher(c);
        if (x.find()) {
          InternetAddress[] a = InternetAddress.parseHeader(x.group(1).trim(), false);
          if (a.length > 0) return a[0].getAddress();
        }
      }
    } catch (ChunkNotFoundException | jakarta.mail.internet.AddressException e) {
      return null;
    }
    return null;
  }

  // ---------------------------------------------------------------- comunes

  private static java.util.Optional<String> adjunto(String nombre, byte[] datos, int profundidad) {
    String bajo = nombre.toLowerCase(Locale.ROOT);
    boolean util = esPdf(datos) || esZip(datos) && bajo.endsWith(".docx") || bajo.endsWith(".txt")
        || (bajo.endsWith(".eml") || bajo.endsWith(".msg")) && profundidad < PROFUNDIDAD_MAXIMA;
    if (!util) return java.util.Optional.empty();
    try {
      String t = leer(datos, profundidad + 1).trim();
      return t.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of("\n\nAdjunto «" + nombre + "»:\n" + t);
    } catch (ReglaNegocioException e) {
      return java.util.Optional.of("\n\nAdjunto «" + nombre + "»: no se pudo leer.");
    }
  }

  private static String encabezado(String remitente, Date fecha, String asunto) {
    StringBuilder s = new StringBuilder();
    if (remitente != null && !remitente.isBlank()) s.append("Correo de: ").append(remitente.trim()).append('\n');
    if (fecha != null) {
      SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT);
      f.setTimeZone(LIMA);
      s.append("Fecha: ").append(f.format(fecha)).append('\n');
    }
    if (asunto != null && !asunto.isBlank()) s.append("Asunto: ").append(asunto.trim()).append('\n');
    return s.isEmpty() ? "" : s.append('\n').toString();
  }

  private static String persona(String nombre, String correo) {
    String n = nombre == null ? "" : nombre.replace("\"", "").trim();
    if (correo == null || correo.isBlank()) return n.isEmpty() ? null : n;
    return n.isEmpty() || n.equalsIgnoreCase(correo) ? "<" + correo + ">" : n + " <" + correo + ">";
  }

  static String sinHtml(String html) {
    return html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ")
        .replaceAll("(?i)<br\\s*/?>|</(p|div|li|tr|h[1-6])>", "\n")
        .replaceAll("<[^>]+>", " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replaceAll("&aacute;", "á").replaceAll("&eacute;", "é").replaceAll("&iacute;", "í").replaceAll("&oacute;", "ó").replaceAll("&uacute;", "ú")
        .replaceAll("&ntilde;", "ñ").replaceAll("&Ntilde;", "Ñ");
  }

  private static String recortar(String texto) {
    String t = texto.replace("\r", "").replaceAll("[ \\t\\u00A0]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    return t.length() > CARACTERES_MAXIMOS ? t.substring(0, CARACTERES_MAXIMOS) : t;
  }
}
