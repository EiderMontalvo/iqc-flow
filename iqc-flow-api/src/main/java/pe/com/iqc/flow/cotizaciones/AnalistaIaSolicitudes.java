package pe.com.iqc.flow.cotizaciones;

import java.util.List;
import java.util.Optional;

/**
 *puerto de IA para analizar la solicitud de un cliente (correo, carta, formulario) y proponer los datos de la
 *cotización. Lo implementa el módulo ia; sin proveedor configurado no hay bean y el análisis es solo local.
 *La IA propone: la persona revisa todo antes de guardar
 */
public interface AnalistaIaSolicitudes {
  /**true si hay un proveedor de IA configurado (clave de API)*/
  boolean disponible();

  /**
   *analiza el texto de la solicitud (y el PDF original si el texto salió vacío, p. ej. un escaneo). Vacío si el
   *modelo se negó o no pudo responder; nunca lanza por un fallo del proveedor
   */
  Optional<PropuestaIa> analizar(String texto, byte[] pdfOriginal);

  /**lo que devuelve la IA, en texto plano: el servicio lo valida y lo traduce al catálogo*/
  record PropuestaIa(
      String resumen,
      List<String> puntosClave,
      List<String> informacionFaltante,
      String ruc,
      String razonSocial,
      String contactoNombre,
      String contactoCargo,
      String contactoCorreo,
      String contactoTelefono,
      String tipoServicio,
      List<String> normas,
      String alcance,
      String numeroSedes,
      String sedesDetalle,
      String numeroTrabajadores,
      String plazo) {
  }
}
