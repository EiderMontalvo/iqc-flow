package pe.com.iqc.flow.compartido.bitacora;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/bitacora")
@Tag(name = "Bitácora", description = "Consulta de la bitácora de actividad")
public class BitacoraController {
  private final Bitacora bitacora;
  private final JsonMapper json;

  public BitacoraController(Bitacora bitacora, JsonMapper json) {
    this.bitacora = bitacora;
    this.json = json;
  }

  @GetMapping
  @Operation(summary = "Consulta la bitácora de la organización, de la más reciente a la más antigua")
  public RespuestaPaginada<RegistroBitacoraRespuesta> consultar(
      @RequestParam(required = false) String entidadTipo,
      @RequestParam(required = false) String entidadId,
      @RequestParam(required = false) String accion,
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(bitacora.consultar(entidadTipo, entidadId, accion, pagina, tamano).map(this::aRespuesta));
  }

  private RegistroBitacoraRespuesta aRespuesta(RegistroBitacora r) {
    return new RegistroBitacoraRespuesta(r.getId(), r.getOcurridoEn(), r.getActorUsuarioId(), r.getTipoActor(),
        r.getOrigen(), r.getAccion(), r.getEntidadTipo(), r.getEntidadId(), leer(r.getEstadoAnterior()),
        leer(r.getEstadoNuevo()), r.getResultado(), r.getMotivo(), r.getIdCorrelacion(), r.getDireccionIp());
  }

  private JsonNode leer(String valor) { return valor == null ? null : json.readTree(valor); }

  public record RegistroBitacoraRespuesta(
      UUID id,
      Instant ocurridoEn,
      UUID actorUsuarioId,
      TipoActor tipoActor,
      OrigenOperacion origen,
      String accion,
      String entidadTipo,
      String entidadId,
      JsonNode estadoAnterior,
      JsonNode estadoNuevo,
      ResultadoOperacion resultado,
      String motivo,
      String idCorrelacion,
      String direccionIp) {
  }
}
