package pe.com.iqc.flow.cartas.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.com.iqc.flow.cartas.domain.TipoCarta;

/**tablas cartas y firmas_usuario (V21), con JdbcTemplate*/
@Repository
public class RepositorioCartas {
  private final JdbcTemplate jdbc;

  public RepositorioCartas(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record FilaCarta(UUID id, String numero, TipoCarta tipo, UUID clienteId, String asunto, LocalDate fecha,
      UUID firmanteId, String datos, UUID documentoPdfId, UUID creadoPor, Instant creadoEn) {
  }

  public record FilaFirma(UUID usuarioId, UUID documentoId, String nombreMostrado, String cargo, Instant actualizadoEn) {
  }

  public void insertar(UUID organizacionId, FilaCarta c) {
    jdbc.update("""
        INSERT INTO cartas (id, organizacion_id, numero, tipo, cliente_id, asunto, fecha, firmante_id, datos, documento_pdf_id, creado_por, creado_en)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)""", c.id(), organizacionId, c.numero(), c.tipo().name(), c.clienteId(),
        c.asunto(), c.fecha(), c.firmanteId(), c.datos(), c.documentoPdfId(), c.creadoPor(), Timestamp.from(c.creadoEn()));
  }

  public Optional<FilaCarta> buscar(UUID organizacionId, UUID id) {
    return jdbc.query(SELECT + " WHERE organizacion_id = ? AND id = ?", this::fila, organizacionId, id).stream().findFirst();
  }

  /**clientes: filtro por cliente (nulo = todos); texto ya resuelto a ids de clientes o número de carta*/
  public List<FilaCarta> listar(UUID organizacionId, UUID clienteId, List<UUID> clientesTexto, String numero, int pagina, int tamano) {
    StringBuilder sql = new StringBuilder(SELECT + " WHERE organizacion_id = ?");
    List<Object> args = new ArrayList<>(List.of(organizacionId));
    filtros(sql, args, clienteId, clientesTexto, numero);
    sql.append(" ORDER BY creado_en DESC LIMIT ? OFFSET ?");
    args.add(tamano);
    args.add(pagina * tamano);
    return jdbc.query(sql.toString(), this::fila, args.toArray());
  }

  public long contar(UUID organizacionId, UUID clienteId, List<UUID> clientesTexto, String numero) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM cartas WHERE organizacion_id = ?");
    List<Object> args = new ArrayList<>(List.of(organizacionId));
    filtros(sql, args, clienteId, clientesTexto, numero);
    Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0 : n;
  }

  private static void filtros(StringBuilder sql, List<Object> args, UUID clienteId, List<UUID> clientesTexto, String numero) {
    if (clienteId != null) {
      sql.append(" AND cliente_id = ?");
      args.add(clienteId);
    }
    if (clientesTexto != null) {
      sql.append(" AND (numero ILIKE ?");
      args.add("%" + numero + "%");
      if (!clientesTexto.isEmpty()) {
        sql.append(" OR cliente_id = ANY (?)");
        args.add(clientesTexto.toArray(UUID[]::new));
      }
      sql.append(")");
    }
  }

  public Optional<FilaFirma> firma(UUID organizacionId, UUID usuarioId) {
    return jdbc.query("SELECT usuario_id, documento_id, nombre_mostrado, cargo, actualizado_en FROM firmas_usuario WHERE organizacion_id = ? AND usuario_id = ?",
        (rs, i) -> new FilaFirma(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
            rs.getTimestamp(5).toInstant()), organizacionId, usuarioId).stream().findFirst();
  }

  public List<FilaFirma> firmas(UUID organizacionId) {
    return jdbc.query("SELECT usuario_id, documento_id, nombre_mostrado, cargo, actualizado_en FROM firmas_usuario WHERE organizacion_id = ?",
        (rs, i) -> new FilaFirma(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
            rs.getTimestamp(5).toInstant()), organizacionId);
  }

  public void guardarFirma(UUID organizacionId, FilaFirma f) {
    jdbc.update("""
        INSERT INTO firmas_usuario (usuario_id, organizacion_id, documento_id, nombre_mostrado, cargo, actualizado_en) VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (usuario_id) DO UPDATE SET documento_id = EXCLUDED.documento_id, nombre_mostrado = EXCLUDED.nombre_mostrado,
          cargo = EXCLUDED.cargo, actualizado_en = EXCLUDED.actualizado_en""",
        f.usuarioId(), organizacionId, f.documentoId(), f.nombreMostrado(), f.cargo(), Timestamp.from(f.actualizadoEn()));
  }

  public void borrarFirma(UUID organizacionId, UUID usuarioId) {
    jdbc.update("DELETE FROM firmas_usuario WHERE organizacion_id = ? AND usuario_id = ?", organizacionId, usuarioId);
  }

  private static final String SELECT = "SELECT id, numero, tipo, cliente_id, asunto, fecha, firmante_id, datos::text, documento_pdf_id, creado_por, creado_en FROM cartas";

  private FilaCarta fila(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    return new FilaCarta(rs.getObject(1, UUID.class), rs.getString(2), TipoCarta.valueOf(rs.getString(3)), rs.getObject(4, UUID.class),
        rs.getString(5), rs.getObject(6, LocalDate.class), rs.getObject(7, UUID.class), rs.getString(8), rs.getObject(9, UUID.class),
        rs.getObject(10, UUID.class), rs.getTimestamp(11).toInstant());
  }
}
