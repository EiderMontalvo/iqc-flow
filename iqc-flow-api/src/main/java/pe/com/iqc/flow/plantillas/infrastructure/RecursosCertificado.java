package pe.com.iqc.flow.plantillas.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**fondos, imágenes y fuentes de {@code resources/certificados}; se leen una vez y quedan en memoria*/
@Component
class RecursosCertificado {
  private static final String RAIZ = "certificados/";
  private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

  byte[] bytes(String ruta) {
    return cache.computeIfAbsent(ruta, r -> {
      try (var entrada = new ClassPathResource(RAIZ + r).getInputStream()) {
        return entrada.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException("Falta el recurso de certificado " + r, e);
      }
    });
  }
}
