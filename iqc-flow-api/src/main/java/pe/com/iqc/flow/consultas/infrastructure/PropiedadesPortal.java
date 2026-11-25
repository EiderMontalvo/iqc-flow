package pe.com.iqc.flow.consultas.infrastructure;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**conexiones de solo lectura del portal (iqc.portal-consultas.portales.PE / LATAM)*/
@ConfigurationProperties("iqc.portal-consultas")
public record PropiedadesPortal(Map<String, Conexion> portales, @DefaultValue("60") int consultasPorMinuto) {
  public PropiedadesPortal {
    TreeMap<String, Conexion> n = new TreeMap<>();
    if (portales != null) portales.forEach((k, v) -> n.put(k.toUpperCase(Locale.ROOT), v));
    portales = Map.copyOf(n);
  }

  public record Conexion(String url, String usuario, String clave, @DefaultValue("public") String esquema) {
    public boolean habilitada() { return url != null && !url.isBlank(); }
  }
}
