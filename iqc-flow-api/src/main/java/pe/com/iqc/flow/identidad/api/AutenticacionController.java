package pe.com.iqc.flow.identidad.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.SolicitudCambioContrasena;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.UsuarioActualRespuesta;
import pe.com.iqc.flow.identidad.application.ServicioAutenticacion;

/**
 *el inicio (POST /inicio-sesion, formulario con correo y contrasena) y el cierre de sesión
 *(POST /cierre-sesion) los atiende Spring Security; ver ConfiguracionSeguridad.
 */
@RestController
@RequestMapping("/api/v1/autenticacion")
@Tag(name = "Autenticación")
public class AutenticacionController {
  private final ServicioAutenticacion servicio;

  public AutenticacionController(ServicioAutenticacion servicio) { this.servicio = servicio; }

  @GetMapping("/csrf")
  @Operation(summary = "Emite la cookie XSRF-TOKEN; su valor debe enviarse en la cabecera X-XSRF-TOKEN")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("cabecera", token.getHeaderName(), "token", token.getToken());
  }

  @GetMapping("/yo")
  @Operation(summary = "Datos, roles y permisos del usuario autenticado")
  public UsuarioActualRespuesta yo() { return servicio.usuarioActual(); }

  @PostMapping("/cambio-contrasena")
  @Operation(summary = "Cambia la contraseña del usuario autenticado")
  public ResponseEntity<Void> cambiarContrasena(@Valid @RequestBody SolicitudCambioContrasena solicitud) {
    servicio.cambiarContrasena(solicitud);
    return ResponseEntity.noContent().build();
  }
}
