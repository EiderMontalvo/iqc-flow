package pe.com.iqc.flow.correos.domain;

import java.time.Instant;
import java.util.List;

/**
 *lo que se guarda de un correo recibido: texto plano (nunca HTML) y los adjuntos solo como datos. El mensaje original completo
 *se guarda aparte como .eml y de ahí se sirven los adjuntos
 */
public record MensajeLeido(String messageId, String inReplyTo, String referencias, String remitenteCorreo, String remitenteNombre,
    String para, String cc, String asunto, Instant fecha, String texto, boolean textoTruncado, List<Adjunto> adjuntos, boolean masivo) {
  public MensajeLeido {
    adjuntos = List.copyOf(adjuntos);
  }

  /**el mismo mensaje con otro texto (p. ej. cuando era demasiado grande y solo se guardó el encabezado)*/
  public MensajeLeido conTexto(String nuevoTexto, boolean truncado) {
    return new MensajeLeido(messageId, inReplyTo, referencias, remitenteCorreo, remitenteNombre, para, cc, asunto, fecha, nuevoTexto, truncado, adjuntos, masivo);
  }

  public record Adjunto(String nombre, String tipo, long tamano) {
  }
}
