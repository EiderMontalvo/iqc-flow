package pe.com.iqc.flow.notificaciones;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.notificaciones.domain.Notificacion;
import pe.com.iqc.flow.notificaciones.infrastructure.NotificacionRepository;

/**API pública de notificaciones: otros módulos envían avisos; cada usuario consulta solo los suyos*/
@Service
public class ServicioNotificaciones {
  private final NotificacionRepository notificaciones;
  private final ActorActual actorActual;
  private final Clock reloj;

  public ServicioNotificaciones(NotificacionRepository notificaciones, ActorActual actorActual, Clock reloj) {
    this.notificaciones = notificaciones;
    this.actorActual = actorActual;
    this.reloj = reloj;
  }

  /**registra el aviso; devuelve false si ya se había enviado uno con la misma clave*/
  @Transactional
  public boolean notificar(NuevaNotificacion aviso) {
    if (aviso.claveDeduplicacion() != null && notificaciones.existsByClaveDeduplicacion(aviso.claveDeduplicacion())) {
      return false;
    }
    notificaciones.save(new Notificacion(aviso.organizacionId(), aviso.destinatarioId(), aviso.tipo(),
        recortar(aviso.titulo(), 200), recortar(aviso.cuerpo(), 1000), aviso.sujetoTipo(), aviso.sujetoId(),
        aviso.claveDeduplicacion()));
    return true;
  }

  @Transactional(readOnly = true)
  public Page<NotificacionRespuesta> listarPropias(boolean soloNoLeidas, int pagina, int tamano) {
    return notificaciones.listar(actorActual.requerido().id(), soloNoLeidas, PageRequest.of(pagina, tamano))
        .map(NotificacionRespuesta::de);
  }

  @Transactional(readOnly = true)
  public long contarNoLeidas() {
    return notificaciones.countByDestinatarioUsuarioIdAndLeidaEnIsNull(actorActual.requerido().id());
  }

  @Transactional
  public NotificacionRespuesta marcarLeida(UUID id) {
    Notificacion notificacion = notificaciones.findByIdAndDestinatarioUsuarioId(id, actorActual.requerido().id())
        .orElseThrow(() -> new RecursoNoEncontradoException("Notificación", id));
    notificacion.marcarLeida(Instant.now(reloj));
    return NotificacionRespuesta.de(notificacion);
  }

  @Transactional
  public int marcarTodasLeidas() {
    return notificaciones.marcarTodasLeidas(actorActual.requerido().id(), Instant.now(reloj));
  }

  private static String recortar(String texto, int maximo) {
    return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
  }

  public record NotificacionRespuesta(UUID id, String tipo, String titulo, String cuerpo, String sujetoTipo,
      UUID sujetoId, boolean leida, Instant creadoEn) {
    static NotificacionRespuesta de(Notificacion n) {
      return new NotificacionRespuesta(n.getId(), n.getTipo(), n.getTitulo(), n.getCuerpo(), n.getSujetoTipo(),
          n.getSujetoId(), n.getLeidaEn() != null, n.getCreadoEn());
    }
  }
}
