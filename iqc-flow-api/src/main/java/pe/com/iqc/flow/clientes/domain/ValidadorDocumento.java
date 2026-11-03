package pe.com.iqc.flow.clientes.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *valida y normaliza el país y el documento fiscal de un cliente. El RUC se verifica con el
 *algoritmo de SUNAT (módulo 11); el GSTIN con su formato oficial; los demás, solo por formato general.
 */
public final class ValidadorDocumento {
  private static final Set<String> PAISES = Set.of(Locale.getISOCountries());
  private static final Set<String> PREFIJOS_RUC = Set.of("10", "15", "16", "17", "20");
  private static final int[] PESOS_RUC = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
  private static final Pattern FORMATO_GSTIN = Pattern.compile("\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]");
  private static final Pattern FORMATO_GENERAL = Pattern.compile("[A-Z0-9-]{3,30}");

  private ValidadorDocumento() {
  }

  public static String normalizarPais(String codigoPais) {
    String pais = codigoPais == null ? "" : codigoPais.trim().toUpperCase(Locale.ROOT);
    if (!PAISES.contains(pais)) {
      throw new ReglaNegocioException("PAIS_INVALIDO", "Código de país ISO inválido: " + codigoPais);
    }
    return pais;
  }

  /**devuelve el número normalizado (sin espacios, en mayúsculas) o lanza DOCUMENTO_INVALIDO. */
  public static String normalizar(String codigoPais, TipoDocumento tipo, String numero) {
    String pais = normalizarPais(codigoPais);
    if (tipo == null || numero == null || numero.isBlank()) {
      throw invalido("Indica el tipo y el número de documento.");
    }
    String normalizado = numero.replaceAll("[\\s.]", "").toUpperCase(Locale.ROOT);
    if ("PE".equals(pais) && tipo != TipoDocumento.RUC) {
      throw invalido("Las empresas peruanas se identifican con RUC.");
    }
    switch (tipo) {
      case RUC -> {
        if (!"PE".equals(pais)) throw invalido("El RUC solo aplica a empresas de Perú (PE).");
        validarRuc(normalizado);
      }
      case GSTIN -> {
        if (!FORMATO_GSTIN.matcher(normalizado).matches()) throw invalido("El GSTIN no tiene el formato oficial de 15 caracteres.");
      }
      default -> {
        if (!FORMATO_GENERAL.matcher(normalizado).matches()) {
          throw invalido("El documento debe tener de 3 a 30 letras, números o guiones.");
        }
      }
    }
    return normalizado;
  }

  public static boolean esRucValido(String ruc) {
    if (ruc == null || !ruc.matches("\\d{11}") || !PREFIJOS_RUC.contains(ruc.substring(0, 2))) return false;
    int suma = 0;
    for (int i = 0; i < PESOS_RUC.length; i++) suma += Character.digit(ruc.charAt(i), 10) * PESOS_RUC[i];
    int verificador = 11 - suma % 11;
    if (verificador == 10) verificador = 0;
    if (verificador == 11) verificador = 1;
    return verificador == Character.digit(ruc.charAt(10), 10);
  }

  private static void validarRuc(String ruc) {
    if (!ruc.matches("\\d{11}")) throw invalido("El RUC debe tener 11 dígitos.");
    if (!PREFIJOS_RUC.contains(ruc.substring(0, 2))) throw invalido("El RUC debe empezar con 10, 15, 16, 17 o 20.");
    if (!esRucValido(ruc)) throw invalido("El dígito verificador del RUC no es correcto.");
  }

  private static ReglaNegocioException invalido(String mensaje) {
    return new ReglaNegocioException("DOCUMENTO_INVALIDO", mensaje);
  }
}
