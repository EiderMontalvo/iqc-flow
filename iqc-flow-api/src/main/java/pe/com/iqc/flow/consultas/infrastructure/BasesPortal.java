package pe.com.iqc.flow.consultas.infrastructure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**conexiones de solo lectura a las bases públicas; no se registran como DataSource de Spring*/
@Component
public class BasesPortal {
  private static final Logger log = LoggerFactory.getLogger(BasesPortal.class);
  private final Map<String, HikariDataSource> origenes = new TreeMap<>();
  private final Map<String, JdbcTemplate> jdbc = new TreeMap<>();

  public BasesPortal(PropiedadesPortal propiedades) {
    propiedades.portales().forEach((portal, c) -> {
      if (c == null || !c.habilitada()) {
        log.warn("Portal de consulta {} sin base configurada", portal);
        return;
      }
      HikariConfig config = new HikariConfig();
      config.setPoolName("portal-" + portal.toLowerCase(Locale.ROOT));
      config.setJdbcUrl(c.url());
      config.setUsername(c.usuario());
      config.setPassword(c.clave());
      config.setSchema(c.esquema());
      config.setReadOnly(true);
      config.setMaximumPoolSize(5);
      config.setMinimumIdle(1);
      config.setIdleTimeout(300_000);
      config.setInitializationFailTimeout(-1);
      HikariDataSource origen = new HikariDataSource(config);
      origenes.put(portal, origen);
      jdbc.put(portal, new JdbcTemplate(origen));
    });
  }

  public Optional<JdbcTemplate> de(String portal) {
    return portal == null ? Optional.empty() : Optional.ofNullable(jdbc.get(portal.toUpperCase(Locale.ROOT)));
  }

  @PreDestroy
  void cerrar() { origenes.values().forEach(HikariDataSource::close); }
}
