package pe.com.iqc.flow.ia.infrastructure;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *el cliente de la API de Anthropic que comparten los adaptadores del módulo. La clave va solo en el entorno del servicio
 *(ANTHROPIC_API_KEY), nunca en el código ni en un registro; sin ella, disponible() es false y nadie llama a nadie
 */
@Component
class ClienteClaude {
  private final String clave;
  private volatile AnthropicClient cliente;

  ClienteClaude(@Value("${ANTHROPIC_API_KEY:}") String clave) { this.clave = clave == null ? "" : clave.trim(); }

  boolean disponible() { return !clave.isEmpty(); }

  AnthropicClient cliente() {
    if (cliente == null) {
      synchronized (this) {
        if (cliente == null) cliente = AnthropicOkHttpClient.builder().apiKey(clave).timeout(Duration.ofSeconds(120)).maxRetries(2).build();
      }
    }
    return cliente;
  }
}
