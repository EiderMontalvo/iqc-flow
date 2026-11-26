package pe.com.iqc.flow.correos.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pe.com.iqc.flow.correos.AsistenteCorreos.Tono;
import pe.com.iqc.flow.correos.TipoCorreo;

/**lo que entra y sale de la API del asistente de IA; la validación de lo que entra se hace aquí, en el borde*/
public final class DtosAsistente {
  private DtosAsistente() {
  }

  /**`motivo` solo cuando no está disponible: por qué (apagada en Configuración, o falta la clave en el servidor)*/
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record EstadoAsistente(boolean disponible, String motivo) {
  }

  /**`accionSugerida` y `tipoSugerido` pueden faltar; el tipo sugerido no cambia el del correo: lo decide una persona*/
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ResumenCorreo(String resumen, List<String> puntosClave, String accionSugerida, TipoCorreo tipoSugerido) {
  }

  /**`instrucciones` son de quien redacta (no del correo); sin tono, FORMAL*/
  public record SolicitudBorrador(@Size(max = 500) String instrucciones, Tono tono) {
  }

  /**siempre un borrador: no se envía solo, la persona lo revisa y lo manda por el endpoint de respuestas*/
  public record BorradorRespuesta(String asunto, String cuerpo) {
  }

  /**las fechas incluyen el día entero (hora de Lima), como en el listado de la bandeja*/
  public record SolicitudConsulta(@NotBlank @Size(max = 500) String pregunta, @Size(max = 40) String cuenta, LocalDate desde, LocalDate hasta) {
  }

  public record CorreoCitado(UUID id, String asunto, String remitenteCorreo, Instant fecha) {
  }

  public record RespuestaConsulta(String respuesta, List<CorreoCitado> correos) {
  }
}
