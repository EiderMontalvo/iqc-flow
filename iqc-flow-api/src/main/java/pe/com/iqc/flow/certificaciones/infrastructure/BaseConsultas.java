package pe.com.iqc.flow.certificaciones.infrastructure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *conexión a la base pública de consulta. No se expone como DataSource ni como gestor de transacciones de Spring
 *para no interferir con la base interna: solo la usa el publicador. Al arrancar aplica sus migraciones (db/consultas)
 */
@Component
public class BaseConsultas {
  private static final Logger log = LoggerFactory.getLogger(BaseConsultas.class);

  private final PropiedadesConsultas propiedades;
  private final HikariDataSource origen;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacciones;

  public BaseConsultas(PropiedadesConsultas propiedades) {
    this.propiedades = propiedades;
    if (!propiedades.habilitada()) {
      log.warn("Publicación a la consulta pública deshabilitada: falta iqc.consultas.url");
      this.origen = null;
      this.jdbc = null;
      this.transacciones = null;
      return;
    }
    HikariConfig config = new HikariConfig();
    config.setPoolName("consultas");
    config.setJdbcUrl(propiedades.url());
    config.setUsername(propiedades.usuario());
    config.setPassword(propiedades.clave());
    config.setMaximumPoolSize(3);
    config.setSchema(propiedades.esquema());
    this.origen = new HikariDataSource(config);
    Flyway.configure()
        .dataSource(origen)
        .locations("classpath:db/consultas")
        .schemas(propiedades.esquema())
        .defaultSchema(propiedades.esquema())
        .createSchemas(true)
        .load()
        .migrate();
    this.jdbc = new JdbcTemplate(origen);
    this.transacciones = new TransactionTemplate(new DataSourceTransactionManager(origen));
  }

  public boolean habilitada() { return origen != null; }

  /**ejecuta la escritura en una transacción de la base pública*/
  public void enTransaccion(Consumer<JdbcTemplate> trabajo) {
    if (!habilitada()) throw new IllegalStateException("La base de consulta no está configurada");
    transacciones.executeWithoutResult(estado -> trabajo.accept(jdbc));
  }

  public JdbcTemplate jdbc() {
    if (!habilitada()) throw new IllegalStateException("La base de consulta no está configurada");
    return jdbc;
  }

  public String esquema() { return propiedades.esquema(); }

  @PreDestroy
  void cerrar() {
    if (origen != null) origen.close();
  }
}
