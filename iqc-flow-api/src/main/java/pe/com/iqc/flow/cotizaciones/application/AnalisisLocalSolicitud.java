package pe.com.iqc.flow.cotizaciones.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pe.com.iqc.flow.cotizaciones.domain.TipoServicioCotizacion;

/**
 *lectura de la solicitud sin IA: reglas sobre el texto para RUC, correo, teléfono, normas ISO, trabajadores, sedes y
 *tipo de servicio. Es la base siempre disponible; la IA, si está, agrega resumen y puntos clave y completa lo que falte
 */
final class AnalisisLocalSolicitud {
  private static final Pattern RUC = Pattern.compile("(?<!\\d)((?:10|15|16|17|20)\\d{9})(?!\\d)");
  private static final Pattern CORREO = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern TELEFONO = Pattern.compile("(?<!\\d)(?:\\+?51[\\s-]?)?(9\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{3})(?!\\d)");
  private static final Pattern NORMA = Pattern.compile("ISO\\s*(?:/\\s*IEC\\s*)?(\\d{4,5})(?:\\s*[:\\-]\\s*(\\d{4}))?", Pattern.CASE_INSENSITIVE);
  private static final Pattern TRABAJADORES = Pattern.compile("(\\d{1,6})\\s*(?:trabajadores|colaboradores|empleados|personas|personal)", Pattern.CASE_INSENSITIVE);
  /** «Correo de:» lo escribe el lector de correos; «De:/From:» viene en los reenvíos. Se toma el último: el remitente original */
  private static final Pattern REMITENTE = Pattern.compile("(?im)^(?:Correo de|De|From)\\s*:\\s*\"?([^\"<\\n]*?)\"?\\s*<([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})>");
  private static final String NOMBRE_PROPIO = "\\p{Lu}[\\p{L}'.-]+(?:[ \\t]+(?:de[ \\t]+(?:la[ \\t]+|los[ \\t]+)?)?\\p{Lu}[\\p{L}'.-]+){1,3}";
  private static final Pattern CONTACTO = Pattern.compile("(?i:contacto|atenci[oó]n|responsable)\\s*:\\s*(" + NOMBRE_PROPIO + ")");
  private static final Pattern FIRMA = Pattern.compile("(?i:atentamente|saludos cordiales|cordialmente|saludos|gracias)[,.!]?\\s*\\n+\\s*(" + NOMBRE_PROPIO + ")\\s*(?:\\n|$)");
  private static final Pattern SEDES = Pattern.compile("(\\d{1,3})\\s*(?:sedes|locales|oficinas|plantas|sucursales)", Pattern.CASE_INSENSITIVE);

  record NormaLeida(String numero, String anio) {
  }

  record Resultado(String ruc, String contactoNombre, String correo, String telefono, List<NormaLeida> normas, Integer trabajadores, Integer sedes,
      TipoServicioCotizacion tipoServicio) {
  }

  private AnalisisLocalSolicitud() {
  }

  static Resultado analizar(String texto) {
    String t = texto == null ? "" : texto;
    String ruc = primero(RUC, t, 1);
    String nombre = null, correo = null;
    Matcher r = REMITENTE.matcher(t);
    while (r.find()) {
      nombre = r.group(1).isBlank() || r.group(1).contains("@") ? null : r.group(1).trim();
      correo = r.group(2);
    }
    if (correo == null) correo = primero(CORREO, t, 0);
    if (nombre == null) nombre = primero(CONTACTO, t, 1);
    if (nombre == null) nombre = primero(FIRMA, t, 1);
    String telefono = primero(TELEFONO, t, 1);
    Set<String> vistas = new LinkedHashSet<>();
    List<NormaLeida> normas = new ArrayList<>();
    Matcher m = NORMA.matcher(t);
    while (m.find()) if (vistas.add(m.group(1))) normas.add(new NormaLeida(m.group(1), m.group(2)));
    Integer trabajadores = entero(primero(TRABAJADORES, t, 1));
    Integer sedes = entero(primero(SEDES, t, 1));
    String bajo = t.toLowerCase(Locale.ROOT);
    TipoServicioCotizacion tipo = bajo.contains("recertific") || bajo.contains("renovaci") ? TipoServicioCotizacion.RECERTIFICACION
        : bajo.contains("ampliaci") ? TipoServicioCotizacion.AMPLIACION
        : bajo.contains("vigilancia") || bajo.contains("seguimiento anual") ? TipoServicioCotizacion.VIGILANCIA
        : TipoServicioCotizacion.CERTIFICACION_INICIAL;
    return new Resultado(ruc, nombre, correo, telefono == null ? null : telefono.replaceAll("[\\s-]", ""), normas, trabajadores, sedes, tipo);
  }

  private static String primero(Pattern p, String t, int grupo) {
    Matcher m = p.matcher(t);
    return m.find() ? m.group(grupo) : null;
  }

  private static Integer entero(String s) {
    try { return s == null ? null : Integer.valueOf(s); } catch (NumberFormatException e) { return null; }
  }
}
