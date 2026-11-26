package pe.com.iqc.flow.correos.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.AsistenteCorreos.Borrador;
import pe.com.iqc.flow.correos.AsistenteCorreos.Consulta;
import pe.com.iqc.flow.correos.AsistenteCorreos.Resumen;

/**
 *lo que dice el modelo es texto de un tercero para la persona que lo revisa, no una verdad: se limpia y se acota antes de mostrarlo. Sin
 *caracteres de control (un salto de línea en el asunto de un borrador inyectaría cabeceras al enviarlo), con largos máximos, y con los
 *ids citados limitados a los que de verdad se le pasaron. Lo que no cumple se rechaza con IA_RESPUESTA_INVALIDA: no se arregla a ojo
 */
public final class ValidadorSalidaIa {
  static final int RESUMEN_MAXIMO = 700;
  static final int PUNTOS_MAXIMOS = 5;
  static final int PUNTO_MAXIMO = 240;
  static final int ACCION_MAXIMA = 240;
  public static final int ASUNTO_MAXIMO = 200;
  public static final int CUERPO_MAXIMO = 5_000;
  static final int RESPUESTA_MAXIMA = 3_000;
  private static final Pattern CONTROL_EN_LINEA = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");
  private static final Pattern CONTROL_TODO = Pattern.compile("\\p{Cntrl}");

  private ValidadorSalidaIa() {
  }

  public static Resumen resumen(Resumen r) {
    String resumen = recortada(r.resumen(), RESUMEN_MAXIMO);
    if (resumen.isEmpty()) throw invalida();
    List<String> puntos = new ArrayList<>();
    if (r.puntosClave() != null) for (String p : r.puntosClave()) {
      String limpio = recortada(p, PUNTO_MAXIMO);
      if (!limpio.isEmpty() && puntos.size() < PUNTOS_MAXIMOS) puntos.add(limpio);
    }
    String accion = recortada(r.accionSugerida(), ACCION_MAXIMA);
    return new Resumen(resumen, List.copyOf(puntos), accion.isEmpty() ? null : accion, r.tipoSugerido(), r.consumo());
  }

  /**el asunto va en una sola línea; si el modelo no puso, el servicio usa «Re: …». El cuerpo conserva sus saltos de línea*/
  public static Borrador borrador(Borrador b) {
    String cuerpo = texto(b.cuerpo(), CUERPO_MAXIMO);
    String asunto = una(b.asunto());
    if (asunto.length() > ASUNTO_MAXIMO) throw invalida();
    return new Borrador(asunto, cuerpo, b.consumo());
  }

  /**`permitidos`: los ids de los correos que se le pasaron; un id inventado o ajeno se descarta, no se muestra*/
  public static Consulta consulta(Consulta c, Set<String> permitidos) {
    List<String> usados = c.idsUsados() == null ? List.of() : c.idsUsados().stream().map(i -> i == null ? "" : i.strip()).filter(permitidos::contains).distinct().toList();
    return new Consulta(texto(c.respuesta(), RESPUESTA_MAXIMA), usados, c.consumo());
  }

  /**una sola línea, sin caracteres de control ni espacios repetidos*/
  private static String una(String s) { return s == null ? "" : CONTROL_TODO.matcher(s).replaceAll(" ").replaceAll("\\s+", " ").strip(); }

  /**texto solo para leer: se recorta con «…» si se pasa; el modelo no siempre cuenta bien las letras*/
  private static String recortada(String s, int maximo) {
    String limpio = una(s);
    return limpio.length() > maximo ? limpio.substring(0, maximo - 1).stripTrailing() + "…" : limpio;
  }

  /**varias líneas permitidas (saltos de línea y tabulaciones); sin otros caracteres de control*/
  private static String texto(String s, int maximo) {
    String limpio = s == null ? "" : CONTROL_EN_LINEA.matcher(s.replace("\r\n", "\n").replace('\t', ' ')).replaceAll("").strip();
    if (limpio.isEmpty() || limpio.length() > maximo) throw invalida();
    return limpio;
  }

  private static ReglaNegocioException invalida() {
    return new ReglaNegocioException("IA_RESPUESTA_INVALIDA", "La IA no devolvió una respuesta utilizable. Inténtalo de nuevo.");
  }
}
