package pe.com.iqc.flow.compartido.infrastructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 *exclusión mutua entre instancias de la API con un advisory lock de PostgreSQL (ADR-044). El bloqueo vive en una
 *conexión propia mientras dura el trabajo y se libera al terminar o si la instancia cae (al cerrarse la conexión)
 */
@Component
public class BloqueoDistribuido {
  private final DataSource origen;

  public BloqueoDistribuido(DataSource origen) { this.origen = origen; }

  /**ejecuta el trabajo si consigue el bloqueo; si otra instancia lo tiene, devuelve vacío sin esperar*/
  public <T> Optional<T> siLibre(long clave, Supplier<T> trabajo) {
    try (Connection conexion = origen.getConnection()) {
      if (!ejecutar(conexion, "SELECT pg_try_advisory_lock(?)", clave)) return Optional.empty();
      try {
        return Optional.ofNullable(trabajo.get());
      } finally {
        ejecutar(conexion, "SELECT pg_advisory_unlock(?)", clave);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("No se pudo tomar el bloqueo " + clave, e);
    }
  }

  private static boolean ejecutar(Connection conexion, String sql, long clave) throws SQLException {
    try (PreparedStatement st = conexion.prepareStatement(sql)) {
      st.setLong(1, clave);
      try (ResultSet rs = st.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }
}
