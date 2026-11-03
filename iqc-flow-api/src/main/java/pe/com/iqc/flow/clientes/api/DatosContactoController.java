package pe.com.iqc.flow.clientes.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.clientes.application.DtosClientes.ContactoRespuesta;
import pe.com.iqc.flow.clientes.application.DtosClientes.DireccionRespuesta;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudContacto;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudDireccion;
import pe.com.iqc.flow.clientes.application.ServicioDatosContacto;

/**contactos y direcciones de un cliente. Requieren poder editar el cliente (clientes:escribir y ser su responsable)*/
@RestController
@RequestMapping("/api/v1/clientes/{clienteId}")
@Tag(name = "Clientes: contactos y direcciones")
public class DatosContactoController {
  private final ServicioDatosContacto servicio;

  public DatosContactoController(ServicioDatosContacto servicio) { this.servicio = servicio; }

  @PostMapping("/contactos")
  @Operation(summary = "Agrega un contacto; si es principal, los demás dejan de serlo")
  public ResponseEntity<ContactoRespuesta> agregarContacto(@PathVariable UUID clienteId,
      @Valid @RequestBody SolicitudContacto solicitud) {
    ContactoRespuesta creado = servicio.agregarContacto(clienteId, solicitud);
    return ResponseEntity.created(URI.create("/api/v1/clientes/" + clienteId + "/contactos/" + creado.id())).body(creado);
  }

  @PutMapping("/contactos/{contactoId}")
  @Operation(summary = "Reemplaza los datos de un contacto (envía la versión vigente)")
  public ContactoRespuesta actualizarContacto(@PathVariable UUID clienteId, @PathVariable UUID contactoId,
      @Valid @RequestBody SolicitudContacto solicitud) {
    return servicio.actualizarContacto(clienteId, contactoId, solicitud);
  }

  @DeleteMapping("/contactos/{contactoId}")
  @Operation(summary = "Elimina un contacto")
  public ResponseEntity<Void> eliminarContacto(@PathVariable UUID clienteId, @PathVariable UUID contactoId) {
    servicio.eliminarContacto(clienteId, contactoId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/direcciones")
  @Operation(summary = "Agrega una dirección; solo puede haber una FISCAL")
  public ResponseEntity<DireccionRespuesta> agregarDireccion(@PathVariable UUID clienteId,
      @Valid @RequestBody SolicitudDireccion solicitud) {
    DireccionRespuesta creada = servicio.agregarDireccion(clienteId, solicitud);
    return ResponseEntity.created(URI.create("/api/v1/clientes/" + clienteId + "/direcciones/" + creada.id())).body(creada);
  }

  @PutMapping("/direcciones/{direccionId}")
  @Operation(summary = "Reemplaza los datos de una dirección (envía la versión vigente)")
  public DireccionRespuesta actualizarDireccion(@PathVariable UUID clienteId, @PathVariable UUID direccionId,
      @Valid @RequestBody SolicitudDireccion solicitud) {
    return servicio.actualizarDireccion(clienteId, direccionId, solicitud);
  }

  @DeleteMapping("/direcciones/{direccionId}")
  @Operation(summary = "Elimina una dirección")
  public ResponseEntity<Void> eliminarDireccion(@PathVariable UUID clienteId, @PathVariable UUID direccionId) {
    servicio.eliminarDireccion(clienteId, direccionId);
    return ResponseEntity.noContent().build();
  }
}
