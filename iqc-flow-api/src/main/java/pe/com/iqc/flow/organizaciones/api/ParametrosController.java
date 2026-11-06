package pe.com.iqc.flow.organizaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion.ParametroRespuesta;

@RestController
@RequestMapping("/api/v1/organizacion/parametros")
@Tag(name = "Parámetros de la organización")
public class ParametrosController {
  private final ParametrosOrganizacion parametros;

  public ParametrosController(ParametrosOrganizacion parametros) { this.parametros = parametros; }

  public record SolicitudParametro(@NotBlank String valor) {
  }

  @GetMapping
  @Operation(summary = "Parámetros con su valor vigente y los valores admitidos")
  public List<ParametroRespuesta> listar() { return parametros.listar(); }

  @PutMapping("/{clave}")
  @Operation(summary = "Cambia un parámetro (organizacion:configurar); queda en la bitácora")
  public ParametroRespuesta cambiar(@PathVariable ParametroOrganizacion clave,
      @Valid @RequestBody SolicitudParametro solicitud) {
    return parametros.cambiar(clave, solicitud.valor());
  }
}
