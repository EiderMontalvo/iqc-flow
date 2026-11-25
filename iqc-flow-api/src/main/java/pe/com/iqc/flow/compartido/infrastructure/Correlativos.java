package pe.com.iqc.flow.compartido.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 *numeración correlativa por organización, serie y año (COT-2026-0001, CON-2026-0001…). El incremento es atómico
 *en PostgreSQL (INSERT … ON CONFLICT … RETURNING): dos personas a la vez nunca reciben el mismo número
 */
@Component
public class Correlativos {
  private final JdbcTemplate jdbc;

  public Correlativos(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /**siguiente número de la serie en ese año, ya reservado (parte de la transacción en curso)*/
  @Transactional(propagation = Propagation.MANDATORY)
  public int siguiente(UUID organizacionId, String serie, int anio) {
    Integer n = jdbc.queryForObject("""
        INSERT INTO correlativos (organizacion_id, serie, anio, ultimo) VALUES (?, ?, ?, 1)
        ON CONFLICT (organizacion_id, serie, anio) DO UPDATE SET ultimo = correlativos.ultimo + 1
        RETURNING ultimo""", Integer.class, organizacionId, serie, anio);
    return n == null ? 1 : n;
  }

  /**número con formato SERIE-AAAA-0001*/
  @Transactional(propagation = Propagation.MANDATORY)
  public String siguienteCodigo(UUID organizacionId, String serie, int anio) {
    return "%s-%d-%04d".formatted(serie, anio, siguiente(organizacionId, serie, anio));
  }
}
