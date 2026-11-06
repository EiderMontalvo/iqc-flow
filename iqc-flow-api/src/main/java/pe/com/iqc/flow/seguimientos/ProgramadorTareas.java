package pe.com.iqc.flow.seguimientos;

import java.time.LocalDate;
import java.util.UUID;

/**API pública de seguimientos para que otros módulos y automatizaciones creen tareas*/
public interface ProgramadorTareas {
  /**
   *crea una tarea con origen AUTOMATIZACION. Si ya existe una tarea abierta con la misma clave de
   *deduplicación, no crea otra y devuelve la existente. Una fecha límite pasada se ajusta a hoy
   */
  UUID crearTareaAutomatica(NuevaTareaAutomatica tarea);

  /**cancela la tarea abierta con esa clave de deduplicación, si existe (p. ej. al restaurar una suspensión)*/
  boolean cancelarTareaAutomatica(UUID organizacionId, String claveDeduplicacion, String motivo);

  record NuevaTareaAutomatica(
      UUID organizacionId,
      String titulo,
      String descripcion,
      String tipoCodigo,
      String sujetoTipo,
      UUID sujetoId,
      UUID clienteId,
      UUID responsableId,
      LocalDate fechaLimite,
      String claveDeduplicacion) {
  }
}
