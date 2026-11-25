package pe.com.iqc.flow.certificaciones.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *lee los campos de un certificado a partir del texto de su PDF (extraído en orden de posición). Reconoce los formatos
 *en uso en IQC: INACAL/SAE (DDC-FRM-015), IQC Global de India (QF-12) y certificados personales. Es una propuesta: la
 *persona revisa y confirma antes de registrar (las fuentes de algunos PDF confunden, p. ej., «6» con «S»)
 */
public final class AnalizadorTextoCertificado {
  private AnalizadorTextoCertificado() {
  }

  public enum Formato { INACAL_SAE, IQC_GLOBAL, PERSONAL, DESCONOCIDO }

  public record Empresarial(String codigo, String esquema, String titular, String direccion, String normaTexto,
      String normaNombre, String edicion, String alcance, Integer ciclo, LocalDate fechaOtorgamiento,
      LocalDate fechaEmision, LocalDate limiteVigilancia1, LocalDate limiteVigilancia2, LocalDate fechaExpiracion,
      String subdivision, String clase) {
  }

  public record Personal(String codigo, String nombres, String tipoDocumento, String numeroDocumento, String curso,
      String normaReferencia, String normaAnio, String subtitulo, String descripcion, BigDecimal duracionHoras,
      String modalidad, LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaEmision, String entrenador) {
  }

  public record Resultado(Formato formato, Empresarial empresarial, Personal personal, List<String> advertencias) {
  }

  private static final Map<String, Integer> MESES = Map.ofEntries(Map.entry("enero", 1), Map.entry("febrero", 2),
      Map.entry("marzo", 3), Map.entry("abril", 4), Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7),
      Map.entry("agosto", 8), Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
      Map.entry("noviembre", 11), Map.entry("diciembre", 12));
  private static final Pattern NORMA = Pattern.compile("(ISO(?:\\s*/\\s*IEC)?)\\s*(\\d{4,5})\\s*:\\s*(\\d{4})");
  private static final Pattern FECHA_LARGA = Pattern.compile(
      "(\\d{1,2})\\s+de\\s+([a-záéíóú]+)\\s+de\\s*(?:l\\s*)?(\\d{4})", Pattern.CASE_INSENSITIVE);
  private static final Pattern FECHA_PUNTOS = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{4})");
  /**India: acreditación JAS-ANZ (JAS-C…) o IAS (IAS-C…), p. ej. PER/IMS-QEO/JAS-C0039/RC1/0406, PER/ABMS/IAS-C0115/0119*/
  private static final Pattern CODIGO_INDIA = Pattern.compile("(PER/[A-Z0-9–-]+(?:/[A-Z0-9–-]+)*/(?:JAS|IAS)[-–]C\\d+(?:/[A-Z0-9]+)*/\\d+)");
  /**INACAL/SAE: formato actual PE-INA-… / PE-SAE-… y formato antiguo sin guiones (PE + 11 dígitos)*/
  private static final Pattern CODIGO_INACAL_SAE = Pattern.compile("Certificado\\s*No\\.?\\s*:?\\s*(PE[-–][A-Z]{3}[-–]\\d{11}[A-Z]?|PE\\d{11}[A-Z]?)\\b",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern DIRECCION = Pattern.compile(
      "^\\s*(AV\\.?|AV |JR\\.?|JR |CAL\\.?|CALLE|MZA?\\.?|PSJE|CARRETERA|CAR\\.|PJ\\.?|URB\\.?|PROLONG|KM\\b|LOTE|NRO)",
      Pattern.CASE_INSENSITIVE);

  public static Resultado analizar(String textoPdf) {
    String texto = unirNumerosPartidos(textoPdf == null ? "" : textoPdf.replace(' ', ' '));
    List<String> lineas = texto.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    String plano = compacto(texto);
    List<String> avisos = new ArrayList<>();
    if (plano.contains("codigodeverificacion")) return new Resultado(Formato.PERSONAL, null, personal(lineas, texto, avisos), avisos);
    if (plano.contains("qf-12") || plano.contains("qf-15") || CODIGO_INDIA.matcher(texto).find()) {
      return new Resultado(Formato.IQC_GLOBAL, india(lineas, texto, avisos), null, avisos);
    }
    if (plano.contains("ddc-frm-015") || plano.contains("certificadono.:")) {
      return new Resultado(Formato.INACAL_SAE, inacalSae(lineas, texto, avisos), null, avisos);
    }
    avisos.add("No se reconoció el formato del PDF: completa los datos a mano o usa la IA si está habilitada.");
    return new Resultado(Formato.DESCONOCIDO, null, null, avisos);
  }

  // INACAL / SAE (DDC-FRM-015)

  private static Empresarial inacalSae(List<String> l, String texto, List<String> avisos) {
    Matcher mc = CODIGO_INACAL_SAE.matcher(texto);
    String codigo = mc.find() ? mc.group(1) : null;
    String esquema;
    if (codigo != null && codigo.replace('–', '-').startsWith("PE-SAE")) esquema = "SAE";
    else if (codigo != null && codigo.replace('–', '-').startsWith("PE-INA")) esquema = "INACAL";
    else esquema = plano(texto).contains("sae") && !plano(texto).contains("inacal") ? "SAE" : plano(texto).contains("inacal") ? "INACAL" : null;
    if (codigo != null && codigo.matches("PE\\d{11}[A-Z]?")) {
      if (esquema != null) {
        String actual = "PE-" + ("SAE".equals(esquema) ? "SAE" : "INA") + "-" + codigo.substring(2);
        avisos.add("El PDF imprime el código en el formato antiguo (" + codigo + "); se propone " + actual + ", el formato con el que se registra.");
        codigo = actual;
      } else {
        avisos.add("El PDF tiene el código en el formato antiguo (" + codigo + ") y no indica el esquema: revisa el código.");
      }
    }
    int inicio = indice(l, "enreconocimiento");
    List<String> titular = new ArrayList<>();
    List<String> direccion = new ArrayList<>();
    if (inicio >= 0) {
      for (int i = inicio + 1; i < l.size(); i++) {
        String linea = l.get(i);
        if (compacto(linea).startsWith("elcualseencuentra")) break;
        if (direccion.isEmpty() && !DIRECCION.matcher(linea).find()) titular.add(linea);
        else direccion.add(linea);
      }
    }
    String alcance = entre(l, "alcance:", "certificadono.");
    Matcher norma = NORMA.matcher(texto);
    LocalDate[] porEtiqueta = fechasInacalSae(l);
    if (plano(texto).contains("fechaefectivadeampliacion")) {
      avisos.add("Es un certificado de ampliación: la fecha de ampliación se usa como fecha de emisión y el servicio es una ampliación.");
    } else if (plano(texto).contains("fechaefectivadereduccion")) {
      avisos.add("Es un certificado de reducción de alcance: la fecha de reducción se usa como fecha de emisión.");
    }
    List<LocalDate> fechas = new ArrayList<>();
    for (LocalDate f : porEtiqueta) if (f != null) fechas.add(f);
    Integer ciclo = entero(grupo(texto, "Ciclo de certificaci[oó]n\\s*Nro\\.?\\s*(\\d+)"));
    Empresarial e = new Empresarial(codigo, esquema, unir(titular), unir(direccion),
        norma.find() ? normaTexto(norma) : null, nombreNorma(texto),
        edicion(texto), alcance, ciclo, porEtiqueta[0], porEtiqueta[1], porEtiqueta[2], porEtiqueta[3], porEtiqueta[4], null, null);
    if (e.fechaOtorgamiento() != null && e.fechaEmision() != null && e.fechaEmision().isBefore(e.fechaOtorgamiento())) {
      avisos.add("En el PDF la fecha de emisión es anterior a la de otorgamiento: revisa ambas antes de registrar.");
    }
    if (fechas.size() < 5) avisos.add("Se esperaban 5 fechas (otorgamiento, emisión, dos vigilancias y expiración); se leyeron " + fechas.size() + ".");
    faltantes(avisos, Map.of("código", e.codigo() == null, "titular", e.titular() == null, "norma", e.normaTexto() == null,
        "alcance", e.alcance() == null));
    return e;
  }

  // IQC Global (India, QF-12)

  private static Empresarial india(List<String> l, String texto, List<String> avisos) {
    Matcher mc = CODIGO_INDIA.matcher(texto);
    String codigo = mc.find() ? mc.group(1) : null;
    int inicio = indice(l, "herebycertifies") + 1;
    int fin = indice(l, "hasidoevaluado");
    if (fin < 0) fin = Math.min(l.size(), inicio + 4);
    String titular = inicio < fin ? l.get(inicio) : null;
    String direccion = inicio + 1 < fin ? unir(l.subList(inicio + 1, fin)) : null;
    String alcance = entre(l, "alcance:", "subdivision");
    if (alcance != null && alcance.contains("Divisi")) alcance = alcance.substring(0, alcance.indexOf("Divisi")).strip();
    if (alcance == null || alcance.isEmpty()) alcance = entre(l, "alcance:", "division");
    // Cada fecha se asigna por su etiqueta (QF-12 y QF-15 las ordenan distinto); la del formulario («date …») no cuenta
    LocalDate inicial = null, emision = null, vigilancia1 = null, vigilancia2 = null, expiracion = null;
    String pendiente = "";
    for (String linea : l) {
      Matcher m = FECHA_PUNTOS.matcher(linea);
      int desde = 0;
      boolean hubo = false;
      while (m.find()) {
        hubo = true;
        String etiqueta = compacto(linea.substring(desde, m.start()));
        desde = m.end();
        // etiqueta en la línea anterior y la fecha sola en esta («Fecha de emisión actual» / «: 31.01.2023»)
        if (etiqueta.replace(":", "").length() < 4) etiqueta = pendiente;
        LocalDate f = fecha(m.group(3), m.group(2), m.group(1));
        if (f == null || etiqueta.endsWith("date") && !etiqueta.contains("issue") && !etiqueta.contains("expiry")) continue;
        if (etiqueta.contains("1era") || etiqueta.contains("primera") || etiqueta.contains("1st") || etiqueta.contains("first")) vigilancia1 = f;
        else if (etiqueta.contains("2da") || etiqueta.contains("segunda") || etiqueta.contains("2nd") || etiqueta.contains("second")) vigilancia2 = f;
        else if (etiqueta.contains("vencimiento") || etiqueta.contains("valido") || etiqueta.contains("expiry") || etiqueta.contains("validuntil")) expiracion = f;
        else if (etiqueta.contains("emisioninicial") || etiqueta.contains("initial") || etiqueta.contains("original")) inicial = f;
        else if (etiqueta.contains("emision") || etiqueta.contains("issue")) emision = f;
      }
      pendiente = compacto(hubo ? linea.substring(desde) : linea);
    }
    if (emision == null) emision = inicial;
    if (inicial == null) inicial = emision;
    List<LocalDate> fechas = new ArrayList<>();
    for (LocalDate f : new LocalDate[] {emision, vigilancia1, vigilancia2, expiracion}) if (f != null) fechas.add(f);
    Matcher norma = NORMA.matcher(texto);
    Empresarial e = new Empresarial(codigo, "IQC_GLOBAL", titular, direccion, norma.find() ? normaTexto(norma) : null,
        nombreNorma(texto), edicion(texto), alcance, null, inicial, emision, vigilancia1, vigilancia2, expiracion,
        limpio(grupo(texto, "(?:Sub)?divisi[oó]n\\s*:\\s*([0-9, ]+)")), limpio(grupo(texto, "Clase\\s*:\\s*([0-9, ]+)")));
    if (fechas.size() < 4) avisos.add("Se esperaban 4 fechas (emisión inicial, dos vigilancias y validez); se leyeron " + fechas.size() + ".");
    faltantes(avisos, Map.of("código", e.codigo() == null, "titular", e.titular() == null, "norma", e.normaTexto() == null));
    return e;
  }

  // Certificados personales

  private static Personal personal(List<String> l, String texto, List<String> avisos) {
    String codigo = grupo(texto, "C[oó]digo de verificaci[oó]n\\s*:\\s*(\\S+)");
    int pres = indice(l, "porlapresentesecertificaque");
    String nombres = pres >= 0 && pres + 1 < l.size() ? l.get(pres + 1) : null;
    Matcher doc = Pattern.compile("\\b(DNI|CE|C[ÉE]DULA|CC|PASAPORTE)\\b\\s*(?:N[°ºo.]*\\s*)?:?\\s*((?=[A-Z0-9-]*\\d)[A-Z0-9-]{5,15})\\b").matcher(texto);
    String tipo = null, numero = null;
    if (doc.find()) {
      tipo = Normalizer.normalize(doc.group(1).toUpperCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
      numero = doc.group(2).toUpperCase(Locale.ROOT);
    }
    int cert = indice(l, "certificado");
    String curso = cert >= 0 && cert + 1 < l.size() && !compacto(l.get(cert + 1)).startsWith("porlapresente") ? l.get(cert + 1) : null;
    Matcher norma = NORMA.matcher(texto);
    String normaReferencia = null, normaAnio = null, subtitulo = null, descripcion = null;
    if (norma.find()) {
      normaReferencia = (norma.group(1).replaceAll("\\s", "") + " " + norma.group(2)).replace("ISO/IEC", "ISO/IEC");
      normaAnio = norma.group(3);
      int linea = indiceDe(l, norma.group(0));
      if (linea >= 0 && linea + 1 < l.size()) subtitulo = sinMarcasLegales(l.get(linea + 1));
      if (linea >= 0 && linea + 2 < l.size() && !compacto(l.get(linea + 2)).startsWith("estecurso")) descripcion = l.get(linea + 2);
    }
    BigDecimal horas = null;
    String modalidad = null;
    Matcher dur = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*horas?\\s+(.*?)\\s+(\\d{1,2}\\s+(?:de\\s+[a-záéíóú]+\\s+)?al\\s+)",
        Pattern.CASE_INSENSITIVE).matcher(texto);
    if (dur.find()) {
      horas = new BigDecimal(dur.group(1).replace(',', '.'));
      modalidad = limpio(dur.group(2));
    } else {
      horas = decimal(grupo(texto, "(\\d+(?:[.,]\\d+)?)\\s*horas"));
    }
    LocalDate inicio = null, fin = null;
    Matcher rango = Pattern.compile("(\\d{1,2})(?:\\s+de\\s+([a-záéíóú]+))?\\s+al\\s+(\\d{1,2})\\s+de\\s+([a-záéíóú]+)\\s+de(?:l)?\\s+(\\d{4})",
        Pattern.CASE_INSENSITIVE).matcher(texto);
    if (rango.find()) {
      String mesInicio = rango.group(2) != null ? rango.group(2) : rango.group(4);
      inicio = fecha(rango.group(5), mes(mesInicio), rango.group(1));
      fin = fecha(rango.group(5), mes(rango.group(4)), rango.group(3));
    }
    LocalDate emision = null;
    Matcher em = Pattern.compile("Fecha de emisi[oó]n\\s*:\\s*" + FECHA_LARGA.pattern(), Pattern.CASE_INSENSITIVE).matcher(texto);
    if (em.find()) emision = fecha(em.group(3), mes(em.group(2)), em.group(1));
    Personal p = new Personal(codigo, nombres, tipo, numero, curso, normaReferencia, normaAnio, subtitulo, descripcion, horas,
        modalidad, inicio, fin, emision, limpio(grupo(texto, "Entrenador\\s*:\\s*([^\\n]+)")));
    faltantes(avisos, Map.of("código", p.codigo() == null, "nombre", p.nombres() == null, "documento", p.numeroDocumento() == null,
        "fecha de emisión", p.fechaEmision() == null));
    return p;
  }

  // Apoyo

  /**PDF que parten números («20 26») o etiquetas («Fec ha»): se unen los dígitos separados por un espacio*/
  static String unirNumerosPartidos(String texto) { return texto.replaceAll("(?<=\\d) (?=\\d{1,3}\\b)", ""); }

  /**minúsculas sin tildes ni espacios, para buscar etiquetas que el PDF parte en pedazos*/
  static String compacto(String texto) {
    return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }

  private static String plano(String texto) { return compacto(texto); }

  /**otorgamiento, emisión, vigilancia 1, vigilancia 2 y expiración, cada una por la etiqueta de su línea*/
  private static LocalDate[] fechasInacalSae(List<String> lineas) {
    LocalDate[] r = new LocalDate[5];
    for (String linea : lineas) {
      Matcher m = FECHA_LARGA.matcher(linea);
      if (!m.find()) continue;
      String etiqueta = compacto(linea.substring(0, m.start()));
      LocalDate f = fecha(m.group(3), mes(m.group(2)), m.group(1));
      if (f == null) continue;
      int i = etiqueta.contains("otorgamiento") ? 0 : etiqueta.contains("expiracion") || etiqueta.contains("vencimiento") ? 4
          : etiqueta.contains("seguimiento1") || etiqueta.contains("vigilancia1") ? 2
          : etiqueta.contains("seguimiento2") || etiqueta.contains("vigilancia2") ? 3
          : etiqueta.contains("emision") || etiqueta.contains("ampliacion") || etiqueta.contains("reduccion") ? 1 : -1;
      if (i >= 0 && r[i] == null) r[i] = f;
    }
    return r;
  }

  private static List<LocalDate> fechasLargas(String texto) {
    List<LocalDate> fechas = new ArrayList<>();
    Matcher m = FECHA_LARGA.matcher(texto);
    while (m.find()) {
      LocalDate f = fecha(m.group(3), mes(m.group(2)), m.group(1));
      if (f != null) fechas.add(f);
    }
    return fechas;
  }

  private static String mes(String nombre) {
    Integer n = nombre == null ? null : MESES.get(Normalizer.normalize(nombre.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", ""));
    return n == null ? null : String.valueOf(n);
  }

  private static LocalDate fecha(String anio, String mes, String dia) {
    try {
      return mes == null ? null : LocalDate.of(Integer.parseInt(anio), Integer.parseInt(mes), Integer.parseInt(dia));
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String normaTexto(Matcher m) { return m.group(1).replaceAll("\\s", "") + " " + m.group(2) + ":" + m.group(3); }

  /**«ISO 37001», «ISO/IEC 27001»: coincide con el nombre corto del catálogo de normas*/
  private static String nombreNorma(String texto) {
    Matcher m = NORMA.matcher(texto);
    return m.find() ? m.group(1).replaceAll("\\s", "") + " " + m.group(2) : null;
  }

  private static String edicion(String texto) {
    Matcher m = NORMA.matcher(texto);
    return m.find() ? m.group(3) : null;
  }

  private static int indice(List<String> lineas, String etiquetaCompacta) {
    for (int i = 0; i < lineas.size(); i++) if (compacto(lineas.get(i)).startsWith(etiquetaCompacta)) return i;
    return -1;
  }

  private static int indiceDe(List<String> lineas, String fragmento) {
    for (int i = 0; i < lineas.size(); i++) if (lineas.get(i).contains(fragmento)) return i;
    return -1;
  }

  /**texto entre la línea que empieza con «desde» (sin esa etiqueta) y la que empieza con «hasta»*/
  private static String entre(List<String> lineas, String desde, String hasta) {
    int i = indice(lineas, desde);
    if (i < 0) return null;
    List<String> partes = new ArrayList<>();
    String primera = lineas.get(i).replaceFirst("(?i)^\\s*alcance\\s*:\\s*", "");
    if (!primera.isBlank()) partes.add(primera);
    for (int j = i + 1; j < lineas.size() && !compacto(lineas.get(j)).startsWith(hasta); j++) partes.add(lineas.get(j));
    String r = String.join("\n", partes).strip();
    return r.isEmpty() ? null : r;
  }

  private static String unir(List<String> lineas) {
    String r = String.join(" ", lineas).replaceAll("\\s+", " ").replaceAll("\\s*-\\s*$", "").strip();
    return r.isEmpty() ? null : r;
  }

  private static String grupo(String texto, String regex) {
    Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(texto);
    return m.find() ? m.group(1).strip() : null;
  }

  private static Integer entero(String v) { return v == null ? null : Integer.valueOf(v); }

  private static BigDecimal decimal(String v) { return v == null ? null : new BigDecimal(v.replace(',', '.')); }

  private static String limpio(String v) { return v == null || v.isBlank() ? null : v.replaceAll("\\s+", " ").strip(); }

  private static String sinMarcasLegales(String v) { return limpio(v); }

  private static void faltantes(List<String> avisos, Map<String, Boolean> campos) {
    List<String> faltan = campos.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).sorted().toList();
    if (!faltan.isEmpty()) avisos.add("No se pudo leer: " + String.join(", ", faltan) + ". Complétalo a mano.");
  }
}
