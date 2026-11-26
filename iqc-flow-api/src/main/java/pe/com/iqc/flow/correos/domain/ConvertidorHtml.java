package pe.com.iqc.flow.correos.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *de HTML a texto para los correos que solo traen HTML: quita scripts, estilos y etiquetas, conserva los saltos de párrafo y
 *decodifica las entidades comunes. El HTML original no se guarda ni se muestra nunca: es la defensa contra scripts, rastreadores
 *e imágenes remotas
 */
public final class ConvertidorHtml {
  private static final Pattern BLOQUES_INERTES = Pattern.compile("(?is)<(script|style|head|title)\\b.*?</\\1\\s*>");
  private static final Pattern COMENTARIOS = Pattern.compile("(?s)<!--.*?-->");
  private static final Pattern SALTO = Pattern.compile("(?i)<\\s*(br|/p|/div|/tr|/h[1-6]|/li|/table|/ul|/ol|/blockquote)\\b[^>]*>");
  private static final Pattern ITEM = Pattern.compile("(?i)<\\s*li\\b[^>]*>");
  private static final Pattern CELDA = Pattern.compile("(?i)<\\s*/t[dh]\\s*>");
  private static final Pattern ETIQUETA = Pattern.compile("(?s)<[^>]*>");
  private static final Pattern ENTIDAD = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");

  private ConvertidorHtml() {
  }

  public static String aTexto(String html) {
    if (html == null || html.isBlank()) return "";
    String t = BLOQUES_INERTES.matcher(html).replaceAll(" ");
    t = COMENTARIOS.matcher(t).replaceAll(" ");
    t = SALTO.matcher(t).replaceAll("\n");
    t = ITEM.matcher(t).replaceAll("\n- ");
    t = CELDA.matcher(t).replaceAll("\t");
    t = ETIQUETA.matcher(t).replaceAll("");
    t = decodificar(t);
    return t.replaceAll("[ \\u00A0]+", " ").replaceAll(" ?\t ?", "\t").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n").strip();
  }

  private static String decodificar(String texto) {
    Matcher m = ENTIDAD.matcher(texto);
    StringBuilder salida = new StringBuilder();
    while (m.find()) m.appendReplacement(salida, Matcher.quoteReplacement(entidad(m.group(1), m.group())));
    m.appendTail(salida);
    return salida.toString();
  }

  private static String entidad(String nombre, String original) {
    if (nombre.startsWith("#")) {
      try {
        int codigo = nombre.charAt(1) == 'x' || nombre.charAt(1) == 'X' ? Integer.parseInt(nombre.substring(2), 16) : Integer.parseInt(nombre.substring(1));
        return Character.isValidCodePoint(codigo) && codigo > 0 ? new String(Character.toChars(codigo)) : original;
      } catch (NumberFormatException e) {
        return original;
      }
    }
    return switch (nombre.toLowerCase()) {
      case "amp" -> "&";
      case "lt" -> "<";
      case "gt" -> ">";
      case "quot" -> "\"";
      case "apos" -> "'";
      case "nbsp" -> " ";
      case "aacute" -> "á"; case "eacute" -> "é"; case "iacute" -> "í"; case "oacute" -> "ó"; case "uacute" -> "ú";
      case "ntilde" -> nombre.equals("Ntilde") ? "Ñ" : "ñ";
      case "iexcl" -> "¡"; case "iquest" -> "¿"; case "ndash", "mdash" -> "-"; case "hellip" -> "...";
      default -> original;
    };
  }
}
