package pe.com.iqc.flow.indicadores.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.indicadores.application.ServicioIndicadores;
import pe.com.iqc.flow.indicadores.application.ServicioIndicadores.Indicadores;

@RestController
@RequestMapping("/api/v1/indicadores")
@Tag(name = "Indicadores del panel")
public class IndicadoresController {
  private final ServicioIndicadores servicio;

  public IndicadoresController(ServicioIndicadores servicio) { this.servicio = servicio; }

  @GetMapping
  @Operation(summary = "Series para los gráficos del inicio de cada rol; cada sección según los permisos del usuario")
  public Indicadores obtener() { return servicio.calcular(); }
}
