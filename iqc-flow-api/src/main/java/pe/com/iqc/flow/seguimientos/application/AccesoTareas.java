package pe.com.iqc.flow.seguimientos.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.seguimientos.domain.TareaSeguimiento;
import pe.com.iqc.flow.seguimientos.infrastructure.TareaRepository;

/**
 *reglas de acceso a tareas: se ven las propias y las de los clientes a cargo (todas con
 *seguimientos:leer-todos). Modifica el responsable, o quien puede asignar o ver todas (supervisión)
 */
@Component
class AccesoTareas {
  static final String LEER_TODOS = "seguimientos:leer-todos";
  static final String ESCRIBIR = "seguimientos:escribir";
  static final String ASIGNAR = "seguimientos:asignar";

  private final TareaRepository tareas;
  private final ConsultaClientes clientes;
  private final ActorActual actorActual;

  AccesoTareas(TareaRepository tareas, ConsultaClientes clientes, ActorActual actorActual) {
    this.tareas = tareas;
    this.clientes = clientes;
    this.actorActual = actorActual;
  }

  boolean veTodas() { return actorActual.tienePermiso(LEER_TODOS); }

  /**clientes del actor, para calcular qué tareas ajenas puede ver*/
  Set<UUID> clientesPropios() {
    UsuarioAutenticado actor = actorActual.requerido();
    return clientes.idsDeResponsable(actor.organizacionId(), actor.id());
  }

  /**las tareas que no puede ver se tratan como inexistentes*/
  TareaSeguimiento cargarVisible(UUID id) {
    UsuarioAutenticado actor = actorActual.requerido();
    TareaSeguimiento tarea = tareas.findByIdAndOrganizacionId(id, actor.organizacionId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Tarea", id));
    if (!puedeVer(tarea)) throw new RecursoNoEncontradoException("Tarea", id);
    return tarea;
  }

  TareaSeguimiento cargarGestionable(UUID id) {
    TareaSeguimiento tarea = cargarVisible(id);
    if (!puedeGestionar(tarea)) {
      throw new AccessDeniedException("Solo el responsable o un supervisor puede modificar esta tarea");
    }
    return tarea;
  }

  boolean puedeVer(TareaSeguimiento tarea) {
    UsuarioAutenticado actor = actorActual.requerido();
    return veTodas() || tarea.getResponsableUsuarioId().equals(actor.id())
        || (tarea.getClienteId() != null && clientesPropios().contains(tarea.getClienteId()));
  }

  boolean puedeGestionar(TareaSeguimiento tarea) {
    return actorActual.tienePermiso(ESCRIBIR)
        && (tarea.getResponsableUsuarioId().equals(actorActual.requerido().id())
            || actorActual.tienePermiso(ASIGNAR) || veTodas());
  }
}
