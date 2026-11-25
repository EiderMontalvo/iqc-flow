package pe.com.iqc.flow.indicadores.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;

/**
 *indicadores para los gráficos del panel. Cada sección se calcula solo si el usuario tiene el permiso de su área;
 *las demás vuelven en null. Las series por mes y por semana se completan con ceros para que el eje sea continuo
 */
@Service
public class ServicioIndicadores {
  /**una barra del gráfico: clave estable (mes, código), etiqueta legible y cantidad*/
  public record Punto(String clave, String etiqueta, long cantidad) {
  }

  public record Certificados(List<Punto> vencimientosPorMes, List<Punto> emisionesPorMes, List<Punto> personalesPorMes,
      List<Punto> vigentesPorNorma, List<Punto> vigentesPorEsquema) {
  }

  /**deTodos: false cuando el usuario solo ve sus propias tareas (sin seguimientos:leer-todos)*/
  public record Seguimientos(boolean deTodos, List<Punto> abiertasPorTipo, List<Punto> abiertasPorPrioridad,
      List<Punto> completadasPorSemana) {
  }

  public record Clientes(List<Punto> porEstado, List<Punto> altasPorMes) {
  }

  public record Indicadores(LocalDate calculadoAl, Certificados certificados, Seguimientos seguimientos, Clientes clientes) {
  }

  private static final String ZONA = "America/Lima";
  private static final int MESES = 12;
  private static final int SEMANAS = 8;
  private static final List<String> ABIERTAS = List.of("PENDIENTE", "EN_CURSO", "EN_ESPERA");
  private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMM yy", Locale.forLanguageTag("es-PE"));
  private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE"));

  private final JdbcTemplate jdbc;
  private final ActorActual actorActual;
  private final Clock reloj;

  public ServicioIndicadores(JdbcTemplate jdbc, ActorActual actorActual, Clock reloj) {
    this.jdbc = jdbc;
    this.actorActual = actorActual;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  public Indicadores calcular() {
    UsuarioAutenticado actor = actorActual.requerido();
    LocalDate hoy = LocalDate.now(reloj);
    UUID org = actor.organizacionId();
    boolean veCertificados = actorActual.tienePermiso("certificados:leer") || actorActual.tienePermiso("expedientes:leer");
    return new Indicadores(hoy,
        veCertificados ? certificados(org, hoy) : null,
        actorActual.tienePermiso("seguimientos:leer") ? seguimientos(org, actor.id(), hoy) : null,
        actorActual.tienePermiso("clientes:leer") ? clientes(org, hoy) : null);
  }

  private Certificados certificados(UUID org, LocalDate hoy) {
    YearMonth actual = YearMonth.from(hoy);
    // Próximos 12 meses, desde el mes en curso: lo que hay que renovar o recertificar
    List<Punto> vencimientos = porMes(actual, MESES, jdbc.queryForList("""
        SELECT to_char(fecha_expiracion, 'YYYY-MM') AS clave, count(*) AS cantidad FROM certificados
        WHERE organizacion_id = ? AND estado = 'EMITIDO' AND fecha_expiracion >= ? AND fecha_expiracion <= ?
        GROUP BY 1""", org, hoy, actual.plusMonths(MESES - 1).atEndOfMonth()));
    // Últimos 12 meses, hasta el mes en curso
    YearMonth desde = actual.minusMonths(MESES - 1);
    List<Punto> emisiones = porMes(desde, MESES, jdbc.queryForList("""
        SELECT to_char(fecha_emision, 'YYYY-MM') AS clave, count(*) AS cantidad FROM certificados
        WHERE organizacion_id = ? AND estado NOT IN ('BORRADOR', 'REVISADO', 'CANCELADO')
          AND fecha_emision >= ? AND fecha_emision <= ?
        GROUP BY 1""", org, desde.atDay(1), hoy));
    List<Punto> personales = porMes(desde, MESES, jdbc.queryForList("""
        SELECT to_char(fecha_emision, 'YYYY-MM') AS clave, count(*) AS cantidad FROM certificados_personales
        WHERE organizacion_id = ? AND fecha_emision >= ? AND fecha_emision <= ?
        GROUP BY 1""", org, desde.atDay(1), hoy));
    List<Punto> porNorma = jdbc.query("""
        SELECT c.norma_codigo AS clave, coalesce(n.nombre_corto, c.norma_codigo) AS etiqueta, count(*) AS cantidad
        FROM certificados c LEFT JOIN normas n ON n.codigo = c.norma_codigo
        WHERE c.organizacion_id = ? AND c.estado = 'EMITIDO' AND (c.fecha_expiracion IS NULL OR c.fecha_expiracion >= ?)
        GROUP BY 1, 2 ORDER BY 3 DESC, 2""", (rs, i) -> new Punto(rs.getString(1), rs.getString(2), rs.getLong(3)), org, hoy);
    List<Punto> porEsquema = jdbc.query("""
        SELECT c.esquema_codigo AS clave, coalesce(e.nombre, c.esquema_codigo) AS etiqueta, count(*) AS cantidad
        FROM certificados c LEFT JOIN esquemas_acreditacion e ON e.codigo = c.esquema_codigo
        WHERE c.organizacion_id = ? AND c.estado = 'EMITIDO' AND (c.fecha_expiracion IS NULL OR c.fecha_expiracion >= ?)
        GROUP BY 1, 2 ORDER BY 3 DESC, 2""", (rs, i) -> new Punto(rs.getString(1), rs.getString(2), rs.getLong(3)), org, hoy);
    return new Certificados(vencimientos, emisiones, personales, porNorma, porEsquema);
  }

  private Seguimientos seguimientos(UUID org, UUID usuario, LocalDate hoy) {
    boolean deTodos = actorActual.tienePermiso("seguimientos:leer-todos");
    // Sin leer-todos, solo las tareas de las que el usuario es responsable (igual que los contadores de «Hoy»)
    String alcance = deTodos ? "" : " AND t.responsable_usuario_id = ?";
    List<Object> base = new ArrayList<>(List.of(org));
    if (!deTodos) base.add(usuario);
    String abiertas = " AND t.estado IN ('" + String.join("', '", ABIERTAS) + "')";

    List<Punto> porTipo = jdbc.query("""
        SELECT t.tipo_codigo, coalesce(tt.nombre, t.tipo_codigo), count(*)
        FROM tareas_seguimiento t LEFT JOIN tipos_tarea tt ON tt.codigo = t.tipo_codigo
        WHERE t.organizacion_id = ?%s%s
        GROUP BY 1, 2 ORDER BY 3 DESC, 2""".formatted(alcance, abiertas),
        (rs, i) -> new Punto(rs.getString(1), rs.getString(2), rs.getLong(3)), base.toArray());

    Map<String, Long> prioridades = new HashMap<>();
    jdbc.query("SELECT t.prioridad, count(*) FROM tareas_seguimiento t WHERE t.organizacion_id = ?" + alcance + abiertas
        + " GROUP BY 1", rs -> { prioridades.put(rs.getString(1), rs.getLong(2)); }, base.toArray());
    List<Punto> porPrioridad = new ArrayList<>();
    for (String[] p : new String[][] {{"URGENTE", "Urgente"}, {"ALTA", "Alta"}, {"NORMAL", "Normal"}, {"BAJA", "Baja"}}) {
      porPrioridad.add(new Punto(p[0], p[1], prioridades.getOrDefault(p[0], 0L)));
    }

    // Últimas 8 semanas (lunes a domingo, hora de Lima), la actual incluida
    LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(SEMANAS - 1);
    List<Object> args = new ArrayList<>(base);
    args.add(lunes);
    Map<String, Long> cerradas = new HashMap<>();
    String cierre = "(t.cerrada_en AT TIME ZONE '" + ZONA + "')";
    jdbc.query("""
        SELECT to_char(date_trunc('week', %1$s), 'YYYY-MM-DD'), count(*) FROM tareas_seguimiento t
        WHERE t.organizacion_id = ?%2$s AND t.estado = 'COMPLETADA' AND %1$s::date >= ?
        GROUP BY 1""".formatted(cierre, alcance), rs -> { cerradas.put(rs.getString(1), rs.getLong(2)); }, args.toArray());
    List<Punto> semanas = new ArrayList<>();
    for (int i = 0; i < SEMANAS; i++) {
      LocalDate s = lunes.plusWeeks(i);
      semanas.add(new Punto(s.toString(), s.format(DIA), cerradas.getOrDefault(s.toString(), 0L)));
    }
    return new Seguimientos(deTodos, porTipo, porPrioridad, semanas);
  }

  private Clientes clientes(UUID org, LocalDate hoy) {
    Map<String, Long> estados = new HashMap<>();
    jdbc.query("SELECT estado, count(*) FROM clientes WHERE organizacion_id = ? AND archivado_en IS NULL GROUP BY 1",
        rs -> { estados.put(rs.getString(1), rs.getLong(2)); }, org);
    List<Punto> porEstado = new ArrayList<>();
    for (String[] e : new String[][] {{"ACTIVO", "Activos"}, {"PROSPECTO", "Prospectos"}, {"INACTIVO", "Inactivos"}}) {
      porEstado.add(new Punto(e[0], e[1], estados.getOrDefault(e[0], 0L)));
    }
    YearMonth desde = YearMonth.from(hoy).minusMonths(MESES - 1);
    // Los clientes importados del panel anterior no cuentan como altas: su fecha de creación es la de la importación
    String creado = "(creado_en AT TIME ZONE '" + ZONA + "')";
    List<Punto> altas = porMes(desde, MESES, jdbc.queryForList("""
        SELECT to_char(%1$s, 'YYYY-MM') AS clave, count(*) AS cantidad FROM clientes
        WHERE organizacion_id = ? AND fuente_legado IS NULL AND %1$s::date >= ?
        GROUP BY 1""".formatted(creado), org, desde.atDay(1)));
    return new Clientes(porEstado, altas);
  }

  /**serie continua de N meses desde «desde»; los meses sin registros van en cero*/
  private static List<Punto> porMes(YearMonth desde, int meses, List<Map<String, Object>> filas) {
    Map<String, Long> cantidades = new HashMap<>();
    for (Map<String, Object> f : filas) cantidades.put((String) f.get("clave"), ((Number) f.get("cantidad")).longValue());
    List<Punto> serie = new ArrayList<>();
    for (int i = 0; i < meses; i++) {
      YearMonth m = desde.plusMonths(i);
      String clave = m.toString();
      serie.add(new Punto(clave, m.atDay(1).format(MES).replace(".", ""), cantidades.getOrDefault(clave, 0L)));
    }
    return serie;
  }
}
