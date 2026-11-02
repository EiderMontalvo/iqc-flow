package pe.com.iqc.flow.identidad.api;

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
import pe.com.iqc.flow.identidad.application.DtosIdentidad.RolRespuesta;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.SolicitudActualizarUsuario;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.SolicitudAsignarRoles;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.SolicitudCrearUsuario;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.UsuarioRespuesta;
import pe.com.iqc.flow.identidad.application.ServicioUsuarios;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Usuarios y roles")
public class UsuariosController {
  private final ServicioUsuarios servicio;

  public UsuariosController(ServicioUsuarios servicio) { this.servicio = servicio; }

  @GetMapping("/usuarios")
  @Operation(summary = "Lista los usuarios de la organización (permiso usuarios:leer)")
  public RespuestaPaginada<UsuarioRespuesta> listar(
      @RequestParam(required = false) String texto,
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(servicio.listar(texto, pagina, tamano));
  }

  @GetMapping("/usuarios/{id}")
  @Operation(summary = "Obtiene un usuario (permiso usuarios:leer)")
  public UsuarioRespuesta obtener(@PathVariable UUID id) { return servicio.obtener(id); }

  @PostMapping("/usuarios")
  @Operation(summary = "Crea un usuario que deberá cambiar su contraseña al ingresar (permiso usuarios:gestionar)")
  public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody SolicitudCrearUsuario solicitud) {
    UsuarioRespuesta creado = servicio.crear(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/usuarios/" + creado.id())).body(creado);
  }

  @PatchMapping("/usuarios/{id}")
  @Operation(summary = "Modifica nombre o estado de un usuario (permiso usuarios:gestionar)")
  public UsuarioRespuesta actualizar(@PathVariable UUID id, @Valid @RequestBody SolicitudActualizarUsuario solicitud) {
    return servicio.actualizar(id, solicitud);
  }

  @PutMapping("/usuarios/{id}/roles")
  @Operation(summary = "Reemplaza los roles de un usuario (permiso roles:asignar)")
  public UsuarioRespuesta asignarRoles(@PathVariable UUID id, @Valid @RequestBody SolicitudAsignarRoles solicitud) {
    return servicio.asignarRoles(id, solicitud);
  }

  @GetMapping("/roles")
  @Operation(summary = "Lista los roles disponibles con sus permisos (permiso usuarios:leer)")
  public List<RolRespuesta> listarRoles() { return servicio.listarRoles(); }
}
