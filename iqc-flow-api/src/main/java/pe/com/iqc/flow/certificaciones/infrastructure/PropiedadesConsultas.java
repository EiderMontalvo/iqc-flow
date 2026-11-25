package pe.com.iqc.flow.certificaciones.infrastructure;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 *bases públicas de consulta (iqc.consultas.*), una por país de marca: PE, EC, CO, PA... (ADR-043). Una base sin url
 *queda deshabilitada y sus certificados se registran con error hasta configurarla. En producción cada una es otra base
 *de datos; el usuario de estas conexiones es el único que escribe en ellas
 */
@ConfigurationProperties("iqc.consultas")
public record PropiedadesConsultas(
    /**conexión por código de país (ISO 3166-1 alfa-2)*/
    Map<String, Conexion> bases,
    /**cada cuánto se buscan cambios para publicar*/
    @DefaultValue("PT1M") Duration intervalo) {

  public PropiedadesConsultas {
    // Las variables de entorno llegan en minúsculas (IQC_CONSULTAS_BASES_EC_URL → "ec")
    TreeMap<String, Conexion> normalizadas = new TreeMap<>();
    if (bases != null) bases.forEach((pais, c) -> normalizadas.put(pais.toUpperCase(Locale.ROOT), c));
    bases = Map.copyOf(normalizadas);
  }

  public record Conexion(
      String url,
      String usuario,
      String clave,
      /**esquema de las tablas públicas: "public" en una base propia; otro nombre si comparte la base (solo desarrollo)*/
      @DefaultValue("public") String esquema) {

    public boolean habilitada() { return url != null && !url.isBlank(); }
  }
}
