package pe.com.iqc.flow.padron.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.padron.ConsultaPadron;
import pe.com.iqc.flow.padron.ConsultaPadron.DatosDni;
import pe.com.iqc.flow.padron.ConsultaPadron.DatosRuc;

@RestController
@RequestMapping("/api/v1/padron")
@Tag(name = "Padrón (SUNAT)")
public class PadronController {
  private final ConsultaPadron padron;

  public PadronController(ConsultaPadron padron) { this.padron = padron; }

  @GetMapping("/ruc/{numero}")
  @Operation(summary = "Razón social y dirección de un RUC según SUNAT (para completar formularios)")
  public DatosRuc ruc(@PathVariable String numero) {
    return padron.ruc(numero).orElseThrow(() -> new RecursoNoEncontradoException("RUC", numero));
  }

  @GetMapping("/dni/{numero}")
  @Operation(summary = "Nombres y apellidos de un DNI según SUNAT (para completar formularios)")
  public DatosDni dni(@PathVariable String numero) {
    return padron.dni(numero).orElseThrow(() -> new RecursoNoEncontradoException("DNI", numero));
  }
}
