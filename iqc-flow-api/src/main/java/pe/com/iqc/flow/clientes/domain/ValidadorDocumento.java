package pe.com.iqc.flow.clientes.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *valida y normaliza el país y el documento fiscal de un cliente.
 *- RUC de Perú: algoritmo de SUNAT (módulo 11); un dígito verificador incorrecto rechaza el documento.
 *- RUC de Ecuador: la estructura (13 dígitos, provincia, tipo de contribuyente) se valida estrictamente;
 *  el dígito verificador solo genera una advertencia, porque hay RUC emitidos que no lo cumplen.
 *- GSTIN de India: formato oficial. Los demás, solo formato general
 */
public final class ValidadorDocumento {
  private static final Set<String> PAISES = Set.of(Locale.getISOCountries());
  /**países donde la empresa se identifica con RUC*/
  private static final Set<String> PAISES_RUC = Set.of("PE", "EC");
  private static final Set<String> PREFIJOS_RUC = Set.of("10", "15", "16", "17", "20");
  private static final int[] PESOS_RUC = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
  private static final int[] PESOS_EC_PRIVADA = {4, 3, 2, 7, 6, 5, 4, 3, 2};
  private static final int[] PESOS_EC_PUBLICA = {3, 2, 7, 6, 5, 4, 3, 2};
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

  /**devuelve el número normalizado (sin espacios, en mayúsculas) o lanza DOCUMENTO_INVALIDO*/
  public static String normalizar(String codigoPais, TipoDocumento tipo, String numero) {
    String pais = normalizarPais(codigoPais);
    if (tipo == null || numero == null || numero.isBlank()) {
      throw invalido("Indica el tipo y el número de documento.");
    }
    String normalizado = numero.replaceAll("[\\s.-]", "").toUpperCase(Locale.ROOT);
    if (PAISES_RUC.contains(pais) && tipo != TipoDocumento.RUC) {
      throw invalido("Las empresas de " + nombrePais(pais) + " se identifican con RUC.");
    }
    switch (tipo) {
      case RUC -> {
        if ("PE".equals(pais)) validarRucPeru(normalizado);
        else if ("EC".equals(pais)) validarEstructuraRucEcuador(normalizado);
        else throw invalido("El RUC solo aplica a empresas de Perú (PE) y Ecuador (EC).");
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

  /**avisos que no impiden registrar el documento (se muestran al verificarlo). Recibe el número ya normalizado*/
  public static List<String> advertencias(String codigoPais, TipoDocumento tipo, String numeroNormalizado) {
    if (tipo == TipoDocumento.RUC && "EC".equals(codigoPais) && !esDigitoVerificadorEcuadorValido(numeroNormalizado)) {
      return List.of("El dígito verificador del RUC ecuatoriano no coincide con el algoritmo del SRI. "
          + "Confirma el número en la consulta de RUC del SRI antes de continuar.");
    }
    return List.of();
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

  /**
   *dígito verificador del RUC ecuatoriano según el tercer dígito: 0–5 persona natural (módulo 10 sobre la
   *cédula), 6 entidad pública (módulo 11, dígito 9), 9 sociedad privada o extranjera (módulo 11, dígito 10)
   */
  public static boolean esDigitoVerificadorEcuadorValido(String ruc) {
    if (ruc == null || !ruc.matches("\\d{13}")) return false;
    int tercero = Character.digit(ruc.charAt(2), 10);
    if (tercero < 6) {
      int suma = 0;
      for (int i = 0; i < 9; i++) {
        int producto = Character.digit(ruc.charAt(i), 10) * (i % 2 == 0 ? 2 : 1);
        suma += producto > 9 ? producto - 9 : producto;
      }
      return (10 - suma % 10) % 10 == Character.digit(ruc.charAt(9), 10);
    }
    int[] pesos = tercero == 6 ? PESOS_EC_PUBLICA : PESOS_EC_PRIVADA;
    int suma = 0;
    for (int i = 0; i < pesos.length; i++) suma += Character.digit(ruc.charAt(i), 10) * pesos[i];
    int resto = suma % 11;
    int verificador = resto == 0 ? 0 : 11 - resto;
    return verificador == Character.digit(ruc.charAt(pesos.length), 10);
  }

  private static void validarRucPeru(String ruc) {
    if (!ruc.matches("\\d{11}")) throw invalido("El RUC peruano debe tener 11 dígitos.");
    if (!PREFIJOS_RUC.contains(ruc.substring(0, 2))) throw invalido("El RUC debe empezar con 10, 15, 16, 17 o 20.");
    if (!esRucValido(ruc)) throw invalido("El dígito verificador del RUC no es correcto.");
  }

  private static void validarEstructuraRucEcuador(String ruc) {
    if (!ruc.matches("\\d{13}")) throw invalido("El RUC ecuatoriano debe tener 13 dígitos.");
    int provincia = Integer.parseInt(ruc.substring(0, 2));
    if (!((provincia >= 1 && provincia <= 24) || provincia == 30)) {
      throw invalido("Los dos primeros dígitos del RUC ecuatoriano deben ser una provincia (01 a 24, o 30).");
    }
    int tercero = Character.digit(ruc.charAt(2), 10);
    if (tercero == 7 || tercero == 8) {
      throw invalido("El tercer dígito del RUC ecuatoriano debe ser 0–5 (persona natural), 6 (pública) o 9 (sociedad).");
    }
    boolean establecimientoValido = tercero == 6 ? !ruc.endsWith("0000") : !ruc.endsWith("000");
    if (!establecimientoValido) {
      throw invalido("El RUC ecuatoriano debe terminar con el número de establecimiento (normalmente 001).");
    }
  }

  private static String nombrePais(String pais) {
    return Locale.of("es", pais).getDisplayCountry(Locale.of("es"));
  }

  private static ReglaNegocioException invalido(String mensaje) {
    return new ReglaNegocioException("DOCUMENTO_INVALIDO", mensaje);
  }
}
