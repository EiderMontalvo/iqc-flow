package pe.com.iqc.flow.certificaciones.infrastructure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *conexiones a las bases públicas de consulta, una por país de marca (ADR-043). No se exponen como DataSource ni como
 *gestor de transacciones de Spring para no interferir con la base interna: solo las usa el publicador. Al arrancar
 *aplica a cada una sus migraciones (db/consultas)
 */
@Component
public class BasesConsultas {
  private static final Logger log = LoggerFactory.getLogger(BasesConsultas.class);

  private final Map<String, BaseConsulta> bases;

  public BasesConsultas(PropiedadesConsultas propiedades) {
    TreeMap<String, BaseConsulta> abiertas = new TreeMap<>();
    // Varios países pueden compartir base (p. ej. una base LATAM para EC, CO y PA): una sola conexión y una sola
    // migración por base física
    java.util.Map<String, BaseConsulta> porDestino = new java.util.HashMap<>();
    propiedades.bases().forEach((pais, conexion) -> {
      if (conexion == null || !conexion.habilitada()) {
        log.warn("Base de consulta de {} sin configurar: sus certificados no se publican", pais);
        return;
      }
      String destino = conexion.url() + "|" + conexion.usuario() + "|" + conexion.esquema();
      abiertas.put(pais, porDestino.computeIfAbsent(destino, d -> new BaseConsulta(pais, conexion)));
    });
    if (abiertas.isEmpty()) log.warn("Publicación a la consulta pública deshabilitada: no hay iqc.consultas.bases");
    this.bases = Collections.unmodifiableMap(abiertas);
  }

  public boolean habilitada() { return !bases.isEmpty(); }

  /**países con base configurada*/
  public Set<String> paises() { return bases.keySet(); }

  /**true si los dos países publican en la misma base física (no hay que retirar al cambiar de marca entre ellos)*/
  public boolean comparten(String paisA, String paisB) {
    BaseConsulta a = de(paisA).orElse(null);
    return a != null && a == de(paisB).orElse(null);
  }

  public Optional<BaseConsulta> de(String pais) {
    return pais == null ? Optional.empty() : Optional.ofNullable(bases.get(pais.toUpperCase(Locale.ROOT)));
  }

  @PreDestroy
  void cerrar() { bases.values().stream().distinct().forEach(BaseConsulta::cerrar); }

  /**base pública de un país*/
  public static final class BaseConsulta {
    private final String pais;
    private final String esquema;
    private final HikariDataSource origen;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;

    BaseConsulta(String pais, PropiedadesConsultas.Conexion conexion) {
      this.pais = pais;
      this.esquema = conexion.esquema();
      HikariConfig config = new HikariConfig();
      config.setPoolName("consultas-" + pais.toLowerCase(Locale.ROOT));
      config.setJdbcUrl(conexion.url());
      config.setUsername(conexion.usuario());
      config.setPassword(conexion.clave());
      // Solo escribe la pasada de publicación: conexiones bajo demanda, sin mantener inactivas
      config.setMaximumPoolSize(3);
      config.setMinimumIdle(0);
      config.setIdleTimeout(120_000);
      config.setSchema(esquema);
      this.origen = new HikariDataSource(config);
      try {
        Flyway.configure()
            .dataSource(origen)
            .locations("classpath:db/consultas")
            .schemas(esquema)
            .defaultSchema(esquema)
            .createSchemas(true)
            .load()
            .migrate();
      } catch (RuntimeException e) {
        origen.close();
        throw e;
      }
      this.jdbc = new JdbcTemplate(origen);
      this.transacciones = new TransactionTemplate(new DataSourceTransactionManager(origen));
    }

    public String pais() { return pais; }

    public String esquema() { return esquema; }

    /**ejecuta la escritura en una transacción de esta base*/
    public void enTransaccion(Consumer<JdbcTemplate> trabajo) {
      transacciones.executeWithoutResult(estado -> trabajo.accept(jdbc));
    }

    public JdbcTemplate jdbc() { return jdbc; }

    void cerrar() { origen.close(); }
  }
}
