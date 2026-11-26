package pe.com.iqc.flow.correos.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.correos.TipoCorreo;
import pe.com.iqc.flow.correos.application.DtosBandeja.AdjuntoCorreo;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoEnHilo;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoEnviado;
import pe.com.iqc.flow.correos.application.DtosAsistente.ResumenCorreo;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoResumen;
import pe.com.iqc.flow.correos.domain.EstadoCorreo;
import pe.com.iqc.flow.correos.domain.MensajeLeido;
import pe.com.iqc.flow.correos.domain.MensajeLeido.Adjunto;
import pe.com.iqc.flow.correos.domain.OrigenTipo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 *los correos recibidos y enviados, y hasta dónde se leyó cada buzón. Todo con JDBC y SQL explícito porque la bandeja filtra por muchos
 *criterios a la vez. Toda consulta lleva la organización: un correo de otra organización no existe para quien pregunta
 */
@Repository
public class RepositorioCorreos {
  private static final String COLUMNAS_RESUMEN = """
      id, cuenta, remitente_correo, remitente_nombre, asunto, fecha, tipo, origen_tipo, estado, responsable_id, cliente_id, version,
      jsonb_array_length(adjuntos) AS n_adjuntos, left(regexp_replace(texto, '\\s+', ' ', 'g'), 160) AS extracto""";
  private static final TypeReference<List<Adjunto>> LISTA_ADJUNTOS = new TypeReference<>() { };

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public RepositorioCorreos(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /**lo que se guarda al recibir un correo; `clienteId` si el remitente es contacto de un cliente*/
  public record NuevoCorreo(UUID organizacionId, String cuenta, long uidValidity, long uid, MensajeLeido mensaje, long tamanoBytes, TipoCorreo tipo, OrigenTipo origenTipo,
      String motivoTipo, UUID clienteId) {
  }

  /**un correo recibido con todas sus columnas*/
  public record FilaCorreo(UUID id, UUID organizacionId, String cuenta, String messageId, String inReplyTo, String referencias, String remitenteCorreo,
      String remitenteNombre, String para, String cc, String asunto, Instant fecha, String texto, boolean textoTruncado, List<Adjunto> adjuntos, TipoCorreo tipo,
      OrigenTipo origenTipo, String motivoTipo, EstadoCorreo estado, UUID responsableId, UUID clienteId, boolean tieneEml, long version) {
  }

  public record EstadoSincronizacion(Long uidValidity, long ultimoUid, Instant ultimaPasada, int nuevosUltima, String ultimoError) {
  }

  /**criterios ya resueltos para la consulta (las fechas son instantes: el servicio aplica la zona horaria)*/
  public record Criterios(String cuenta, TipoCorreo tipo, EstadoCorreo estado, String texto, Instant desde, Instant hastaExclusivo, boolean sinAtender, UUID responsableId,
      boolean incluirPublicidad) {
  }

  /**guarda el correo y su .eml; vacío si ya estaba (mismo UID o mismo Message-ID en la cuenta)*/
  @Transactional
  public Optional<UUID> guardar(NuevoCorreo n, byte[] eml) {
    MensajeLeido m = n.mensaje();
    UUID id = UUID.randomUUID();
    Optional<UUID> guardado = jdbc.sql("""
        INSERT INTO correos_recibidos (id, organizacion_id, cuenta, uid_validity, uid, message_id, in_reply_to, referencias, remitente_correo, remitente_nombre,
          para, cc, asunto, fecha, texto, texto_truncado, adjuntos, tamano_bytes, tipo, origen_tipo, motivo_tipo, cliente_id, tiene_eml)
        VALUES (:id, :org, :cuenta, :validez, :uid, :messageId, :inReplyTo, :referencias, :de, :nombre, :para, :cc, :asunto, :fecha, :texto, :truncado,
          CAST(:adjuntos AS jsonb), :tamano, :tipo, :origen, :motivo, :cliente, TRUE)
        ON CONFLICT DO NOTHING RETURNING id""")
        .param("id", id).param("org", n.organizacionId()).param("cuenta", n.cuenta()).param("validez", n.uidValidity()).param("uid", n.uid())
        .param("messageId", m.messageId()).param("inReplyTo", m.inReplyTo()).param("referencias", m.referencias()).param("de", m.remitenteCorreo())
        .param("nombre", m.remitenteNombre()).param("para", m.para()).param("cc", m.cc()).param("asunto", m.asunto()).param("fecha", Timestamp.from(m.fecha()))
        .param("texto", m.texto()).param("truncado", m.textoTruncado()).param("adjuntos", json.writeValueAsString(m.adjuntos())).param("tamano", n.tamanoBytes())
        .param("tipo", n.tipo().name()).param("origen", n.origenTipo().name()).param("motivo", n.motivoTipo()).param("cliente", n.clienteId())
        .query(UUID.class).optional();
    guardado.ifPresent(correo -> jdbc.sql("INSERT INTO correos_eml (correo_id, contenido) VALUES (:id, :eml)").param("id", correo).param("eml", eml).update());
    return guardado;
  }

  public EstadoSincronizacion estado(String cuenta) {
    return jdbc.sql("SELECT uid_validity, ultimo_uid, ultima_pasada, nuevos_ultima, ultimo_error FROM correos_sincronizacion WHERE cuenta = :cuenta")
        .param("cuenta", cuenta).query((rs, i) -> new EstadoSincronizacion(rs.getObject("uid_validity", Long.class), rs.getLong("ultimo_uid"),
            instante(rs, "ultima_pasada"), rs.getInt("nuevos_ultima"), rs.getString("ultimo_error"))).optional()
        .orElse(new EstadoSincronizacion(null, 0, null, 0, null));
  }

  public void guardarPasada(String cuenta, long uidValidity, long ultimoUid, int nuevos, String aviso, Instant ahora) {
    jdbc.sql("""
        INSERT INTO correos_sincronizacion (cuenta, uid_validity, ultimo_uid, ultima_pasada, nuevos_ultima, ultimo_error)
        VALUES (:cuenta, :validez, :uid, :ahora, :nuevos, :aviso)
        ON CONFLICT (cuenta) DO UPDATE SET uid_validity = :validez, ultimo_uid = :uid, ultima_pasada = :ahora, nuevos_ultima = :nuevos, ultimo_error = :aviso""")
        .param("cuenta", cuenta).param("validez", uidValidity).param("uid", ultimoUid).param("ahora", Timestamp.from(ahora)).param("nuevos", nuevos).param("aviso", aviso).update();
  }

  /**anota que la pasada falló sin tocar hasta dónde se había leído*/
  public void guardarError(String cuenta, String error, Instant ahora) {
    jdbc.sql("""
        INSERT INTO correos_sincronizacion (cuenta, ultima_pasada, nuevos_ultima, ultimo_error) VALUES (:cuenta, :ahora, 0, :error)
        ON CONFLICT (cuenta) DO UPDATE SET ultima_pasada = :ahora, nuevos_ultima = 0, ultimo_error = :error""")
        .param("cuenta", cuenta).param("ahora", Timestamp.from(ahora)).param("error", error).update();
  }

  public record Pagina(List<CorreoResumen> filas, long total) {
  }

  public Pagina listar(UUID org, Criterios c, int pagina, int tamano) {
    Map<String, Object> p = new HashMap<>();
    StringBuilder donde = new StringBuilder(" WHERE organizacion_id = :org");
    p.put("org", org);
    if (c.cuenta() != null) { donde.append(" AND cuenta = :cuenta"); p.put("cuenta", c.cuenta()); }
    if (c.tipo() != null) { donde.append(" AND tipo = :tipo"); p.put("tipo", c.tipo().name()); }
    else if (!c.incluirPublicidad()) donde.append(" AND tipo <> 'PUBLICIDAD'"); // la publicidad no ensucia la bandeja: solo sale si se pide
    if (c.estado() != null) { donde.append(" AND estado = :estado"); p.put("estado", c.estado().name()); }
    if (c.sinAtender()) donde.append(" AND estado IN ('NUEVO', 'EN_CURSO')");
    if (c.responsableId() != null) { donde.append(" AND responsable_id = :responsable"); p.put("responsable", c.responsableId()); }
    if (c.desde() != null) { donde.append(" AND fecha >= :desde"); p.put("desde", Timestamp.from(c.desde())); }
    if (c.hastaExclusivo() != null) { donde.append(" AND fecha < :hasta"); p.put("hasta", Timestamp.from(c.hastaExclusivo())); }
    if (c.texto() != null && !c.texto().isBlank()) {
      donde.append(" AND (asunto ILIKE :like OR remitente_correo ILIKE :like OR remitente_nombre ILIKE :like OR to_tsvector('spanish', texto) @@ plainto_tsquery('spanish', :q))");
      p.put("like", "%" + c.texto().strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
      p.put("q", c.texto().strip());
    }
    long total = jdbc.sql("SELECT count(*) FROM correos_recibidos" + donde).params(p).query(Long.class).single();
    p.put("limite", tamano);
    p.put("desplazamiento", (long) pagina * tamano);
    List<CorreoResumen> filas = jdbc.sql("SELECT " + COLUMNAS_RESUMEN + " FROM correos_recibidos" + donde + " ORDER BY fecha DESC, id DESC LIMIT :limite OFFSET :desplazamiento")
        .params(p).query((rs, i) -> resumen(rs)).list();
    return new Pagina(filas, total);
  }

  public Optional<FilaCorreo> buscar(UUID org, UUID id) {
    return jdbc.sql("SELECT * FROM correos_recibidos WHERE id = :id AND organizacion_id = :org").param("id", id).param("org", org).query((rs, i) -> fila(rs)).optional();
  }

  public Optional<byte[]> eml(UUID org, UUID id) {
    return jdbc.sql("SELECT e.contenido FROM correos_eml e JOIN correos_recibidos c ON c.id = e.correo_id WHERE c.id = :id AND c.organizacion_id = :org")
        .param("id", id).param("org", org).query((rs, i) -> rs.getBytes(1)).optional();
  }

  /**cambia lo que una persona decide de un correo; falso si la versión ya no es la que ella vio*/
  public boolean actualizar(UUID org, UUID id, long version, TipoCorreo tipo, OrigenTipo origen, String motivo, EstadoCorreo estado, UUID responsableId, UUID clienteId) {
    return jdbc.sql("""
        UPDATE correos_recibidos SET tipo = :tipo, origen_tipo = :origen, motivo_tipo = :motivo, estado = :estado, responsable_id = :responsable, cliente_id = :cliente,
          version = version + 1, actualizado_en = now()
        WHERE id = :id AND organizacion_id = :org AND version = :version""")
        .param("tipo", tipo.name()).param("origen", origen.name()).param("motivo", motivo).param("estado", estado.name()).param("responsable", responsableId)
        .param("cliente", clienteId).param("id", id).param("org", org).param("version", version).update() == 1;
  }

  public void marcarAtendido(UUID org, UUID id) {
    jdbc.sql("UPDATE correos_recibidos SET estado = 'ATENDIDO', version = version + 1, actualizado_en = now() WHERE id = :id AND organizacion_id = :org")
        .param("id", id).param("org", org).update();
  }

  public boolean usuarioActivo(UUID org, UUID usuarioId) {
    return jdbc.sql("SELECT count(*) FROM usuarios WHERE id = :id AND organizacion_id = :org AND estado = 'ACTIVO'").param("id", usuarioId).param("org", org)
        .query(Long.class).single() > 0;
  }

  /**los mensajes que responden a este o a los que este responde, del más antiguo al más nuevo; sin hilos complicados: solo un nivel hacia cada lado*/
  public List<CorreoEnHilo> hilo(FilaCorreo c) {
    List<String> anteriores = new ArrayList<>();
    if (c.inReplyTo() != null) anteriores.add(c.inReplyTo());
    if (c.referencias() != null) for (String r : c.referencias().split("\\s+")) if (!r.isBlank()) anteriores.add(r);
    List<String> condiciones = new ArrayList<>();
    Map<String, Object> p = new HashMap<>(Map.of("org", c.organizacionId(), "id", c.id()));
    if (!anteriores.isEmpty()) { condiciones.add("message_id IN (:anteriores)"); p.put("anteriores", anteriores); }
    if (c.messageId() != null) {
      condiciones.add("in_reply_to = :mensaje");
      condiciones.add("position(:mensajeEntre IN ' ' || coalesce(referencias, '') || ' ') > 0");
      p.put("mensaje", c.messageId());
      p.put("mensajeEntre", " " + c.messageId() + " ");
    }
    if (condiciones.isEmpty()) return List.of();
    return jdbc.sql("SELECT id, asunto, remitente_correo, fecha FROM correos_recibidos WHERE organizacion_id = :org AND id <> :id AND (" + String.join(" OR ", condiciones)
        + ") ORDER BY fecha LIMIT 20").params(p).query((rs, i) -> new CorreoEnHilo(rs.getObject("id", UUID.class), rs.getString("asunto"), rs.getString("remitente_correo"),
        instante(rs, "fecha"))).list();
  }

  public UUID guardarEnviado(UUID org, UUID correoRecibidoId, String cuenta, String messageId, String inReplyTo, String para, String asunto, String texto, UUID enviadoPor) {
    UUID id = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO correos_enviados (id, organizacion_id, correo_recibido_id, cuenta, message_id, in_reply_to, para, asunto, texto, enviado_por)
        VALUES (:id, :org, :recibido, :cuenta, :messageId, :inReplyTo, :para, :asunto, :texto, :por)""")
        .param("id", id).param("org", org).param("recibido", correoRecibidoId).param("cuenta", cuenta).param("messageId", messageId).param("inReplyTo", inReplyTo)
        .param("para", para).param("asunto", asunto).param("texto", texto).param("por", enviadoPor).update();
    return id;
  }

  public List<CorreoEnviado> respuestasDe(UUID org, UUID correoRecibidoId) {
    return jdbc.sql("""
        SELECT id, cuenta, para, asunto, texto, message_id, correo_recibido_id, enviado_por, enviado_en FROM correos_enviados
        WHERE organizacion_id = :org AND correo_recibido_id = :id ORDER BY enviado_en""").param("org", org).param("id", correoRecibidoId)
        .query((rs, i) -> new CorreoEnviado(rs.getObject("id", UUID.class), rs.getString("cuenta"), rs.getString("para"), rs.getString("asunto"), rs.getString("texto"),
            rs.getString("message_id"), rs.getObject("correo_recibido_id", UUID.class), rs.getObject("enviado_por", UUID.class), instante(rs, "enviado_en"))).list();
  }

  /**cuántos NUEVO hay por cuenta y por tipo, cuántos EN_CURSO y cuánta PUBLICIDAD sin atender; los tres primeros no cuentan la PUBLICIDAD*/
  public record Conteo(long enCurso, long publicidad, Map<String, Long> nuevosPorCuenta, Map<String, Long> nuevosPorTipo) {
  }

  public Conteo contar(UUID org) {
    Map<String, Long> porCuenta = new LinkedHashMap<>(), porTipo = new LinkedHashMap<>();
    jdbc.sql("SELECT cuenta, tipo, count(*) AS n FROM correos_recibidos WHERE organizacion_id = :org AND estado = 'NUEVO' AND tipo <> 'PUBLICIDAD' GROUP BY cuenta, tipo ORDER BY cuenta, tipo")
        .param("org", org).query((rs, i) -> {
          porCuenta.merge(rs.getString("cuenta"), rs.getLong("n"), Long::sum);
          porTipo.merge(rs.getString("tipo"), rs.getLong("n"), Long::sum);
          return null;
        }).list();
    long enCurso = jdbc.sql("SELECT count(*) FROM correos_recibidos WHERE organizacion_id = :org AND estado = 'EN_CURSO' AND tipo <> 'PUBLICIDAD'").param("org", org).query(Long.class).single();
    long publicidad = jdbc.sql("SELECT count(*) FROM correos_recibidos WHERE organizacion_id = :org AND tipo = 'PUBLICIDAD' AND estado IN ('NUEVO', 'EN_CURSO')").param("org", org)
        .query(Long.class).single();
    return new Conteo(enCurso, publicidad, porCuenta, porTipo);
  }

  /**pasa a DESCARTADO la PUBLICIDAD que está NUEVO (nunca la que alguien ya tomó); devuelve cuántas*/
  public int descartarPublicidadNueva(UUID org) {
    return jdbc.sql("UPDATE correos_recibidos SET estado = 'DESCARTADO', version = version + 1, actualizado_en = now() WHERE organizacion_id = :org AND tipo = 'PUBLICIDAD' AND estado = 'NUEVO'")
        .param("org", org).update();
  }

  /**lo que hace falta para volver a clasificar un correo con reglas: su texto, los nombres de sus adjuntos y el remitente*/
  public record Reclasificable(UUID id, String remitenteCorreo, String asunto, String texto, List<String> nombresAdjuntos, TipoCorreo tipo) {
  }

  /**los correos cuyo tipo lo puso una regla (nunca los que fijó una persona o la IA), en orden de id y después del último ya visto*/
  public List<Reclasificable> reclasificables(UUID org, UUID despuesDe, int limite) {
    return jdbc.sql("SELECT id, remitente_correo, asunto, texto, adjuntos, tipo FROM correos_recibidos WHERE organizacion_id = :org AND origen_tipo = 'REGLA' AND id > :despuesDe ORDER BY id LIMIT :limite")
        .param("org", org).param("despuesDe", despuesDe).param("limite", limite)
        .query((rs, i) -> new Reclasificable(rs.getObject("id", UUID.class), rs.getString("remitente_correo"), rs.getString("asunto"), rs.getString("texto"),
            json.readValue(rs.getString("adjuntos"), LISTA_ADJUNTOS).stream().map(Adjunto::nombre).toList(), TipoCorreo.valueOf(rs.getString("tipo")))).list();
  }

  /**cambia el tipo de un correo que sigue siendo de regla; falso si entretanto una persona lo fijó a mano*/
  public boolean cambiarTipoPorRegla(UUID org, UUID id, TipoCorreo tipo, String motivo) {
    return jdbc.sql("UPDATE correos_recibidos SET tipo = :tipo, motivo_tipo = :motivo, version = version + 1, actualizado_en = now() WHERE id = :id AND organizacion_id = :org AND origen_tipo = 'REGLA'")
        .param("tipo", tipo.name()).param("motivo", motivo).param("id", id).param("org", org).update() == 1;
  }

  private CorreoResumen resumen(ResultSet rs) throws SQLException {
    return new CorreoResumen(rs.getObject("id", UUID.class), rs.getString("cuenta"), rs.getString("remitente_correo"), rs.getString("remitente_nombre"), rs.getString("asunto"),
        instante(rs, "fecha"), rs.getString("extracto"), TipoCorreo.valueOf(rs.getString("tipo")), OrigenTipo.valueOf(rs.getString("origen_tipo")),
        EstadoCorreo.valueOf(rs.getString("estado")), rs.getObject("responsable_id", UUID.class), rs.getObject("cliente_id", UUID.class), rs.getInt("n_adjuntos"), rs.getLong("version"));
  }

  private FilaCorreo fila(ResultSet rs) throws SQLException {
    return new FilaCorreo(rs.getObject("id", UUID.class), rs.getObject("organizacion_id", UUID.class), rs.getString("cuenta"), rs.getString("message_id"), rs.getString("in_reply_to"),
        rs.getString("referencias"), rs.getString("remitente_correo"), rs.getString("remitente_nombre"), rs.getString("para"), rs.getString("cc"), rs.getString("asunto"),
        instante(rs, "fecha"), rs.getString("texto"), rs.getBoolean("texto_truncado"), json.readValue(rs.getString("adjuntos"), LISTA_ADJUNTOS), TipoCorreo.valueOf(rs.getString("tipo")),
        OrigenTipo.valueOf(rs.getString("origen_tipo")), rs.getString("motivo_tipo"), EstadoCorreo.valueOf(rs.getString("estado")), rs.getObject("responsable_id", UUID.class),
        rs.getObject("cliente_id", UUID.class), rs.getBoolean("tiene_eml"), rs.getLong("version"));
  }

  /**los adjuntos de una fila como los ve el cliente: con su número de orden*/
  public static List<AdjuntoCorreo> adjuntos(List<Adjunto> guardados) {
    List<AdjuntoCorreo> salida = new ArrayList<>();
    for (int i = 0; i < guardados.size(); i++) salida.add(new AdjuntoCorreo(i, guardados.get(i).nombre(), guardados.get(i).tipo(), guardados.get(i).tamano()));
    return salida;
  }

  private static Instant instante(ResultSet rs, String columna) throws SQLException {
    Timestamp t = rs.getTimestamp(columna);
    return t == null ? null : t.toInstant();
  }

  /**el resumen que ya hizo la IA de este correo, si hay*/
  public Optional<ResumenCorreo> resumenIa(UUID org, UUID id) {
    return jdbc.sql("SELECT resumen_ia::text FROM correos_recibidos WHERE id = :id AND organizacion_id = :org AND resumen_ia IS NOT NULL").param("id", id).param("org", org)
        .query(String.class).optional().map(j -> json.readValue(j, ResumenCorreo.class));
  }

  public void guardarResumenIa(UUID org, UUID id, ResumenCorreo resumen, String modelo) {
    jdbc.sql("UPDATE correos_recibidos SET resumen_ia = CAST(:resumen AS jsonb), resumen_ia_modelo = :modelo WHERE id = :id AND organizacion_id = :org")
        .param("resumen", json.writeValueAsString(resumen)).param("modelo", modelo).param("id", id).param("org", org).update();
  }

  /**un correo para dárselo de contexto a la IA: el texto ya viene recortado a `largoTexto`*/
  public record CorreoRelevante(UUID id, String remitenteCorreo, String remitenteNombre, String asunto, Instant fecha, String texto) {
  }

  /**
   *los correos que más se parecen a lo que se busca: cuántos de los `terminos` (ya normalizados) aparecen en el asunto o el texto, sin
   *tildes ni mayúsculas, y a igualdad los más recientes. Sin términos, los más recientes. La publicidad solo entra si se pide.
   *shortcut: recorre los correos del período sin índice (translate+LIKE); con decenas de miles, pasar a un tsvector sin tildes
   */
  public List<CorreoRelevante> relevantes(UUID org, Collection<String> terminos, String cuenta, Instant desde, Instant hastaExclusivo, boolean incluirPublicidad, int limite,
      int largoTexto) {
    Map<String, Object> p = new HashMap<>();
    StringBuilder donde = new StringBuilder(" WHERE organizacion_id = :org");
    p.put("org", org);
    if (cuenta != null) { donde.append(" AND cuenta = :cuenta"); p.put("cuenta", cuenta); }
    if (!incluirPublicidad) donde.append(" AND tipo <> 'PUBLICIDAD'");
    if (desde != null) { donde.append(" AND fecha >= :desde"); p.put("desde", Timestamp.from(desde)); }
    if (hastaExclusivo != null) { donde.append(" AND fecha < :hasta"); p.put("hasta", Timestamp.from(hastaExclusivo)); }
    StringBuilder puntaje = new StringBuilder("0");
    int i = 0;
    for (String termino : terminos) { // solo letras y dígitos: ya pasaron por el normalizador, no hay comodines que escapar
      puntaje.append(" + CASE WHEN plano LIKE :t").append(i).append(" THEN 1 ELSE 0 END");
      p.put("t" + i++, "%" + termino + "%");
    }
    p.put("limite", limite);
    p.put("largo", largoTexto);
    String filtroPuntaje = terminos.isEmpty() ? "" : " WHERE puntaje > 0";
    return jdbc.sql("SELECT id, remitente_correo, remitente_nombre, asunto, fecha, texto FROM (SELECT id, remitente_correo, remitente_nombre, asunto, fecha, left(texto, :largo) AS texto, "
        + "(" + puntaje + ") AS puntaje FROM (SELECT *, translate(lower(asunto || ' ' || texto), 'áéíóúüñ', 'aeiouun') AS plano FROM correos_recibidos" + donde + ") c)"
        + " r" + filtroPuntaje + " ORDER BY puntaje DESC, fecha DESC LIMIT :limite").params(p)
        .query((rs, n) -> new CorreoRelevante(rs.getObject("id", UUID.class), rs.getString("remitente_correo"), rs.getString("remitente_nombre"), rs.getString("asunto"),
            rs.getTimestamp("fecha").toInstant(), rs.getString("texto"))).list();
  }
}
