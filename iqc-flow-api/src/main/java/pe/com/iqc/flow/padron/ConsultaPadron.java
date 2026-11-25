package pe.com.iqc.flow.padron;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 *Consulta de RUC y DNI en el padrón de SUNAT (servicio público que usa su propio formulario web). No es una API
 *oficial: puede cambiar o caerse, así que la respuesta se cachea, tiene tiempo límite y un fallo nunca bloquea el
 *formulario: la persona escribe los datos a mano
 */
@Service
public class ConsultaPadron {
  private static final Logger log = LoggerFactory.getLogger(ConsultaPadron.class);
  private static final Pattern RUC = Pattern.compile("(10|15|16|17|20)\\d{9}");
  private static final Pattern DNI = Pattern.compile("\\d{8}");
  private static final Duration VIGENCIA_CACHE = Duration.ofHours(12);
  private static final int MAXIMO_CACHE = 5000;

  public record DatosRuc(String ruc, String razonSocial, String direccion, String distrito, String provincia, String departamento) {
  }

  /**nombreCompleto: nombres y apellidos en el orden y la forma en que se escriben en los certificados*/
  public record DatosDni(String dni, String nombres, String apellidos, String nombreCompleto) {
  }

  private record EnCache(Object valor, Instant hasta) {
  }

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final Map<String, EnCache> cache = new ConcurrentHashMap<>();
  private final JsonMapper json;
  private final Clock reloj;
  private final String base;

  public ConsultaPadron(JsonMapper json, Clock reloj,
      @Value("${iqc.padron.url:https://ww1.sunat.gob.pe/ol-ti-itfisdenreg/itfisdenreg.htm}") String base) {
    this.json = json;
    this.reloj = reloj;
    this.base = base;
  }

  @PreAuthorize("isAuthenticated()")
  public Optional<DatosRuc> ruc(String numero) {
    String n = numero == null ? "" : numero.trim();
    if (!RUC.matcher(n).matches()) throw new ReglaNegocioException("RUC_INVALIDO", "El RUC tiene 11 dígitos y empieza con 10, 15, 16, 17 o 20.");
    return cacheado("ruc:" + n, () -> leerRuc(n, consultar("obtenerDatosRuc", "nroRuc", n), json));
  }

  @PreAuthorize("isAuthenticated()")
  public Optional<DatosDni> dni(String numero) {
    String n = numero == null ? "" : numero.trim();
    if (!DNI.matcher(n).matches()) throw new ReglaNegocioException("DNI_INVALIDO", "El DNI tiene 8 dígitos.");
    return cacheado("dni:" + n, () -> leerDni(n, consultar("obtenerDatosDni", "numDocumento", n), json));
  }

  @SuppressWarnings("unchecked")
  private <T> Optional<T> cacheado(String clave, java.util.function.Supplier<Optional<T>> consulta) {
    Instant ahora = Instant.now(reloj);
    EnCache c = cache.get(clave);
    if (c != null && c.hasta().isAfter(ahora)) return Optional.ofNullable((T) c.valor());
    Optional<T> r = consulta.get();
    if (cache.size() >= MAXIMO_CACHE) cache.clear();
    cache.put(clave, new EnCache(r.orElse(null), ahora.plus(VIGENCIA_CACHE)));
    return r;
  }

  private String consultar(String accion, String parametro, String numero) {
    URI uri = URI.create(base + "?accion=" + accion + "&" + parametro + "=" + URLEncoder.encode(numero, StandardCharsets.UTF_8));
    HttpRequest pedido = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
        .header("User-Agent", "Mozilla/5.0 (IQC Flow)").header("Accept", "application/json, text/plain, */*").GET().build();
    try {
      HttpResponse<String> r = http.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (r.statusCode() != 200) throw new IOException("SUNAT respondió " + r.statusCode());
      return r.body();
    } catch (IOException e) {
      log.warn("Consulta al padrón de SUNAT fallida ({}): {}", accion, e.getMessage());
      throw noDisponible();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw noDisponible();
    }
  }

  private static ReglaNegocioException noDisponible() {
    return new ReglaNegocioException("PADRON_NO_DISPONIBLE", "SUNAT no respondió. Escribe los datos a mano o vuelve a intentar en un momento.");
  }

  /**datos del RUC; vacío si SUNAT no lo tiene. Paquete: lo prueba ConsultaPadronTest*/
  static Optional<DatosRuc> leerRuc(String ruc, String cuerpo, JsonMapper json) {
    JsonNode fila = primera(cuerpo, json);
    if (fila == null) return Optional.empty();
    String razon = limpio(fila.path("apenomdenunciado").asString(""));
    if (razon.isEmpty()) return Optional.empty();
    return Optional.of(new DatosRuc(ruc, razon, limpio(fila.path("direstablecimiento").asString("")),
        limpio(fila.path("desdistrito").asString("")), limpio(fila.path("desprovincia").asString("")),
        limpio(fila.path("desdepartamento").asString(""))));
  }

  /**SUNAT devuelve «APELLIDOS,NOMBRES» en mayúsculas; se entrega también «Nombres Apellidos» en tipo título*/
  static Optional<DatosDni> leerDni(String dni, String cuerpo, JsonMapper json) {
    JsonNode fila = primera(cuerpo, json);
    if (fila == null) return Optional.empty();
    String crudo = limpio(fila.path("nombresapellidos").asString(""));
    if (crudo.isEmpty()) return Optional.empty();
    int coma = crudo.indexOf(',');
    String apellidos = coma < 0 ? "" : crudo.substring(0, coma).trim(), nombres = coma < 0 ? crudo : crudo.substring(coma + 1).trim();
    return Optional.of(new DatosDni(dni, titulo(nombres), titulo(apellidos), titulo((nombres + " " + apellidos).trim())));
  }

  private static JsonNode primera(String cuerpo, JsonMapper json) {
    try {
      JsonNode raiz = json.readTree(cuerpo);
      JsonNode lista = raiz.path("lista");
      return lista.isArray() && !lista.isEmpty() ? lista.get(0) : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String limpio(String texto) { return texto.replaceAll("\\s+", " ").trim(); }

  /**«DE LA CRUZ» → «de la Cruz»: partículas en minúscula salvo al inicio*/
  static String titulo(String texto) {
    var particulas = java.util.Set.of("de", "del", "la", "las", "los", "y", "e");
    String[] partes = texto.toLowerCase(Locale.forLanguageTag("es-PE")).split(" ");
    return Arrays.stream(partes).filter(p -> !p.isEmpty()).map(new java.util.function.Function<String, String>() {
      private boolean primera = true;
      public String apply(String p) {
        boolean minuscula = !primera && particulas.contains(p);
        primera = false;
        return minuscula ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1);
      }
    }).collect(Collectors.joining(" "));
  }
}
