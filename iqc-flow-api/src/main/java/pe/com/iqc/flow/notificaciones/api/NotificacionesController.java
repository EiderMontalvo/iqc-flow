package pe.com.iqc.flow.notificaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.notificaciones.ServicioNotificaciones;
import pe.com.iqc.flow.notificaciones.ServicioNotificaciones.NotificacionRespuesta;

/**cada usuario solo ve y marca sus propias notificaciones*/
@RestController
@RequestMapping("/api/v1/notificaciones")
@Tag(name = "Notificaciones")
public class NotificacionesController {
  private final ServicioNotificaciones servicio;

  public NotificacionesController(ServicioNotificaciones servicio) { this.servicio = servicio; }

  @GetMapping
  @Operation(summary = "Mis notificaciones, de la más reciente a la más antigua")
  public RespuestaPaginada<NotificacionRespuesta> listar(
      @RequestParam(defaultValue = "false") boolean soloNoLeidas,
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(servicio.listarPropias(soloNoLeidas, pagina, tamano));
  }

  @GetMapping("/contador")
  @Operation(summary = "Cantidad de notificaciones sin leer (para el ícono del panel)")
  public Map<String, Long> contador() { return Map.of("noLeidas", servicio.contarNoLeidas()); }

  @PostMapping("/{id}/lectura")
  @Operation(summary = "Marca una notificación como leída")
  public NotificacionRespuesta marcarLeida(@PathVariable UUID id) { return servicio.marcarLeida(id); }

  @PostMapping("/lectura")
  @Operation(summary = "Marca todas mis notificaciones como leídas")
  public Map<String, Integer> marcarTodas() { return Map.of("marcadas", servicio.marcarTodasLeidas()); }
}
