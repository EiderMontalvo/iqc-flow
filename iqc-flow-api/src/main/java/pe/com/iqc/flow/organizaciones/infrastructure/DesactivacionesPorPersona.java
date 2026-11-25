package pe.com.iqc.flow.organizaciones.infrastructure;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**opciones de MASTER apagadas para una persona concreta (tabla parametros_desactivados_usuario, V15)*/
@Repository
public class DesactivacionesPorPersona {
  private final JdbcTemplate jdbc;

  public DesactivacionesPorPersona(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public boolean desactivado(UUID usuarioId, String clave) {
    Integer n = jdbc.queryForObject("SELECT count(*) FROM parametros_desactivados_usuario WHERE usuario_id = ? AND clave = ?",
        Integer.class, usuarioId, clave);
    return n != null && n > 0;
  }

  /**personas de la organización a las que se les apagó la opción*/
  public List<UUID> personas(UUID organizacionId, String clave) {
    return jdbc.queryForList("""
        SELECT usuario_id FROM parametros_desactivados_usuario WHERE organizacion_id = ? AND clave = ?
        ORDER BY desactivado_en""", UUID.class, organizacionId, clave);
  }

  /**true si el usuario existe en la organización (la tabla de usuarios es de identidad: solo se comprueba la pertenencia)*/
  public boolean perteneceA(UUID organizacionId, UUID usuarioId) {
    Integer n = jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE id = ? AND organizacion_id = ?",
        Integer.class, usuarioId, organizacionId);
    return n != null && n > 0;
  }

  /**devuelve true si cambió algo*/
  public boolean desactivar(UUID organizacionId, UUID usuarioId, String clave, UUID actor, Instant ahora) {
    return jdbc.update("""
        INSERT INTO parametros_desactivados_usuario (usuario_id, clave, organizacion_id, desactivado_por, desactivado_en)
        VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""", usuarioId, clave, organizacionId, actor, Timestamp.from(ahora)) > 0;
  }

  public boolean reactivar(UUID usuarioId, String clave) {
    return jdbc.update("DELETE FROM parametros_desactivados_usuario WHERE usuario_id = ? AND clave = ?", usuarioId, clave) > 0;
  }
}
