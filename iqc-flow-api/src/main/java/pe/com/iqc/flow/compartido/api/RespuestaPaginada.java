package pe.com.iqc.flow.compartido.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**formato estable de las respuestas paginadas de la API*/
public record RespuestaPaginada<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
  public static <T> RespuestaPaginada<T> de(Page<T> pagina) {
    return new RespuestaPaginada<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
        pagina.getTotalElements(), pagina.getTotalPages());
  }
}
