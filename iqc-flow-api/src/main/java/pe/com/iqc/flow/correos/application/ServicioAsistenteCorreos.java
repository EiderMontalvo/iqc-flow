package pe.com.iqc.flow.correos.application;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.correos.AsistenteCorreos;
import pe.com.iqc.flow.correos.AsistenteCorreos.Consumo;
import pe.com.iqc.flow.correos.AsistenteCorreos.CorreoContexto;
import pe.com.iqc.flow.correos.AsistenteCorreos.Tono;
import pe.com.iqc.flow.correos.application.DtosAsistente.BorradorRespuesta;
import pe.com.iqc.flow.correos.application.DtosAsistente.CorreoCitado;
import pe.com.iqc.flow.correos.application.DtosAsistente.EstadoAsistente;
import pe.com.iqc.flow.correos.application.DtosAsistente.RespuestaConsulta;
import pe.com.iqc.flow.correos.application.DtosAsistente.ResumenCorreo;
import pe.com.iqc.flow.correos.application.DtosAsistente.SolicitudBorrador;
import pe.com.iqc.flow.correos.application.DtosAsistente.SolicitudConsulta;
import pe.com.iqc.flow.correos.domain.TerminosDeBusqueda;
import pe.com.iqc.flow.correos.domain.ValidadorSalidaIa;
import pe.com.iqc.flow.correos.infrastructure.PropiedadesCorreo;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos.CorreoRelevante;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos.FilaCorreo;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;

/**
 *la IA sobre la bandeja: resumir un correo, redactar un borrador de respuesta y contestar preguntas sobre lo que llegó. Solo cuando la
 *organización la encendió (IA_HABILITADA, y la persona no la tiene apagada) y el servidor tiene clave: sin eso no sale ningún byte. Solo
 *devuelve texto: nada se envía ni se cambia solo. Del correo solo sale el texto recortado (sin adjuntos, cabeceras ni el .eml), y la
 *bitácora guarda qué se hizo y cuánto costó, nunca el contenido
 */
@Service
public class ServicioAsistenteCorreos {
  /**lo que se le da de cada correo al contestar una pregunta, y cuántos correos como máximo*/
  static final int CONTEXTO_POR_CORREO = 1_500;
  static final int CORREOS_EN_CONSULTA = 10;
  private static final String APAGADA = "La IA está apagada en Configuración.";
  private static final String SIN_CLAVE = "Falta la clave de IA en el servidor.";

  private final ObjectProvider<AsistenteCorreos> asistente;
  private final RepositorioCorreos repositorio;
  private final ParametrosOrganizacion parametros;
  private final PropiedadesCorreo propiedades;
  private final Bitacora bitacora;
  private final ActorActual actor;
  private final Clock reloj;
  private final LimitadorLlamadas limitador;

  public ServicioAsistenteCorreos(ObjectProvider<AsistenteCorreos> asistente, RepositorioCorreos repositorio, ParametrosOrganizacion parametros, PropiedadesCorreo propiedades,
      Bitacora bitacora, ActorActual actor, Clock reloj) {
    this.asistente = asistente;
    this.repositorio = repositorio;
    this.parametros = parametros;
    this.propiedades = propiedades;
    this.bitacora = bitacora;
    this.actor = actor;
    this.reloj = reloj;
    this.limitador = new LimitadorLlamadas(propiedades.asistente().llamadasPorMinuto(), reloj);
  }

  @PreAuthorize("hasAuthority('correos:leer')")
  public EstadoAsistente estado() { return estadoPara(actor.requerido()); }

  @PreAuthorize("hasAuthority('correos:leer')")
  public ResumenCorreo resumir(UUID id) {
    UsuarioAutenticado usuario = actor.requerido();
    AsistenteCorreos ia = exigirDisponible(usuario);
    FilaCorreo c = exigir(usuario.organizacionId(), id);
    var guardado = repositorio.resumenIa(usuario.organizacionId(), id);
    if (guardado.isPresent()) return guardado.get();
    limitar(usuario);
    var r = ia.resumir(c.asunto(), recortar(c.texto())).map(ValidadorSalidaIa::resumen).orElseThrow(ServicioAsistenteCorreos::sinRespuesta);
    var resumen = new ResumenCorreo(r.resumen(), r.puntosClave(), r.accionSugerida(), r.tipoSugerido());
    repositorio.guardarResumenIa(usuario.organizacionId(), id, resumen, r.consumo().modelo());
    registrar("CORREO_IA_RESUMEN", "CORREO_RECIBIDO", id.toString(), r.consumo(), Map.of());
    return resumen;
  }

  @PreAuthorize("hasAuthority('correos:responder')")
  public BorradorRespuesta redactar(UUID id, SolicitudBorrador s) {
    UsuarioAutenticado usuario = actor.requerido();
    AsistenteCorreos ia = exigirDisponible(usuario);
    FilaCorreo c = exigir(usuario.organizacionId(), id);
    limitar(usuario);
    String remitente = c.remitenteNombre() == null || c.remitenteNombre().isBlank() ? c.remitenteCorreo() : c.remitenteNombre() + " <" + c.remitenteCorreo() + ">";
    String instrucciones = s == null || s.instrucciones() == null ? "" : sinControl(s.instrucciones());
    Tono tono = s == null || s.tono() == null ? Tono.FORMAL : s.tono();
    var b = ia.redactarRespuesta(new CorreoContexto(id.toString(), remitente, c.asunto(), c.fecha(), recortar(c.texto())), instrucciones, tono)
        .map(ValidadorSalidaIa::borrador).orElseThrow(ServicioAsistenteCorreos::sinRespuesta);
    registrar("CORREO_IA_BORRADOR", "CORREO_RECIBIDO", id.toString(), b.consumo(), Map.of());
    return new BorradorRespuesta(b.asunto().isEmpty() ? ServicioBandeja.asuntoDeRespuesta(c.asunto()) : b.asunto(), b.cuerpo());
  }

  @PreAuthorize("hasAuthority('correos:leer')")
  public RespuestaConsulta consultar(SolicitudConsulta s) {
    UsuarioAutenticado usuario = actor.requerido();
    AsistenteCorreos ia = exigirDisponible(usuario);
    String pregunta = sinControl(s.pregunta());
    var zona = reloj.getZone();
    List<CorreoRelevante> encontrados = repositorio.relevantes(usuario.organizacionId(), TerminosDeBusqueda.de(pregunta), vacioANulo(s.cuenta()),
        s.desde() == null ? null : s.desde().atStartOfDay(zona).toInstant(), s.hasta() == null ? null : s.hasta().plusDays(1).atStartOfDay(zona).toInstant(),
        TerminosDeBusqueda.pidePublicidad(pregunta), CORREOS_EN_CONSULTA, CONTEXTO_POR_CORREO);
    // Sin correos que se le parezcan no se le pregunta al modelo: no tiene de dónde sacar la respuesta y no hay que dejar que la invente
    if (encontrados.isEmpty()) return new RespuestaConsulta("No encontré correos relacionados con tu pregunta en la bandeja.", List.of());
    limitar(usuario);
    List<CorreoContexto> contexto = encontrados.stream().map(e -> new CorreoContexto(e.id().toString(), e.remitenteNombre() == null || e.remitenteNombre().isBlank()
        ? e.remitenteCorreo() : e.remitenteNombre() + " <" + e.remitenteCorreo() + ">", e.asunto(), e.fecha(), e.texto())).toList();
    Set<String> ids = contexto.stream().map(CorreoContexto::id).collect(Collectors.toSet());
    var r = ia.consultar(pregunta, contexto).map(x -> ValidadorSalidaIa.consulta(x, ids)).orElseThrow(ServicioAsistenteCorreos::sinRespuesta);
    registrar("CORREO_IA_CONSULTA", "CORREO_BANDEJA", "asistente", r.consumo(), Map.of("correosEnContexto", contexto.size()));
    List<CorreoCitado> citados = encontrados.stream().filter(e -> r.idsUsados().contains(e.id().toString()))
        .map(e -> new CorreoCitado(e.id(), e.asunto(), e.remitenteCorreo(), e.fecha())).toList();
    return new RespuestaConsulta(r.respuesta(), citados);
  }

  private EstadoAsistente estadoPara(UsuarioAutenticado usuario) {
    if (!parametros.activoPara(usuario.organizacionId(), usuario.id(), ParametroOrganizacion.IA_HABILITADA)) return new EstadoAsistente(false, APAGADA);
    if (!asistente().disponible()) return new EstadoAsistente(false, SIN_CLAVE);
    return new EstadoAsistente(true, null);
  }

  private AsistenteCorreos asistente() { return asistente.getIfAvailable(() -> AsistenteCorreos.NINGUNO); }

  private AsistenteCorreos exigirDisponible(UsuarioAutenticado usuario) {
    EstadoAsistente estado = estadoPara(usuario);
    if (!estado.disponible()) throw new ReglaNegocioException("IA_NO_DISPONIBLE", estado.motivo());
    return asistente();
  }

  private void limitar(UsuarioAutenticado usuario) {
    if (!limitador.permitir(usuario.id())) throw new ReglaNegocioException("IA_LIMITE", "Hiciste muchas consultas a la IA seguidas: espera un minuto e inténtalo de nuevo.");
  }

  private FilaCorreo exigir(UUID org, UUID id) {
    return repositorio.buscar(org, id).orElseThrow(() -> new RecursoNoEncontradoException("Correo", id));
  }

  /**lo único del correo que sale hacia el proveedor: el texto, recortado*/
  private String recortar(String texto) {
    int maximo = propiedades.asistente().textoMaximo();
    String t = texto == null ? "" : texto;
    return t.length() > maximo ? t.substring(0, maximo) : t;
  }

  private void registrar(String accion, String entidad, String id, Consumo consumo, Map<String, Object> extra) {
    Map<String, Object> datos = new java.util.LinkedHashMap<>(extra);
    datos.put("modelo", consumo.modelo());
    datos.put("tokensEntrada", consumo.tokensEntrada());
    datos.put("tokensSalida", consumo.tokensSalida());
    bitacora.registrar(EntradaBitacora.accion(accion).entidad(entidad, id).estadoNuevo(datos));
  }

  private static String sinControl(String s) { return s.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip(); }

  private static String vacioANulo(String s) { return s == null || s.isBlank() ? null : s.strip().toLowerCase(Locale.ROOT); }

  private static ReglaNegocioException sinRespuesta() {
    return new ReglaNegocioException("IA_RESPUESTA_INVALIDA", "La IA no pudo responder esta vez. Inténtalo de nuevo.");
  }
}
