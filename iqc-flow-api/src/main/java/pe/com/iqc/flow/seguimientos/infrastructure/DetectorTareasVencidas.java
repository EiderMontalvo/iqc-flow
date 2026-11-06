package pe.com.iqc.flow.seguimientos.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.notificaciones.NuevaNotificacion;
import pe.com.iqc.flow.notificaciones.ServicioNotificaciones;
import pe.com.iqc.flow.seguimientos.domain.TareaSeguimiento;

/**
 *revisa periódicamente las tareas vencidas y avisa una sola vez a su responsable. El estado queda en la
 *base de datos (aviso_vencimiento_en), así que un reinicio del servidor no repite ni pierde avisos.
 *El escalamiento a supervisores llegará con el motor de automatizaciones
 */
@Component
public class DetectorTareasVencidas {
  private static final Logger log = LoggerFactory.getLogger(DetectorTareasVencidas.class);
  private static final int LOTE = 200;
  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final TareaRepository tareas;
  private final ServicioNotificaciones notificaciones;
  private final Clock reloj;

  public DetectorTareasVencidas(TareaRepository tareas, ServicioNotificaciones notificaciones, Clock reloj) {
    this.tareas = tareas;
    this.notificaciones = notificaciones;
    this.reloj = reloj;
  }

  @Scheduled(fixedDelayString = "${iqc.seguimientos.revision-vencidas:PT15M}", initialDelayString = "PT1M")
  @Transactional
  public void ejecutarProgramado() {
    int avisadas = revisar();
    if (avisadas > 0) log.info("Avisos de tareas vencidas enviados: {}", avisadas);
  }

  /**devuelve cuántas tareas se notificaron en esta revisión*/
  @Transactional
  public int revisar() {
    LocalDate hoy = LocalDate.now(reloj);
    LocalTime ahora = LocalTime.now(reloj);
    Instant instante = Instant.now(reloj);
    List<TareaSeguimiento> vencidas = tareas.bloquearVencidasSinAviso(TareaSeguimiento.ABIERTAS, hoy, ahora,
        PageRequest.of(0, LOTE));
    for (TareaSeguimiento tarea : vencidas) {
      notificaciones.notificar(new NuevaNotificacion(
          tarea.getOrganizacionId(),
          tarea.getResponsableUsuarioId(),
          "TAREA_VENCIDA",
          "Tarea vencida: " + tarea.getTitulo(),
          "Venció el " + FECHA.format(tarea.getFechaLimite())
              + (tarea.getHoraLimite() != null ? " a las " + tarea.getHoraLimite() : "") + ".",
          "TAREA",
          tarea.getId(),
          // Incluye las reprogramaciones: si se reprograma y vuelve a vencer, se avisa de nuevo
          "tarea-vencida:" + tarea.getId() + ":" + tarea.getReprogramaciones()));
      tarea.marcarAvisoVencimiento(instante);
    }
    return vencidas.size();
  }
}
