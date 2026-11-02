package pe.com.iqc.flow.compartido.api;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/publico")
public class EstadoApiController {
  @GetMapping("/estado")
  public Map<String, String> estado() { return Map.of("servicio", "IQC Flow", "estado", "ok", "version", "0.1.0"); }
}
