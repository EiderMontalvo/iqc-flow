package pe.com.iqc.flow.ia.infrastructure;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.anthropic.models.beta.messages.StructuredTextBlock;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.AsistenteCorreos;
import pe.com.iqc.flow.correos.TipoCorreo;

/**
 *el asistente de la bandeja con Claude. Dos modelos según la tarea, para no pagar de más: uno ligero para clasificar y resumir (corto,
 *barato y rápido) y otro mejor para redactar a un cliente y para contestar preguntas sobre varios correos. Salida estructurada: el
 *modelo devuelve exactamente los campos del registro, que luego se validan. Los registros de este adaptador nunca llevan el contenido de
 *un correo ni la clave; solo el código de estado del proveedor
 */
@Component
class AsistenteCorreosClaude implements AsistenteCorreos {
  private static final Logger log = LoggerFactory.getLogger(AsistenteCorreosClaude.class);

  record RespuestaTipo(@JsonPropertyDescription("COTIZACION, PAGO, AUDITORIA, CERTIFICADO, RECLAMO, PUBLICIDAD u OTRO") String tipo) {
  }

  record RespuestaResumen(
      @JsonPropertyDescription("Resumen del correo en dos o tres frases") String resumen,
      @JsonPropertyDescription("Hasta cinco puntos clave, una frase cada uno") List<String> puntosClave,
      @JsonPropertyDescription("Qué debería hacer la persona del equipo, en una frase; vacío si no corresponde") String accionSugerida,
      @JsonPropertyDescription("COTIZACION, PAGO, AUDITORIA, CERTIFICADO, RECLAMO, PUBLICIDAD u OTRO") String tipoSugerido) {
  }

  record RespuestaBorrador(
      @JsonPropertyDescription("Asunto de la respuesta, en una línea") String asunto,
      @JsonPropertyDescription("Cuerpo del correo en texto plano") String cuerpo) {
  }

  record RespuestaConsulta(
      @JsonPropertyDescription("La respuesta a la pregunta, apoyada solo en los correos del contexto") String respuesta,
      @JsonPropertyDescription("Los id de los correos del contexto en los que se apoya la respuesta; vacío si ninguno") List<String> idsUsados) {
  }

  private record Salida<T>(T valor, Consumo consumo) {
  }

  private final ClienteClaude claude;
  private final String modeloLigero;
  private final String modeloRedaccion;

  AsistenteCorreosClaude(ClienteClaude claude, @Value("${iqc.ia.modelo-correos-ligero:claude-haiku-5-5}") String modeloLigero,
      @Value("${iqc.ia.modelo-correos:claude-sonnet-5-5}") String modeloRedaccion) {
    this.claude = claude;
    this.modeloLigero = modeloLigero;
    this.modeloRedaccion = modeloRedaccion;
  }

  @Override
  public boolean disponible() { return claude.disponible(); }

  @Override
  public Optional<TipoCorreo> clasificar(String asunto, String texto) {
    if (!disponible()) return Optional.empty();
    try {
      return pedir(modeloLigero, PromptsCorreo.CLASIFICAR, PromptsCorreo.clasificar(asunto, texto), 60, RespuestaTipo.class)
          .map(s -> tipo(s.valor().tipo())).filter(t -> t != null && t != TipoCorreo.OTRO);
    } catch (ReglaNegocioException limite) {
      return Optional.empty(); // al recibir correos no se detiene nada por el límite del proveedor: queda por reglas
    }
  }

  @Override
  public Optional<Resumen> resumir(String asunto, String texto) {
    return pedir(modeloLigero, PromptsCorreo.RESUMIR, PromptsCorreo.resumir(asunto, texto), 800, RespuestaResumen.class)
        .map(s -> new Resumen(s.valor().resumen(), s.valor().puntosClave(), s.valor().accionSugerida(), tipo(s.valor().tipoSugerido()), s.consumo()));
  }

  @Override
  public Optional<Borrador> redactarRespuesta(CorreoContexto correo, String instrucciones, Tono tono) {
    return pedir(modeloRedaccion, PromptsCorreo.redactar(tono), PromptsCorreo.redactar(correo, instrucciones), 1_500, RespuestaBorrador.class)
        .map(s -> new Borrador(s.valor().asunto(), s.valor().cuerpo(), s.consumo()));
  }

  @Override
  public Optional<Consulta> consultar(String pregunta, List<CorreoContexto> correos) {
    return pedir(modeloRedaccion, PromptsCorreo.CONSULTAR, PromptsCorreo.consultar(pregunta, correos), 1_500, RespuestaConsulta.class)
        .map(s -> new Consulta(s.valor().respuesta(), s.valor().idsUsados(), s.consumo()));
  }

  /**el tipo que dijo el modelo, o null si no es uno de los nuestros: nunca se acepta un valor inventado*/
  private static TipoCorreo tipo(String valor) {
    if (valor == null) return null;
    try {
      return TipoCorreo.valueOf(valor.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private <T> Optional<Salida<T>> pedir(String modelo, String sistema, String usuario, long maxTokens, Class<T> clase) {
    if (!disponible()) return Optional.empty();
    StructuredMessageCreateParams<T> params = MessageCreateParams.builder().model(modelo).maxTokens(maxTokens).outputConfig(clase).system(sistema)
        .addUserMessageOfContentBlockParams(List.of(BetaContentBlockParam.ofText(usuario))).build();
    try {
      StructuredMessage<T> r = claude.cliente().beta().messages().create(params);
      if (r.stopReason().map(BetaStopReason.REFUSAL::equals).orElse(false)) {
        log.warn("El modelo {} no atendió una consulta de correo (refusal)", modelo);
        return Optional.empty();
      }
      Consumo consumo = new Consumo(modelo, r.usage().inputTokens(), r.usage().outputTokens());
      return r.content().stream().flatMap(b -> b.text().stream()).map(StructuredTextBlock::text).findFirst().map(valor -> new Salida<>(valor, consumo));
    } catch (AnthropicServiceException e) {
      if (e.statusCode() == 429) throw new ReglaNegocioException("IA_LIMITE", "El proveedor de IA está limitando el uso. Inténtalo en unos minutos.");
      log.warn("La API de Claude respondió {} a una consulta de correo", e.statusCode());
      return Optional.empty();
    } catch (AnthropicException e) {
      log.warn("No se pudo contactar con la API de Claude para una consulta de correo ({})", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
