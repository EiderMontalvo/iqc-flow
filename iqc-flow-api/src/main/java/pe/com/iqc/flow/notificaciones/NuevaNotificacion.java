package pe.com.iqc.flow.notificaciones;

import java.util.UUID;

/**
 *aviso que otro módulo quiere enviar. Con claveDeduplicacion, el mismo hecho solo se notifica una vez
 *(por ejemplo "tarea-vencida:{id}:{reprogramaciones}")
 */
public record NuevaNotificacion(
    UUID organizacionId,
    UUID destinatarioId,
    String tipo,
    String titulo,
    String cuerpo,
    String sujetoTipo,
    UUID sujetoId,
    String claveDeduplicacion) {
}
