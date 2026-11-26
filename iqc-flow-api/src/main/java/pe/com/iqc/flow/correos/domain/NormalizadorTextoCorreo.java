package pe.com.iqc.flow.correos.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 *minúsculas y sin tildes («Cotización» y «COTIZACION» valen lo mismo para las reglas), palabras sueltas y comparación tolerante a
 *una errata: una letra de más, de menos, distinta o dos contiguas cambiadas («certiticado», «certifcado»)
 */
final class NormalizadorTextoCorreo {
  private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

  private NormalizadorTextoCorreo() {
  }

  static String normalizar(String texto) {
    if (texto == null) return "";
    return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
  }

  /**las palabras de un texto ya normalizado*/
  static List<String> palabras(String normalizado) {
    List<String> palabras = new ArrayList<>();
    for (String p : NO_ALFANUMERICO.split(normalizado)) if (!p.isEmpty()) palabras.add(p);
    return palabras;
  }

  /**
   *verdadero si `a` y `b` son iguales o difieren en una sola errata: una letra sustituida, insertada o quitada, o dos letras
   *contiguas intercambiadas. Es la distancia de edición ≤ 1 (con transposición), calculada sin matriz: una pasada
   */
  static boolean casiIguales(String a, String b) {
    int diferencia = a.length() - b.length();
    if (diferencia > 1 || diferencia < -1) return false;
    if (diferencia != 0) {
      String larga = diferencia > 0 ? a : b, corta = diferencia > 0 ? b : a;
      int i = 0;
      while (i < corta.length() && larga.charAt(i) == corta.charAt(i)) i++;
      return larga.regionMatches(i + 1, corta, i, corta.length() - i);
    }
    int i = 0;
    while (i < a.length() && a.charAt(i) == b.charAt(i)) i++;
    if (i == a.length()) return true;
    if (a.regionMatches(i + 1, b, i + 1, a.length() - i - 1)) return true; // una letra distinta
    return i + 1 < a.length() && a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i) && a.regionMatches(i + 2, b, i + 2, a.length() - i - 2);
  }
}
