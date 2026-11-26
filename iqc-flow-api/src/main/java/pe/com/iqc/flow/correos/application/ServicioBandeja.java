package pe.com.iqc.flow.correos.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.correos.EnvioCorreo;
import pe.com.iqc.flow.correos.EnvioCorreo.Mensaje;
import pe.com.iqc.flow.correos.TipoCorreo;
import pe.com.iqc.flow.correos.application.DtosBandeja.ClienteLigado;
import pe.com.iqc.flow.correos.application.DtosBandeja.Contadores;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoDetalle;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoEnviado;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoResumen;
import pe.com.iqc.flow.correos.application.DtosBandeja.EstadoCuenta;
import pe.com.iqc.flow.correos.application.DtosBandeja.FiltroBandeja;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoCuenta;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoDescarte;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoReclasificacion;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoSincronizacion;
import pe.com.iqc.flow.correos.application.DtosBandeja.SolicitudActualizacion;
import pe.com.iqc.flow.correos.application.DtosBandeja.SolicitudEnvio;
import pe.com.iqc.flow.correos.application.DtosBandeja.SolicitudRespuesta;
import pe.com.iqc.flow.correos.domain.ClasificadorCorreo;
import pe.com.iqc.flow.correos.domain.EstadoCorreo;
import pe.com.iqc.flow.correos.domain.OrigenTipo;
import pe.com.iqc.flow.correos.infrastructure.AnalizadorMime;
import pe.com.iqc.flow.correos.infrastructure.AnalizadorMime.AdjuntoContenido;
import pe.com.iqc.flow.correos.infrastructure.PropiedadesCorreo;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos.Criterios;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos.EstadoSincronizacion;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos.FilaCorreo;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos.Reclasificable;

/**
 *la bandeja de correo: ver lo que llegó a los buzones, clasificarlo, asignarlo, ligarlo a un cliente, responder y enviar correos nuevos.
 *Ver pide `correos:leer`; todo lo que cambia algo o envía pide `correos:responder`
 */
@Service
public class ServicioBandeja {
  private static final int REFERENCIAS_MAXIMAS = 10;
  private static final int LOTE_RECLASIFICACION = 200;

  private final RepositorioCorreos repositorio;
  private final ConsultaClientes clientes;
  private final EnvioCorreo envio;
  private final SincronizadorCorreos sincronizador;
  private final PropiedadesCorreo propiedades;
  private final Bitacora bitacora;
  private final ActorActual actor;
  private final Clock reloj;

  public ServicioBandeja(RepositorioCorreos repositorio, ConsultaClientes clientes, EnvioCorreo envio, SincronizadorCorreos sincronizador, PropiedadesCorreo propiedades,
      Bitacora bitacora, ActorActual actor, Clock reloj) {
    this.repositorio = repositorio;
    this.clientes = clientes;
    this.envio = envio;
    this.sincronizador = sincronizador;
    this.propiedades = propiedades;
    this.bitacora = bitacora;
    this.actor = actor;
    this.reloj = reloj;
  }

  /**un archivo para descargar: el nombre ya está saneado*/
  public record Descarga(String nombre, String tipo, byte[] contenido) {
  }

  @PreAuthorize("hasAuthority('correos:leer')")
  public RespuestaPaginada<CorreoResumen> listar(FiltroBandeja f, int pagina, int tamano) {
    var zona = reloj.getZone();
    var pagina0 = repositorio.listar(actor.requerido().organizacionId(), new Criterios(vacioANulo(f.cuenta()), f.tipo(), f.estado(), f.texto(),
        f.desde() == null ? null : f.desde().atStartOfDay(zona).toInstant(), f.hasta() == null ? null : f.hasta().plusDays(1).atStartOfDay(zona).toInstant(),
        Boolean.TRUE.equals(f.sinAtender()), f.responsableId(), Boolean.TRUE.equals(f.incluirPublicidad())), pagina, tamano);
    return RespuestaPaginada.de(new PageImpl<>(pagina0.filas(), PageRequest.of(pagina, tamano), pagina0.total()));
  }

  @PreAuthorize("hasAuthority('correos:leer')")
  public CorreoDetalle detalle(UUID id) {
    UUID org = actor.requerido().organizacionId();
    FilaCorreo c = exigir(org, id);
    ClienteLigado cliente = c.clienteId() == null ? null : clientes.buscar(org, c.clienteId()).map(r -> new ClienteLigado(r.id(), r.razonSocial())).orElse(null);
    return new CorreoDetalle(c.id(), c.cuenta(), c.remitenteCorreo(), c.remitenteNombre(), c.para(), c.cc(), c.asunto(), c.fecha(), c.texto(), c.textoTruncado(), c.tipo(),
        c.origenTipo(), c.motivoTipo(), c.estado(), c.responsableId(), cliente, c.tieneEml(), RepositorioCorreos.adjuntos(c.adjuntos()), repositorio.hilo(c),
        repositorio.respuestasDe(org, id), c.version());
  }

  @PreAuthorize("hasAuthority('correos:responder')")
  public CorreoDetalle actualizar(UUID id, SolicitudActualizacion s) {
    UUID org = actor.requerido().organizacionId();
    FilaCorreo c = exigir(org, id);
    TipoCorreo tipo = s.tipo() != null ? s.tipo() : c.tipo();
    boolean tipoCambia = s.tipo() != null && s.tipo() != c.tipo();
    OrigenTipo origen = tipoCambia ? OrigenTipo.MANUAL : c.origenTipo();
    String motivo = tipoCambia ? "Elegido por una persona" : c.motivoTipo();
    EstadoCorreo estado = s.estado() != null ? s.estado() : c.estado();
    UUID responsable = Boolean.TRUE.equals(s.quitarResponsable()) ? null : s.responsableId() != null ? validarResponsable(org, s.responsableId()) : c.responsableId();
    UUID cliente = Boolean.TRUE.equals(s.quitarCliente()) ? null : s.clienteId() != null ? validarCliente(org, s.clienteId()) : c.clienteId();
    if (tipo == c.tipo() && estado == c.estado() && java.util.Objects.equals(responsable, c.responsableId()) && java.util.Objects.equals(cliente, c.clienteId())) return detalle(id);
    if (!repositorio.actualizar(org, id, s.version(), tipo, origen, motivo, estado, responsable, cliente)) {
      throw new ConflictoException("VERSION_DESACTUALIZADA", "El correo cambió mientras lo mirabas: ábrelo de nuevo y repite el cambio.");
    }
    bitacora.registrar(EntradaBitacora.accion("CORREO_ACTUALIZADO").entidad("CORREO_RECIBIDO", id)
        .estadoAnterior(estadoDe(c.tipo(), c.estado(), c.responsableId(), c.clienteId())).estadoNuevo(estadoDe(tipo, estado, responsable, cliente)));
    return detalle(id);
  }

  @PreAuthorize("hasAuthority('correos:leer')")
  public Descarga adjunto(UUID id, int indice) {
    UUID org = actor.requerido().organizacionId();
    FilaCorreo c = exigir(org, id);
    if (indice < 0 || indice >= c.adjuntos().size()) throw new RecursoNoEncontradoException("Adjunto", indice);
    byte[] eml = repositorio.eml(org, id).orElseThrow(() -> new RecursoNoEncontradoException("Correo original", id));
    try {
      AdjuntoContenido a = AnalizadorMime.adjunto(eml, indice);
      return new Descarga(a.nombre(), a.tipo(), a.contenido());
    } catch (IllegalArgumentException e) {
      throw new RecursoNoEncontradoException("Adjunto", indice);
    }
  }

  @PreAuthorize("hasAuthority('correos:leer')")
  public Descarga eml(UUID id) {
    UUID org = actor.requerido().organizacionId();
    exigir(org, id);
    return new Descarga("correo-" + id + ".eml", "message/rfc822", repositorio.eml(org, id).orElseThrow(() -> new RecursoNoEncontradoException("Correo original", id)));
  }

  @PreAuthorize("hasAuthority('correos:leer')")
  public Contadores contadores() {
    var conteo = repositorio.contar(actor.requerido().organizacionId());
    return new Contadores(conteo.nuevosPorCuenta().values().stream().mapToLong(Long::longValue).sum(), conteo.enCurso(), conteo.publicidad(), conteo.nuevosPorCuenta(), conteo.nuevosPorTipo());
  }

  @PreAuthorize("hasAuthority('correos:leer')")
  public List<EstadoCuenta> estadoSincronizacion() {
    List<EstadoCuenta> estados = new ArrayList<>();
    propiedades.cuentas().forEach((nombre, cuenta) -> {
      EstadoSincronizacion e = repositorio.estado(nombre);
      estados.add(new EstadoCuenta(nombre, cuenta.sincronizable(), e.ultimaPasada(), e.nuevosUltima(), e.ultimoError()));
    });
    return estados;
  }

  /**«Revisar ahora»: la misma pasada que hace el programador, a pedido*/
  @PreAuthorize("hasAuthority('correos:responder')")
  public ResultadoSincronizacion sincronizarAhora() {
    var resultado = sincronizador.pasada();
    List<ResultadoCuenta> cuentas = resultado.orElse(List.of());
    if (resultado.isPresent()) {
      Map<String, Object> resumen = new LinkedHashMap<>();
      cuentas.forEach(c -> resumen.put(c.cuenta(), c.error() == null ? c.nuevos() + " nuevos" : c.error()));
      bitacora.registrar(EntradaBitacora.accion("CORREOS_REVISADOS_A_MANO").entidad("CORREO_BANDEJA", "sincronizacion").estadoNuevo(resumen));
    }
    return new ResultadoSincronizacion(resultado.isEmpty(), cuentas);
  }

  /**
   *vuelve a correr las reglas sobre los correos cuyo tipo lo puso una regla (nunca pisa un tipo fijado a mano ni por la IA), con lo guardado:
   *texto, adjuntos y las cabeceras del .eml para el indicador de envío masivo. No toca el estado ni el cliente. Solo se hace a pedido
   */
  @PreAuthorize("hasAuthority('correos:responder')")
  public ResultadoReclasificacion reclasificar() {
    UUID org = actor.requerido().organizacionId();
    int revisados = 0, cambiaron = 0;
    UUID ultimo = new UUID(0, 0);
    for (List<Reclasificable> lote = repositorio.reclasificables(org, ultimo, LOTE_RECLASIFICACION); !lote.isEmpty(); lote = repositorio.reclasificables(org, ultimo, LOTE_RECLASIFICACION)) {
      for (Reclasificable c : lote) {
        revisados++;
        boolean cliente = clientes.clientePorCorreoDeContacto(org, c.remitenteCorreo()).isPresent();
        boolean masivo = repositorio.eml(org, c.id()).map(AnalizadorMime::esMasivo).orElse(false);
        var nueva = ClasificadorCorreo.clasificar(new ClasificadorCorreo.Entrada(c.asunto(), c.texto(), c.nombresAdjuntos(), cliente, masivo, c.remitenteCorreo()));
        if (nueva.tipo() != c.tipo() && repositorio.cambiarTipoPorRegla(org, c.id(), nueva.tipo(), nueva.motivo())) cambiaron++;
        ultimo = c.id();
      }
    }
    bitacora.registrar(EntradaBitacora.accion("CORREOS_RECLASIFICADOS").entidad("CORREO_BANDEJA", "reclasificacion").estadoNuevo(Map.of("revisados", revisados, "cambiaron", cambiaron)));
    return new ResultadoReclasificacion(revisados, cambiaron);
  }

  /**pasa a DESCARTADO toda la PUBLICIDAD que sigue NUEVO; la que alguien ya tomó (EN_CURSO) no se toca. No se descarta sola: lo decide una persona*/
  @PreAuthorize("hasAuthority('correos:responder')")
  public ResultadoDescarte descartarPublicidad() {
    int descartados = repositorio.descartarPublicidadNueva(actor.requerido().organizacionId());
    bitacora.registrar(EntradaBitacora.accion("PUBLICIDAD_DESCARTADA_EN_BLOQUE").entidad("CORREO_BANDEJA", "publicidad").estadoNuevo(Map.of("descartados", descartados)));
    return new ResultadoDescarte(descartados);
  }

  @PreAuthorize("hasAuthority('correos:responder')")
  public CorreoEnviado responder(UUID id, SolicitudRespuesta s) {
    UsuarioAutenticado usuario = actor.requerido();
    FilaCorreo c = exigir(usuario.organizacionId(), id);
    if (c.remitenteCorreo() == null || !c.remitenteCorreo().contains("@")) throw new ReglaNegocioException("SIN_REMITENTE", "El correo no trae una dirección a la que responder.");
    String cuenta = s.cuenta() == null || s.cuenta().isBlank() ? c.cuenta() : s.cuenta();
    String asunto = s.asunto() == null || s.asunto().isBlank() ? asuntoDeRespuesta(c.asunto()) : s.asunto().strip();
    String mensajeId = envio.enviar(cuenta, new Mensaje(c.remitenteCorreo(), asunto, s.cuerpo(), c.messageId(), c.messageId() == null ? null : ultimasReferencias(c.referencias())));
    UUID enviadoId = repositorio.guardarEnviado(usuario.organizacionId(), id, cuenta, mensajeId, c.messageId(), c.remitenteCorreo(), asunto, s.cuerpo(), usuario.id());
    boolean atendido = Boolean.TRUE.equals(s.marcarAtendido());
    if (atendido) repositorio.marcarAtendido(usuario.organizacionId(), id);
    bitacora.registrar(EntradaBitacora.accion("CORREO_RESPONDIDO").entidad("CORREO_RECIBIDO", id)
        .estadoNuevo(Map.of("cuenta", cuenta, "destino", ServicioCorreos.enmascarar(c.remitenteCorreo()), "marcadoAtendido", atendido)));
    return new CorreoEnviado(enviadoId, cuenta, c.remitenteCorreo(), asunto, s.cuerpo(), mensajeId, id, usuario.id(), reloj.instant());
  }

  @PreAuthorize("hasAuthority('correos:responder')")
  public CorreoEnviado enviarNuevo(SolicitudEnvio s) {
    UsuarioAutenticado usuario = actor.requerido();
    String mensajeId = envio.enviar(s.cuenta(), new Mensaje(s.para(), s.asunto().strip(), s.cuerpo()));
    UUID enviadoId = repositorio.guardarEnviado(usuario.organizacionId(), null, s.cuenta(), mensajeId, null, s.para(), s.asunto().strip(), s.cuerpo(), usuario.id());
    bitacora.registrar(EntradaBitacora.accion("CORREO_ENVIADO").entidad("CORREO_ENVIADO", enviadoId).estadoNuevo(Map.of("cuenta", s.cuenta(), "destino", ServicioCorreos.enmascarar(s.para()))));
    return new CorreoEnviado(enviadoId, s.cuenta(), s.para(), s.asunto().strip(), s.cuerpo(), mensajeId, null, usuario.id(), reloj.instant());
  }

  private FilaCorreo exigir(UUID org, UUID id) {
    return repositorio.buscar(org, id).orElseThrow(() -> new RecursoNoEncontradoException("Correo", id));
  }

  private UUID validarResponsable(UUID org, UUID usuarioId) {
    if (!repositorio.usuarioActivo(org, usuarioId)) throw new ReglaNegocioException("RESPONSABLE_INVALIDO", "El responsable no es un usuario activo.");
    return usuarioId;
  }

  private UUID validarCliente(UUID org, UUID clienteId) {
    return clientes.buscar(org, clienteId).map(ClienteReferencia::id).orElseThrow(() -> new ReglaNegocioException("CLIENTE_INVALIDO", "El cliente no existe."));
  }

  private static Map<String, Object> estadoDe(TipoCorreo tipo, EstadoCorreo estado, UUID responsable, UUID cliente) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("tipo", tipo.name());
    m.put("estado", estado.name());
    m.put("responsableId", responsable == null ? null : responsable.toString());
    m.put("clienteId", cliente == null ? null : cliente.toString());
    return m;
  }

  /**«Re: asunto», sin repetir el «Re:» si ya lo trae*/
  static String asuntoDeRespuesta(String asunto) {
    String base = asunto == null ? "" : asunto.strip();
    String respuesta = base.regionMatches(true, 0, "re:", 0, 3) ? base : "Re: " + base;
    return respuesta.length() > 900 ? respuesta.substring(0, 900) : respuesta;
  }

  /**las últimas referencias del hilo: la cabecera no debe crecer sin límite*/
  static String ultimasReferencias(String referencias) {
    if (referencias == null || referencias.isBlank()) return null;
    String[] ids = referencias.strip().split("\\s+");
    return String.join(" ", Arrays.copyOfRange(ids, Math.max(0, ids.length - REFERENCIAS_MAXIMAS), ids.length));
  }

  private static String vacioANulo(String s) { return s == null || s.isBlank() ? null : s.strip().toLowerCase(java.util.Locale.ROOT); }
}
