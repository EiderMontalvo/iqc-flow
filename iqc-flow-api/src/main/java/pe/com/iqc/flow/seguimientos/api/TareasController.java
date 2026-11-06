package pe.com.iqc.flow.seguimientos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudCancelarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudCompletarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudCrearTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudEditarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudReasignarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudReprogramarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.TareaDetalle;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.TareaResumen;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.TipoTareaRespuesta;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.VistaTareas;
import pe.com.iqc.flow.seguimientos.application.ServicioTareas;
import pe.com.iqc.flow.seguimientos.domain.EstadoTarea;
import pe.com.iqc.flow.seguimientos.domain.PrioridadTarea;

@RestController
@RequestMapping("/api/v1/seguimientos")
@Tag(name = "Seguimientos: tareas")
public class TareasController {
  private final ServicioTareas servicio;

  public TareasController(ServicioTareas servicio) { this.servicio = servicio; }

  @GetMapping("/tipos-tarea")
  @Operation(summary = "Catálogo de tipos de tarea")
  public List<TipoTareaRespuesta> tipos() { return servicio.listarTipos(); }

  @GetMapping("/tareas")
  @Operation(summary = "Centro de seguimientos: tareas visibles según la vista (HOY, ATRASADAS, PROXIMAS, EN_ESPERA…)")
  public RespuestaPaginada<TareaResumen> listar(
      @RequestParam(defaultValue = "ABIERTAS") VistaTareas vista,
      @RequestParam(defaultValue = "false") boolean mias,
      @RequestParam(required = false) UUID responsableId,
      @RequestParam(required = false) UUID clienteId,
      @RequestParam(required = false) String tipo,
      @RequestParam(required = false) EstadoTarea estado,
      @RequestParam(required = false) PrioridadTarea prioridad,
      @RequestParam(defaultValue = "7") @Min(1) @Max(90) int dias,
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(servicio.listar(vista, mias, responsableId, clienteId, tipo, estado, prioridad, dias,
        pagina, tamano));
  }

  @GetMapping("/tareas/{id}")
  @Operation(summary = "Detalle de la tarea con su historial")
  public TareaDetalle obtener(@PathVariable UUID id) { return servicio.obtener(id); }

  @PostMapping("/tareas")
  @Operation(summary = "Crea una tarea (para otra persona requiere seguimientos:asignar)")
  public ResponseEntity<TareaDetalle> crear(@Valid @RequestBody SolicitudCrearTarea solicitud) {
    TareaDetalle creada = servicio.crear(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/seguimientos/tareas/" + creada.id())).body(creada);
  }

  @PatchMapping("/tareas/{id}")
  @Operation(summary = "Edita título, descripción, prioridad o estado abierto (PENDIENTE, EN_CURSO, EN_ESPERA)")
  public TareaDetalle editar(@PathVariable UUID id, @Valid @RequestBody SolicitudEditarTarea solicitud) {
    return servicio.editar(id, solicitud);
  }

  @PostMapping("/tareas/{id}/completado")
  @Operation(summary = "Completa la tarea con un resultado; opcionalmente crea la siguiente")
  public TareaDetalle completar(@PathVariable UUID id, @Valid @RequestBody SolicitudCompletarTarea solicitud) {
    return servicio.completar(id, solicitud);
  }

  @PostMapping("/tareas/{id}/reprogramacion")
  @Operation(summary = "Cambia la fecha límite indicando el motivo")
  public TareaDetalle reprogramar(@PathVariable UUID id, @Valid @RequestBody SolicitudReprogramarTarea solicitud) {
    return servicio.reprogramar(id, solicitud);
  }

  @PutMapping("/tareas/{id}/responsable")
  @Operation(summary = "Reasigna la tarea (seguimientos:asignar)")
  public TareaDetalle reasignar(@PathVariable UUID id, @Valid @RequestBody SolicitudReasignarTarea solicitud) {
    return servicio.reasignar(id, solicitud);
  }

  @PostMapping("/tareas/{id}/cancelacion")
  @Operation(summary = "Cancela la tarea indicando el motivo")
  public TareaDetalle cancelar(@PathVariable UUID id, @Valid @RequestBody SolicitudCancelarTarea solicitud) {
    return servicio.cancelar(id, solicitud);
  }
}
