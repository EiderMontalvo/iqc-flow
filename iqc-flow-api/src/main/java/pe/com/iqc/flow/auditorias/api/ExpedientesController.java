package pe.com.iqc.flow.auditorias.api;

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
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.AuditoriaRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.AuditoriaSinEquipo;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ExpedienteDetalle;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ExpedienteResumen;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ResumenExpedientes;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudActualizarAuditoria;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudActualizarExpediente;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudAuditoria;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudCertificacion;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudCrearExpediente;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudEquipo;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudTransicion;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.VigilanciaPendiente;
import pe.com.iqc.flow.auditorias.application.ServicioAuditorias;
import pe.com.iqc.flow.auditorias.application.ServicioAuditorias.VistaVigilancias;
import pe.com.iqc.flow.auditorias.application.ServicioExpedientes;
import pe.com.iqc.flow.auditorias.domain.EstadoExpediente;
import pe.com.iqc.flow.auditorias.domain.TipoServicio;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;

@RestController
@RequestMapping("/api/v1/expedientes")
@Tag(name = "Expedientes y auditorías")
public class ExpedientesController {
  private final ServicioExpedientes expedientes;
  private final ServicioAuditorias auditorias;

  public ExpedientesController(ServicioExpedientes expedientes, ServicioAuditorias auditorias) {
    this.expedientes = expedientes;
    this.auditorias = auditorias;
  }

  @GetMapping
  @Operation(summary = "Busca expedientes por código, contrato o código de India; filtra por cliente, esquema, estado y tipo")
  public RespuestaPaginada<ExpedienteResumen> listar(
      @RequestParam(required = false) String texto,
      @RequestParam(required = false) UUID clienteId,
      @RequestParam(required = false) String esquema,
      @RequestParam(required = false) EstadoExpediente estado,
      @RequestParam(required = false) TipoServicio tipoServicio,
      @RequestParam(required = false) UUID responsableId,
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(expedientes.listar(texto, clienteId, esquema, estado, tipoServicio, responsableId,
        pagina, tamano));
  }

  @GetMapping("/resumen")
  @Operation(summary = "Expedientes por estado, vigilancias vencidas y próximas, auditorías sin equipo")
  public ResumenExpedientes resumen() { return expedientes.resumen(); }

  @GetMapping("/vigilancias")
  @Operation(summary = "Vigilancias pendientes: PROXIMAS (en los próximos N días) o VENCIDAS")
  public List<VigilanciaPendiente> vigilancias(
      @RequestParam(defaultValue = "PROXIMAS") VistaVigilancias vista,
      @RequestParam(defaultValue = "60") @Min(1) @Max(365) int dias) {
    return auditorias.vigilancias(vista, dias);
  }

  @GetMapping("/auditorias-sin-equipo")
  @Operation(summary = "Auditorías por programar o programadas sin auditores asignados")
  public List<AuditoriaSinEquipo> sinEquipo() { return auditorias.sinEquipo(); }

  @GetMapping("/{id}")
  @Operation(summary = "Ficha del expediente: normas, sedes, auditorías con su equipo, historial y transiciones permitidas")
  public ExpedienteDetalle obtener(@PathVariable UUID id) { return expedientes.obtener(id); }

  @PostMapping
  @Operation(summary = "Abre un expediente; el estado inicial depende del esquema (expedientes:gestionar)")
  public ResponseEntity<ExpedienteDetalle> crear(@Valid @RequestBody SolicitudCrearExpediente solicitud) {
    ExpedienteDetalle creado = expedientes.crear(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/expedientes/" + creado.id())).body(creado);
  }

  @PatchMapping("/{id}")
  public ExpedienteDetalle actualizar(@PathVariable UUID id, @Valid @RequestBody SolicitudActualizarExpediente solicitud) {
    return expedientes.actualizar(id, solicitud);
  }

  @PostMapping("/{id}/transicion")
  @Operation(summary = "Cambia el estado según el flujo del esquema; para CANCELADO el motivo es obligatorio")
  public ExpedienteDetalle cambiarEstado(@PathVariable UUID id, @Valid @RequestBody SolicitudTransicion solicitud) {
    return expedientes.cambiarEstado(id, solicitud);
  }

  @PostMapping("/{id}/certificacion")
  @Operation(summary = "Registra la fecha de certificación: crea las vigilancias del ciclo y sus tareas de seguimiento")
  public ExpedienteDetalle registrarCertificacion(@PathVariable UUID id,
      @Valid @RequestBody SolicitudCertificacion solicitud) {
    return expedientes.registrarCertificacion(id, solicitud);
  }

  @PostMapping("/{id}/auditorias")
  public ResponseEntity<AuditoriaRespuesta> crearAuditoria(@PathVariable UUID id,
      @Valid @RequestBody SolicitudAuditoria solicitud) {
    AuditoriaRespuesta creada = auditorias.crear(id, solicitud);
    return ResponseEntity.created(URI.create("/api/v1/expedientes/" + id + "/auditorias/" + creada.id())).body(creada);
  }

  @PatchMapping("/{id}/auditorias/{auditoriaId}")
  @Operation(summary = "Planifica, registra la ejecución, el estado del paquete (IQC Global) o cancela la auditoría")
  public AuditoriaRespuesta actualizarAuditoria(@PathVariable UUID id, @PathVariable UUID auditoriaId,
      @Valid @RequestBody SolicitudActualizarAuditoria solicitud) {
    return auditorias.actualizar(id, auditoriaId, solicitud);
  }

  @PutMapping("/{id}/auditorias/{auditoriaId}/equipo")
  @Operation(summary = "Reemplaza el equipo auditor; devuelve avisos de competencia y de líder")
  public AuditoriaRespuesta asignarEquipo(@PathVariable UUID id, @PathVariable UUID auditoriaId,
      @Valid @RequestBody SolicitudEquipo solicitud) {
    return auditorias.asignarEquipo(id, auditoriaId, solicitud);
  }
}
