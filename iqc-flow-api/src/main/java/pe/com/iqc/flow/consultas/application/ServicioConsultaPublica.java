package pe.com.iqc.flow.consultas.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.consultas.infrastructure.BasesPortal;

/**
 *consulta pública: lo que abre un QR y la verificación por documento y código. Solo lee las bases públicas. Solo se
 *muestra lo publicado; los datos internos (expedientes, contactos, auditorías) nunca llegan a estas bases
 */
@Service
public class ServicioConsultaPublica {
  private final BasesPortal bases;
  private final Clock reloj;

  public ServicioConsultaPublica(BasesPortal bases, Clock reloj) {
    this.bases = bases;
    this.reloj = reloj;
  }

  public record Empresarial(UUID id, String codigo, String titular, String documento, String norma, String esquema,
      String organismoAcreditador, String alcance, String estado, String situacion, LocalDate fechaOtorgamiento,
      LocalDate fechaEmision, LocalDate fechaExpiracion, String marca, String marcaPais, boolean tienePdf) {
  }

  public record Personal(UUID id, String codigo, String nombres, String tipoDocumento, String documento, String curso,
      String norma, String subtitulo, String descripcion, BigDecimal duracionHoras, String modalidad, LocalDate fechaInicio,
      LocalDate fechaFin, LocalDate fechaEmision, String entrenador, String estado, String marca, String marcaPais,
      UUID tokenPdf, boolean tienePdf) {
  }

  /**tipo EMPRESA o PERSONA; redireccion: dirección del portal que corresponde a un QR antiguo de otra marca*/
  public record Resultado(String tipo, Empresarial empresarial, Personal personal, String redireccion) {
  }

  public record Pdf(byte[] contenido, String tipoContenido, String nombre, String urlExterna) {
  }

  public Resultado porQr(String portal, String token) {
    JdbcTemplate jdbc = base(portal);
    String t = token == null ? "" : token.strip();
    if (t.isEmpty() || t.length() > 80) throw noEncontrado(t);
    Optional<Empresarial> e = empresariales(jdbc, "c.id = (SELECT certificado_id FROM qr_publicos WHERE token = ?)", t).stream().findFirst();
    if (e.isPresent()) return new Resultado("EMPRESA", e.get(), null, null);
    List<String> destino = jdbc.queryForList("SELECT url_destino FROM qr_redirecciones WHERE token = ?", String.class, t);
    if (!destino.isEmpty()) return new Resultado(null, null, null, destino.get(0));
    throw noEncontrado(t);
  }

  public Resultado verificar(String portal, String documento, String codigo) {
    String doc = documento == null ? "" : documento.strip().toUpperCase(Locale.ROOT);
    String cod = codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
    if (doc.length() < 5 || doc.length() > 20 || cod.length() < 4 || cod.length() > 64) {
      throw new ReglaNegocioException("DATOS_INVALIDOS", "Ingresa el documento (RUC, DNI u otro) y el código del certificado.");
    }
    JdbcTemplate jdbc = base(portal);
    List<Empresarial> e = empresariales(jdbc,
        "upper(translate(c.codigo, '–', '-')) = translate(?, '–', '-') AND upper(c.titular_numero_documento) = ?", cod, doc);
    if (!e.isEmpty()) return new Resultado("EMPRESA", e.get(0), null, null);
    List<Personal> p = jdbc.query("""
        SELECT * FROM personales_publicos WHERE upper(codigo) = ? AND upper(titular_numero_documento) = ?""",
        (rs, i) -> personal(rs), cod, doc);
    if (!p.isEmpty()) return new Resultado("PERSONA", null, p.get(0), null);
    throw new RecursoNoEncontradoException("Certificado", cod);
  }

  public Pdf pdfEmpresarial(String portal, UUID id) {
    JdbcTemplate jdbc = base(portal);
    List<Pdf> propio = jdbc.query("SELECT contenido, tipo_contenido, nombre FROM documentos_publicos WHERE certificado_id = ?",
        (rs, i) -> new Pdf(rs.getBytes(1), rs.getString(2), rs.getString(3), null), id);
    if (!propio.isEmpty()) return propio.get(0);
    // Certificados importados: su PDF sigue en el almacén anterior (ADR-005)
    List<String> legado = jdbc.queryForList("SELECT url_documento_legado FROM certificados_publicos WHERE id = ?", String.class, id);
    if (!legado.isEmpty() && legado.get(0) != null && legado.get(0).startsWith("https://storage.googleapis.com/")) {
      return new Pdf(null, null, null, legado.get(0));
    }
    throw new RecursoNoEncontradoException("PDF del certificado", id);
  }

  public Pdf pdfPersonal(String portal, UUID token) {
    JdbcTemplate jdbc = base(portal);
    List<Pdf> pdf = jdbc.query("""
        SELECT d.contenido, d.tipo_contenido FROM documentos_personales_publicos d
        JOIN personales_publicos p ON p.id = d.certificado_id
        WHERE p.token_pdf = ? AND p.estado = 'EMITIDO'""",
        (rs, i) -> new Pdf(rs.getBytes(1), rs.getString(2), "certificado-personal.pdf", null), token);
    if (pdf.isEmpty()) throw new RecursoNoEncontradoException("PDF del certificado", token);
    return pdf.get(0);
  }

  private List<Empresarial> empresariales(JdbcTemplate jdbc, String condicion, Object... args) {
    LocalDate hoy = LocalDate.now(reloj);
    return jdbc.query("SELECT * FROM certificados_publicos c WHERE " + condicion, (rs, i) -> {
      LocalDate exp = fecha(rs, "fecha_expiracion");
      String estado = rs.getString("estado");
      String situacion = !"EMITIDO".equals(estado) ? estado : exp == null ? "SIN_FECHA" : exp.isBefore(hoy) ? "VENCIDO" : "VIGENTE";
      return new Empresarial((UUID) rs.getObject("id"), rs.getString("codigo"), rs.getString("titular_razon_social"),
          rs.getString("titular_numero_documento"), rs.getString("norma_designacion"), rs.getString("esquema"),
          rs.getString("organismo_acreditador"), rs.getString("alcance"), estado, situacion, fecha(rs, "fecha_otorgamiento"),
          fecha(rs, "fecha_emision"), exp, rs.getString("marca_nombre"), rs.getString("marca_pais"),
          rs.getBoolean("tiene_pdf") || rs.getString("url_documento_legado") != null);
    }, args);
  }

  private static Personal personal(ResultSet rs) throws SQLException {
    String norma = rs.getString("norma_referencia");
    String anio = rs.getString("norma_anio");
    return new Personal((UUID) rs.getObject("id"), rs.getString("codigo"), rs.getString("titular_nombres"),
        rs.getString("titular_tipo_documento"), rs.getString("titular_numero_documento"), rs.getString("curso"),
        norma == null ? null : anio == null ? norma : norma + ":" + anio, rs.getString("subtitulo"), rs.getString("descripcion"),
        rs.getBigDecimal("duracion_horas"), rs.getString("modalidad"), fecha(rs, "fecha_inicio"), fecha(rs, "fecha_fin"),
        fecha(rs, "fecha_emision"), rs.getString("entrenador"), rs.getString("estado"), rs.getString("marca_nombre"),
        rs.getString("marca_pais"), (UUID) rs.getObject("token_pdf"), rs.getBoolean("tiene_pdf"));
  }

  private static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
    java.sql.Date d = rs.getDate(columna);
    return d == null ? null : d.toLocalDate();
  }

  private JdbcTemplate base(String portal) {
    return bases.de(portal).orElseThrow(() -> new RecursoNoEncontradoException("Portal de consulta", portal));
  }

  private static RecursoNoEncontradoException noEncontrado(String token) { return new RecursoNoEncontradoException("Código QR", token); }
}
