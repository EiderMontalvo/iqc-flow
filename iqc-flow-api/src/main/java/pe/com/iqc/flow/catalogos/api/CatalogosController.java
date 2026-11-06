package pe.com.iqc.flow.catalogos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.EsquemaCatalogo;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaCatalogo;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.PaisCatalogo;

/**catálogos de referencia, disponibles para cualquier usuario con sesión*/
@RestController
@RequestMapping("/api/v1/catalogos")
@Tag(name = "Catálogos")
public class CatalogosController {
  private final ServicioCatalogos servicio;

  public CatalogosController(ServicioCatalogos servicio) { this.servicio = servicio; }

  @GetMapping("/paises")
  @Operation(summary = "Países con documento fiscal, moneda, impuesto y nombres de los niveles de dirección")
  public List<PaisCatalogo> paises() { return servicio.paises(); }

  @GetMapping("/esquemas-acreditacion")
  @Operation(summary = "Esquemas de acreditación: INACAL, SAE, IQC Global")
  public List<EsquemaCatalogo> esquemas() { return servicio.esquemas(); }

  @GetMapping("/normas")
  @Operation(summary = "Normas con su código de sistema (01–05) y sus ediciones")
  public List<NormaCatalogo> normas() { return servicio.normas(); }
}
