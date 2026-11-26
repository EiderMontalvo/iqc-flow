package pe.com.iqc.flow.correos.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 *las palabras que valen la pena de una pregunta en lenguaje natural para buscarla en la bandeja: sin tildes ni mayúsculas, sin las
 *palabras vacías («qué», «llegaron», «esta»…) ni las muy cortas, y sin repetir. Con ellas se buscan los correos; si no queda ninguna, la
 *pregunta es general («¿qué llegó hoy?») y se miran los más recientes
 */
public final class TerminosDeBusqueda {
  private static final int LARGO_MINIMO = 4;
  private static final int MAXIMO = 8;
  /**palabras de pregunta y de uso general que no distinguen un correo de otro*/
  private static final Set<String> VACIAS = Set.of("que", "cual", "cuales", "cuando", "como", "donde", "quien", "quienes", "cuanto", "cuantos", "cuantas",
      "para", "pero", "porque", "sobre", "entre", "desde", "hasta", "este", "esta", "estos", "estas", "ese", "esa", "esos", "esas", "aquel", "aquella",
      "llego", "llegaron", "llegado", "recibi", "recibimos", "recibio", "recibieron", "hay", "habia", "tengo", "tenemos", "tiene", "tienen", "hoy",
      "ayer", "semana", "mes", "dime", "dame", "muestrame", "muestra", "busca", "buscar", "correo", "correos", "mensaje", "mensajes", "algun", "alguna",
      "algunos", "algunas", "todos", "todas", "todo", "cada", "tambien", "ademas", "favor", "gracias", "ultimo", "ultimos", "ultima", "ultimas", "reciente",
      "recientes", "tipo", "cuenta", "cuentas", "buzon", "bandeja", "pendiente", "pendientes", "resumen", "resume", "resumir", "lista", "listar");
  /**quien pregunta por la publicidad la quiere ver: de lo contrario la consulta no la mira*/
  private static final List<String> PIDE_PUBLICIDAD = List.of("publicidad", "spam", "promocion", "promociones", "no deseado", "no deseados", "masivo", "masivos");

  private TerminosDeBusqueda() {
  }

  /**hasta ocho palabras, en el orden en que aparecen*/
  public static Set<String> de(String pregunta) {
    Set<String> terminos = new LinkedHashSet<>();
    for (String palabra : NormalizadorTextoCorreo.palabras(NormalizadorTextoCorreo.normalizar(pregunta))) {
      if (palabra.length() >= LARGO_MINIMO && !VACIAS.contains(palabra)) terminos.add(palabra);
      if (terminos.size() == MAXIMO) break;
    }
    return terminos;
  }

  public static boolean pidePublicidad(String pregunta) {
    String normalizada = NormalizadorTextoCorreo.normalizar(pregunta);
    return PIDE_PUBLICIDAD.stream().anyMatch(normalizada::contains);
  }
}
