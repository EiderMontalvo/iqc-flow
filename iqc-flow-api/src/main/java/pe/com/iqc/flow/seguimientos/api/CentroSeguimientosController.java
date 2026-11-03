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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.seguimientos.application.CentroSeguimientos;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.ActividadRespuesta;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.ClienteSinMovimiento;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.ResumenCentro;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudActividad;
import pe.com.iqc.flow.seguimientos.application.ServicioActividades;

@RestController
@RequestMapping("/api/v1/seguimientos")
@Tag(name = "Seguimientos: centro y actividades")
public class CentroSeguimientosController {
  private final CentroSeguimientos centro;
  private final ServicioActividades actividades;

  public CentroSeguimientosController(CentroSeguimientos centro, ServicioActividades actividades) {
    this.centro = centro;
    this.actividades = actividades;
  }

  @GetMapping("/resumen")
  @Operation(summary = "Contadores de mis tareas: hoy, atrasadas, próximas y en espera")
  public ResumenCentro resumen(@RequestParam(defaultValue = "7") @Min(1) @Max(90) int dias) {
    return centro.resumen(dias);
  }

  @GetMapping("/clientes-sin-movimiento")
  @Operation(summary = "Clientes vigentes sin actividad en N días y sin tareas abiertas")
  public List<ClienteSinMovimiento> clientesSinMovimiento(
      @RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias,
      @RequestParam(required = false) UUID responsableId) {
    return centro.clientesSinMovimiento(dias, responsableId);
  }

  @PostMapping("/actividades")
  @Operation(summary = "Registra una llamada, correo, reunión, WhatsApp, visita o nota con un cliente")
  public ResponseEntity<ActividadRespuesta> registrarActividad(@Valid @RequestBody SolicitudActividad solicitud) {
    ActividadRespuesta creada = actividades.registrar(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/seguimientos/actividades/" + creada.id())).body(creada);
  }

  @GetMapping("/actividades")
  @Operation(summary = "Historial de actividades de un cliente, de la más reciente a la más antigua")
  public RespuestaPaginada<ActividadRespuesta> listarActividades(
      @RequestParam UUID clienteId,
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(actividades.listarDeCliente(clienteId, pagina, tamano));
  }
}
