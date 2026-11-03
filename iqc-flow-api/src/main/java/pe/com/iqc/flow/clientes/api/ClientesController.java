package pe.com.iqc.flow.clientes.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
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
import pe.com.iqc.flow.clientes.application.DtosClientes.ClienteDetalle;
import pe.com.iqc.flow.clientes.application.DtosClientes.ClienteResumen;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudActualizarCliente;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudArchivo;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudCrearCliente;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudReasignarCliente;
import pe.com.iqc.flow.clientes.application.DtosClientes.VerificacionDocumento;
import pe.com.iqc.flow.clientes.application.ServicioClientes;
import pe.com.iqc.flow.clientes.domain.EstadoCliente;
import pe.com.iqc.flow.clientes.domain.TipoDocumento;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;

@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes")
public class ClientesController {
  private final ServicioClientes servicio;

  public ClientesController(ServicioClientes servicio) { this.servicio = servicio; }

  @GetMapping
  @Operation(summary = "Busca clientes y prospectos por texto (sin distinguir tildes), estado y responsable (clientes:leer)")
  public RespuestaPaginada<ClienteResumen> buscar(
      @RequestParam(required = false) String texto,
      @RequestParam(required = false) EstadoCliente estado,
      @RequestParam(required = false) UUID responsableId,
      @RequestParam(defaultValue = "false") boolean incluirArchivados,
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(servicio.buscar(texto, estado, responsableId, incluirArchivados, pagina, tamano));
  }

  @GetMapping("/verificacion-documento")
  @Operation(summary = "Comprueba si un documento es válido y si ya pertenece a un cliente vigente (clientes:leer)")
  public VerificacionDocumento verificarDocumento(
      @RequestParam(defaultValue = "PE") String codigoPais,
      @RequestParam TipoDocumento tipoDocumento,
      @RequestParam String numeroDocumento) {
    return servicio.verificarDocumento(codigoPais, tipoDocumento, numeroDocumento);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Ficha del cliente con contactos y direcciones (clientes:leer)")
  public ClienteDetalle obtener(@PathVariable UUID id) { return servicio.obtener(id); }

  @PostMapping
  @Operation(summary = "Registra un cliente o prospecto (clientes:escribir)")
  public ResponseEntity<ClienteDetalle> crear(@Valid @RequestBody SolicitudCrearCliente solicitud) {
    ClienteDetalle creado = servicio.crear(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/clientes/" + creado.id())).body(creado);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Modifica un cliente propio, o cualquiera con clientes:escribir-todos")
  public ClienteDetalle actualizar(@PathVariable UUID id, @Valid @RequestBody SolicitudActualizarCliente solicitud) {
    return servicio.actualizar(id, solicitud);
  }

  @PutMapping("/{id}/responsable")
  @Operation(summary = "Cambia el responsable comercial (clientes:reasignar)")
  public ClienteDetalle reasignar(@PathVariable UUID id, @Valid @RequestBody SolicitudReasignarCliente solicitud) {
    return servicio.reasignar(id, solicitud);
  }

  @PostMapping("/{id}/archivo")
  @Operation(summary = "Archiva el cliente: deja de aparecer en las búsquedas, conserva su historial (clientes:archivar)")
  public ClienteDetalle archivar(@PathVariable UUID id, @Valid @RequestBody SolicitudArchivo solicitud) {
    return servicio.archivar(id, solicitud);
  }

  @PostMapping("/{id}/restauracion")
  @Operation(summary = "Restaura un cliente archivado (clientes:archivar)")
  public ClienteDetalle restaurar(@PathVariable UUID id, @Valid @RequestBody SolicitudArchivo solicitud) {
    return servicio.restaurar(id, solicitud);
  }
}
