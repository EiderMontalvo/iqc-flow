package pe.com.iqc.flow.correos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.correos.EnvioCorreo.Estado;
import pe.com.iqc.flow.correos.application.ServicioCorreos;

@RestController
@Tag(name = "Correos")
public class CorreosController {
  private final ServicioCorreos servicio;

  public CorreosController(ServicioCorreos servicio) { this.servicio = servicio; }

  public record SolicitudPrueba(@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+") String cuenta, @NotBlank @Email @Size(max = 254) String para) {
  }

  @GetMapping("/api/v1/correos/estado")
  @Operation(summary = "Las cuentas de correo del servidor y si tienen credenciales, sin sus contraseñas (correos:configurar)")
  public List<Estado> estados() { return servicio.estados(); }

  @PostMapping("/api/v1/correos/prueba")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Envía un correo de prueba a la dirección indicada con la cuenta elegida (correos:configurar)")
  public void prueba(@Valid @RequestBody SolicitudPrueba solicitud) { servicio.enviarPrueba(solicitud.cuenta(), solicitud.para()); }
}
