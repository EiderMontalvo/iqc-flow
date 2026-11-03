package pe.com.iqc.flow.clientes.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**texto en minúsculas, sin tildes y con espacios simples, para buscar "mineria" y encontrar "Minería". */
public final class NormalizadorTexto {
  private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
  private static final Pattern ESPACIOS = Pattern.compile("\\s+");

  private NormalizadorTexto() {
  }

  public static String normalizar(String texto) {
    if (texto == null) return "";
    String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
    return ESPACIOS.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
  }
}
