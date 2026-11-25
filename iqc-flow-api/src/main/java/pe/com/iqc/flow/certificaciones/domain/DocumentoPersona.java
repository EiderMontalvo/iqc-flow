package pe.com.iqc.flow.certificaciones.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *documento de identidad de una persona certificada, validado según su país. Las reglas son las de la plataforma
 *anterior (config/personal_documents.php) para que los registros importados sigan siendo válidos
 */
public record DocumentoPersona(String pais, String tipo, String numero) {
  /**tipos de documento admitidos por país: código → (etiqueta, regla, ayuda)*/
  public record TipoDocumento(String codigo, String etiqueta, Pattern regla, String ayuda) {
  }

  private static final Map<String, Map<String, TipoDocumento>> TIPOS = new LinkedHashMap<>();

  static {
    TipoDocumento pasaporte = new TipoDocumento("PASAPORTE", "Pasaporte", Pattern.compile("[A-Z0-9]{6,12}"),
        "Entre 6 y 12 caracteres alfanuméricos");
    tipos("PE", new TipoDocumento("DNI", "DNI", Pattern.compile("[0-9]{8}"), "8 dígitos"),
        new TipoDocumento("CE", "Carné de extranjería", Pattern.compile("[0-9]{9,12}"), "Entre 9 y 12 dígitos"), pasaporte);
    tipos("EC", new TipoDocumento("CEDULA", "Cédula de identidad", Pattern.compile("[0-9]{10}"), "10 dígitos"), pasaporte);
    tipos("CO", new TipoDocumento("CC", "Cédula de ciudadanía", Pattern.compile("[0-9]{6,10}"), "Entre 6 y 10 dígitos"),
        new TipoDocumento("CE", "Cédula de extranjería", Pattern.compile("[0-9]{6,10}"), "Entre 6 y 10 dígitos"), pasaporte);
    tipos("PA", new TipoDocumento("CEDULA", "Cédula de identidad", Pattern.compile("[0-9A-Z-]{5,15}"),
        "Entre 5 y 15 caracteres (números, letras y guiones)"), pasaporte);
  }

  private static void tipos(String pais, TipoDocumento... lista) {
    Map<String, TipoDocumento> porCodigo = new LinkedHashMap<>();
    for (TipoDocumento t : lista) porCodigo.put(t.codigo(), t);
    TIPOS.put(pais, porCodigo);
  }

  public static Map<String, Map<String, TipoDocumento>> tipos() { return TIPOS; }

  /**normaliza (mayúsculas, sin espacios) y valida; lanza DOCUMENTO_INVALIDO con la ayuda del tipo*/
  public static DocumentoPersona validar(String pais, String tipo, String numero) {
    String p = pais == null ? "" : pais.trim().toUpperCase(Locale.ROOT);
    String t = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
    String n = numero == null ? "" : numero.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    Map<String, TipoDocumento> delPais = TIPOS.get(p);
    if (delPais == null) throw new ReglaNegocioException("PAIS_INVALIDO", "No hay documentos configurados para el país " + pais + ".");
    TipoDocumento regla = delPais.get(t);
    if (regla == null) {
      throw new ReglaNegocioException("DOCUMENTO_INVALIDO", "El tipo de documento " + tipo + " no se admite en " + p
          + ". Tipos válidos: " + String.join(", ", delPais.keySet()) + ".");
    }
    if (!regla.regla().matcher(n).matches()) {
      throw new ReglaNegocioException("DOCUMENTO_INVALIDO", regla.etiqueta() + " inválido: " + regla.ayuda() + ".");
    }
    return new DocumentoPersona(p, t, n);
  }
}
