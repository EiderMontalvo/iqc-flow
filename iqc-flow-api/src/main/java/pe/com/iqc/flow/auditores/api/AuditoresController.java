package pe.com.iqc.flow.auditores.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.auditores.RolAuditor;
import pe.com.iqc.flow.auditores.application.ServicioAuditores;
import pe.com.iqc.flow.auditores.application.ServicioAuditores.AuditorRespuesta;
import pe.com.iqc.flow.auditores.application.ServicioAuditores.SolicitudAuditor;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;

@RestController
@RequestMapping("/api/v1/auditores")
@Tag(name = "Auditores")
public class AuditoresController {
  private final ServicioAuditores servicio;

  public AuditoresController(ServicioAuditores servicio) { this.servicio = servicio; }

  @GetMapping
  @Operation(summary = "Busca auditores por nombre, rol, norma en la que es competente y estado (auditores:leer)")
  public RespuestaPaginada<AuditorRespuesta> listar(
      @RequestParam(required = false) String texto,
      @RequestParam(required = false) RolAuditor rol,
      @RequestParam(required = false) String norma,
      @RequestParam(required = false) Boolean activo,
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(servicio.listar(texto, rol, norma, activo, pagina, tamano));
  }

  @GetMapping("/{id}")
  public AuditorRespuesta obtener(@PathVariable UUID id) { return servicio.obtener(id); }

  @PostMapping
  @Operation(summary = "Registra un auditor (auditores:gestionar)")
  public ResponseEntity<AuditorRespuesta> crear(@Valid @RequestBody SolicitudAuditor solicitud) {
    AuditorRespuesta creado = servicio.crear(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/auditores/" + creado.id())).body(creado);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Reemplaza los datos del auditor; envía la versión vigente (auditores:gestionar)")
  public AuditorRespuesta actualizar(@PathVariable UUID id, @Valid @RequestBody SolicitudAuditor solicitud) {
    return servicio.actualizar(id, solicitud);
  }
}
