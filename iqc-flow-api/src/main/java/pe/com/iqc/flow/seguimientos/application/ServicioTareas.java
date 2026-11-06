package pe.com.iqc.flow.seguimientos.application;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;
import pe.com.iqc.flow.notificaciones.NuevaNotificacion;
import pe.com.iqc.flow.notificaciones.ServicioNotificaciones;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.ClienteDeTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.EventoRespuesta;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.Referencia;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SiguienteTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudCancelarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudCompletarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudCrearTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudEditarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudReasignarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudReprogramarTarea;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.TareaDetalle;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.TareaResumen;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.TipoTareaRespuesta;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.VistaTareas;
import pe.com.iqc.flow.seguimientos.domain.EstadoTarea;
import pe.com.iqc.flow.seguimientos.domain.EventoHistorial;
import pe.com.iqc.flow.seguimientos.domain.HistorialTarea;
import pe.com.iqc.flow.seguimientos.domain.OrigenTarea;
import pe.com.iqc.flow.seguimientos.domain.PrioridadTarea;
import pe.com.iqc.flow.seguimientos.domain.TareaSeguimiento;
import pe.com.iqc.flow.seguimientos.domain.TipoTarea;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas;
import pe.com.iqc.flow.compartido.bitacora.TipoActor;
import pe.com.iqc.flow.seguimientos.infrastructure.HistorialTareaRepository;
import pe.com.iqc.flow.seguimientos.infrastructure.TareaRepository;
import pe.com.iqc.flow.seguimientos.infrastructure.TipoTareaRepository;

/**creación, consulta y ciclo de vida de las tareas de seguimiento*/
@Service
public class ServicioTareas implements ProgramadorTareas {
  static final String ENTIDAD = "TAREA";
  static final String SUJETO_CLIENTE = "CLIENTE";

  private final TareaRepository tareas;
  private final TipoTareaRepository tipos;
  private final HistorialTareaRepository historial;
  private final AccesoTareas acceso;
  private final ConsultaClientes clientes;
  private final DirectorioUsuarios directorio;
  private final ServicioNotificaciones notificaciones;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioTareas(TareaRepository tareas, TipoTareaRepository tipos, HistorialTareaRepository historial,
      AccesoTareas acceso, ConsultaClientes clientes, DirectorioUsuarios directorio,
      ServicioNotificaciones notificaciones, ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.tareas = tareas;
    this.tipos = tipos;
    this.historial = historial;
    this.acceso = acceso;
    this.clientes = clientes;
    this.directorio = directorio;
    this.notificaciones = notificaciones;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('seguimientos:leer')")
  public List<TipoTareaRespuesta> listarTipos() {
    return tipos.findByActivoTrueOrderByOrdenAsc().stream()
        .map(tipo -> new TipoTareaRespuesta(tipo.getCodigo(), tipo.getNombre(), tipo.getPrioridadPorDefecto()))
        .toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('seguimientos:leer')")
  public Page<TareaResumen> listar(VistaTareas vista, boolean soloMias, UUID responsableId, UUID clienteId,
      String tipo, EstadoTarea estado, PrioridadTarea prioridad, int diasProximas, int pagina, int tamano) {
    UsuarioAutenticado actor = actorActual.requerido();
    LocalDate hoy = LocalDate.now(reloj);
    LocalTime ahora = LocalTime.now(reloj);
    boolean veTodas = acceso.veTodas();
    Set<UUID> clientesPropios = veTodas ? Set.of() : acceso.clientesPropios();
    VistaTareas vistaEfectiva = vista == null ? VistaTareas.ABIERTAS : vista;

    Specification<TareaSeguimiento> filtro = (raiz, consulta, cb) -> {
      List<Predicate> c = new ArrayList<>();
      c.add(cb.equal(raiz.get("organizacionId"), actor.organizacionId()));
      if (!veTodas) {
        Predicate propias = cb.equal(raiz.get("responsableUsuarioId"), actor.id());
        c.add(clientesPropios.isEmpty() ? propias : cb.or(propias, raiz.get("clienteId").in(clientesPropios)));
      }
      if (soloMias) c.add(cb.equal(raiz.get("responsableUsuarioId"), actor.id()));
      if (responsableId != null) c.add(cb.equal(raiz.get("responsableUsuarioId"), responsableId));
      if (clienteId != null) c.add(cb.equal(raiz.get("clienteId"), clienteId));
      if (tipo != null && !tipo.isBlank()) c.add(cb.equal(raiz.get("tipoCodigo"), tipo));
      if (estado != null) c.add(cb.equal(raiz.get("estado"), estado));
      if (prioridad != null) c.add(cb.equal(raiz.get("prioridad"), prioridad));
      Predicate abierta = raiz.get("estado").in(TareaSeguimiento.ABIERTAS);
      switch (vistaEfectiva) {
        case ABIERTAS -> c.add(abierta);
        case HOY -> c.add(cb.and(abierta, cb.equal(raiz.get("fechaLimite"), hoy)));
        case ATRASADAS -> c.add(cb.and(abierta, cb.or(
            cb.lessThan(raiz.get("fechaLimite"), hoy),
            cb.and(cb.equal(raiz.get("fechaLimite"), hoy), cb.isNotNull(raiz.get("horaLimite")),
                cb.lessThan(raiz.get("horaLimite"), ahora)))));
        case PROXIMAS -> c.add(cb.and(abierta, cb.greaterThan(raiz.get("fechaLimite"), hoy),
            cb.lessThanOrEqualTo(raiz.get("fechaLimite"), hoy.plusDays(diasProximas))));
        case EN_ESPERA -> c.add(cb.equal(raiz.get("estado"), EstadoTarea.EN_ESPERA));
        case CERRADAS -> c.add(cb.not(abierta));
        case TODAS -> { }
      }
      return cb.and(c.toArray(Predicate[]::new));
    };
    Sort orden = vistaEfectiva == VistaTareas.CERRADAS
        ? Sort.by(Sort.Direction.DESC, "cerradaEn")
        : Sort.by("fechaLimite").and(Sort.by("horaLimite")).and(Sort.by("creadoEn"));
    Page<TareaSeguimiento> resultado = tareas.findAll(filtro, PageRequest.of(pagina, tamano, orden));
    return new PageImpl<>(resumir(resultado.getContent()), resultado.getPageable(), resultado.getTotalElements());
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('seguimientos:leer')")
  public TareaDetalle obtener(UUID id) { return detalle(acceso.cargarVisible(id)); }

  @Transactional
  @PreAuthorize("hasAuthority('seguimientos:escribir')")
  public TareaDetalle crear(SolicitudCrearTarea solicitud) {
    UsuarioAutenticado actor = actorActual.requerido();
    UUID responsable = resolverResponsable(solicitud.responsableId(), actor);
    TareaSeguimiento tarea = crearInterna(actor, solicitud.titulo(), limpio(solicitud.descripcion()), solicitud.tipo(),
        solicitud.clienteId(), responsable, solicitud.prioridad(), solicitud.fechaLimite(), solicitud.horaLimite());
    return detalle(tarea);
  }

  @Transactional
  @PreAuthorize("hasAuthority('seguimientos:escribir')")
  public TareaDetalle editar(UUID id, SolicitudEditarTarea solicitud) {
    TareaSeguimiento tarea = acceso.cargarGestionable(id);
    verificarVersion(tarea, solicitud.version());
    String antes = descripcionEditable(tarea);
    EstadoTarea estadoAnterior = tarea.getEstado();
    tarea.editar(
        solicitud.titulo() != null ? solicitud.titulo() : tarea.getTitulo(),
        solicitud.descripcion() == null ? tarea.getDescripcion() : limpio(solicitud.descripcion()),
        solicitud.prioridad() != null ? solicitud.prioridad() : tarea.getPrioridad());
    if (solicitud.estado() != null && solicitud.estado() != estadoAnterior) {
      tarea.cambiarEstado(solicitud.estado());
      registrar(tarea, EventoHistorial.ESTADO_CAMBIADO, estadoAnterior.name(), solicitud.estado().name(), null);
    }
    String despues = descripcionEditable(tarea);
    if (!antes.equals(despues)) registrar(tarea, EventoHistorial.EDITADA, antes, despues, null);
    tareas.flush();
    return detalle(tarea);
  }

  @Transactional
  @PreAuthorize("hasAuthority('seguimientos:escribir')")
  public TareaDetalle completar(UUID id, SolicitudCompletarTarea solicitud) {
    TareaSeguimiento tarea = acceso.cargarGestionable(id);
    verificarVersion(tarea, solicitud.version());
    UsuarioAutenticado actor = actorActual.requerido();
    String estadoAnterior = tarea.getEstado().name();
    tarea.completar(solicitud.resultado(), limpio(solicitud.notas()), Instant.now(reloj), actor.id());
    registrar(tarea, EventoHistorial.COMPLETADA, estadoAnterior, solicitud.resultado().name(), limpio(solicitud.notas()));
    SiguienteTarea siguiente = solicitud.siguiente();
    if (siguiente != null) {
      crearInterna(actor, siguiente.titulo(), limpio(siguiente.descripcion()), siguiente.tipo(), tarea.getClienteId(),
          tarea.getResponsableUsuarioId(), siguiente.prioridad(), siguiente.fechaLimite(), siguiente.horaLimite());
    }
    tareas.flush();
    return detalle(tarea);
  }

  @Transactional
  @PreAuthorize("hasAuthority('seguimientos:escribir')")
  public TareaDetalle reprogramar(UUID id, SolicitudReprogramarTarea solicitud) {
    TareaSeguimiento tarea = acceso.cargarGestionable(id);
    verificarVersion(tarea, solicitud.version());
    exigirFechaNoPasada(solicitud.fechaLimite());
    String antes = fechaTexto(tarea.getFechaLimite(), tarea.getHoraLimite());
    tarea.reprogramar(solicitud.fechaLimite(), solicitud.horaLimite());
    registrar(tarea, EventoHistorial.REPROGRAMADA, antes, fechaTexto(tarea.getFechaLimite(), tarea.getHoraLimite()),
        solicitud.motivo().trim());
    tareas.flush();
    return detalle(tarea);
  }

  @Transactional
  @PreAuthorize("hasAuthority('seguimientos:asignar')")
  public TareaDetalle reasignar(UUID id, SolicitudReasignarTarea solicitud) {
    TareaSeguimiento tarea = acceso.cargarVisible(id);
    verificarVersion(tarea, solicitud.version());
    if (!tarea.getResponsableUsuarioId().equals(solicitud.responsableId())) {
      UUID anterior = tarea.getResponsableUsuarioId();
      UsuarioResumen nuevo = validarResponsable(tarea.getOrganizacionId(), solicitud.responsableId());
      tarea.reasignar(nuevo.id());
      registrar(tarea, EventoHistorial.REASIGNADA, anterior.toString(), nuevo.id().toString(), null);
      avisarAsignacion(tarea);
      tareas.flush();
    }
    return detalle(tarea);
  }

  @Transactional
  @PreAuthorize("hasAuthority('seguimientos:escribir')")
  public TareaDetalle cancelar(UUID id, SolicitudCancelarTarea solicitud) {
    TareaSeguimiento tarea = acceso.cargarGestionable(id);
    verificarVersion(tarea, solicitud.version());
    String estadoAnterior = tarea.getEstado().name();
    tarea.cancelar(solicitud.motivo().trim(), Instant.now(reloj), actorActual.requerido().id());
    registrar(tarea, EventoHistorial.CANCELADA, estadoAnterior, EstadoTarea.CANCELADA.name(), solicitud.motivo().trim());
    tareas.flush();
    return detalle(tarea);
  }

  private TareaSeguimiento crearInterna(UsuarioAutenticado actor, String titulo, String descripcion, String tipoCodigo,
      UUID clienteId, UUID responsable, PrioridadTarea prioridad, LocalDate fecha, LocalTime hora) {
    TipoTarea tipo = tipos.findById(tipoCodigo).filter(TipoTarea::isActivo)
        .orElseThrow(() -> new ReglaNegocioException("TIPO_TAREA_INVALIDO", "Tipo de tarea desconocido: " + tipoCodigo));
    exigirFechaNoPasada(fecha);
    if (clienteId != null) {
      ClienteReferencia cliente = clientes.buscar(actor.organizacionId(), clienteId)
          .orElseThrow(() -> new ReglaNegocioException("CLIENTE_INVALIDO", "El cliente indicado no existe."));
      if (cliente.archivado()) {
        throw new ReglaNegocioException("CLIENTE_ARCHIVADO", "No se pueden crear tareas para un cliente archivado.");
      }
    }
    TareaSeguimiento tarea = tareas.save(TareaSeguimiento.nueva(actor.organizacionId(), titulo, descripcion,
        tipo.getCodigo(), clienteId == null ? null : SUJETO_CLIENTE, clienteId, clienteId, responsable,
        prioridad != null ? prioridad : tipo.getPrioridadPorDefecto(), fecha, hora, OrigenTarea.MANUAL, null,
        actor.id()));
    registrar(tarea, EventoHistorial.CREADA, null, tarea.getTitulo(), null);
    if (!responsable.equals(actor.id())) avisarAsignacion(tarea);
    return tarea;
  }

  @Override
  @Transactional
  public UUID crearTareaAutomatica(NuevaTareaAutomatica solicitud) {
    var existente = tareas.findFirstByOrganizacionIdAndClaveDeduplicacionAndEstadoIn(solicitud.organizacionId(),
        solicitud.claveDeduplicacion(), TareaSeguimiento.ABIERTAS);
    if (existente.isPresent()) return existente.get().getId();
    TipoTarea tipo = tipos.findById(solicitud.tipoCodigo()).filter(TipoTarea::isActivo)
        .orElseThrow(() -> new IllegalArgumentException("Tipo de tarea desconocido: " + solicitud.tipoCodigo()));
    UUID responsable = directorio.buscarActivo(solicitud.organizacionId(), solicitud.responsableId())
        .map(UsuarioResumen::id)
        .orElseThrow(() -> new ReglaNegocioException("RESPONSABLE_INVALIDO",
            "El responsable de la tarea automática no existe o está deshabilitado."));
    LocalDate hoy = LocalDate.now(reloj);
    LocalDate fecha = solicitud.fechaLimite().isBefore(hoy) ? hoy : solicitud.fechaLimite();
    TareaSeguimiento tarea = tareas.save(TareaSeguimiento.nueva(solicitud.organizacionId(), solicitud.titulo(),
        solicitud.descripcion(), tipo.getCodigo(), solicitud.sujetoTipo(), solicitud.sujetoId(), solicitud.clienteId(),
        responsable, tipo.getPrioridadPorDefecto(), fecha, null, OrigenTarea.AUTOMATIZACION,
        solicitud.claveDeduplicacion(), null));
    historial.save(new HistorialTarea(tarea.getId(), EventoHistorial.CREADA, null, tarea.getTitulo(),
        "Creada automáticamente", null, Instant.now(reloj)));
    bitacora.registrar(EntradaBitacora.accion("TAREA_CREADA")
        .entidad(ENTIDAD, tarea.getId())
        .organizacion(solicitud.organizacionId())
        .actor(null, TipoActor.AUTOMATIZACION)
        .estadoNuevo(valor(tarea.getTitulo()))
        .motivo("Clave " + solicitud.claveDeduplicacion()));
    return tarea.getId();
  }

  @Override
  @Transactional
  public boolean cancelarTareaAutomatica(UUID organizacionId, String claveDeduplicacion, String motivo) {
    var abierta = tareas.findFirstByOrganizacionIdAndClaveDeduplicacionAndEstadoIn(organizacionId, claveDeduplicacion,
        TareaSeguimiento.ABIERTAS);
    if (abierta.isEmpty()) return false;
    TareaSeguimiento tarea = abierta.get();
    String anterior = tarea.getEstado().name();
    Instant ahora = Instant.now(reloj);
    tarea.cancelar(motivo, ahora, null);
    historial.save(new HistorialTarea(tarea.getId(), EventoHistorial.CANCELADA, anterior, EstadoTarea.CANCELADA.name(),
        motivo, null, ahora));
    bitacora.registrar(EntradaBitacora.accion("TAREA_CANCELADA")
        .entidad(ENTIDAD, tarea.getId())
        .organizacion(organizacionId)
        .actor(null, TipoActor.AUTOMATIZACION)
        .motivo(motivo));
    return true;
  }

  private UUID resolverResponsable(UUID solicitado, UsuarioAutenticado actor) {
    if (solicitado == null || solicitado.equals(actor.id())) return actor.id();
    if (!actorActual.tienePermiso(AccesoTareas.ASIGNAR)) {
      throw new AccessDeniedException("Asignar tareas a otra persona requiere el permiso seguimientos:asignar");
    }
    return validarResponsable(actor.organizacionId(), solicitado).id();
  }

  private UsuarioResumen validarResponsable(UUID organizacionId, UUID usuarioId) {
    UsuarioResumen usuario = directorio.buscarActivo(organizacionId, usuarioId)
        .orElseThrow(() -> new ReglaNegocioException("RESPONSABLE_INVALIDO",
            "El responsable indicado no existe o está deshabilitado."));
    if (!usuario.tienePermiso(AccesoTareas.ESCRIBIR)) {
      throw new ReglaNegocioException("RESPONSABLE_SIN_PERMISO",
          usuario.nombre() + " no tiene un rol que le permita gestionar tareas.");
    }
    return usuario;
  }

  private void avisarAsignacion(TareaSeguimiento tarea) {
    notificaciones.notificar(new NuevaNotificacion(tarea.getOrganizacionId(), tarea.getResponsableUsuarioId(),
        "TAREA_ASIGNADA", "Nueva tarea asignada: " + tarea.getTitulo(),
        "Fecha límite: " + fechaTexto(tarea.getFechaLimite(), tarea.getHoraLimite()) + ".", ENTIDAD, tarea.getId(),
        null));
  }

  private void exigirFechaNoPasada(LocalDate fecha) {
    if (fecha.isBefore(LocalDate.now(reloj))) {
      throw new ReglaNegocioException("FECHA_PASADA", "La fecha límite no puede ser anterior a hoy.");
    }
  }

  /**historial visible de la tarea y registro en la bitácora*/
  private void registrar(TareaSeguimiento tarea, EventoHistorial evento, String desde, String hacia, String motivo) {
    UUID actor = actorActual.obtener().map(UsuarioAutenticado::id).orElse(null);
    historial.save(new HistorialTarea(tarea.getId(), evento, recortar(desde), recortar(hacia), motivo, actor,
        Instant.now(reloj)));
    bitacora.registrar(EntradaBitacora.accion("TAREA_" + evento.name())
        .entidad(ENTIDAD, tarea.getId())
        .estadoAnterior(valor(desde))
        .estadoNuevo(valor(hacia))
        .motivo(motivo));
  }

  private TareaDetalle detalle(TareaSeguimiento tarea) {
    List<HistorialTarea> eventos = historial.findByTareaIdOrderByOcurridoEnAsc(tarea.getId());
    Set<UUID> usuarios = new HashSet<>();
    usuarios.add(tarea.getResponsableUsuarioId());
    if (tarea.getCerradaPor() != null) usuarios.add(tarea.getCerradaPor());
    if (tarea.getCreadoPor() != null) usuarios.add(tarea.getCreadoPor());
    eventos.stream().map(HistorialTarea::getActorUsuarioId).filter(Objects::nonNull).forEach(usuarios::add);
    Map<UUID, Referencia> personas = personas(usuarios);
    TipoTarea tipo = tipos.findById(tarea.getTipoCodigo()).orElseThrow();
    List<EventoRespuesta> listaEventos = eventos.stream()
        .map(e -> new EventoRespuesta(e.getEvento(), e.getDesde(), e.getHacia(), e.getMotivo(),
            e.getActorUsuarioId() == null ? null : personas.get(e.getActorUsuarioId()), e.getOcurridoEn()))
        .toList();
    return new TareaDetalle(tarea.getId(), tarea.getTitulo(), tarea.getDescripcion(), tipo.getCodigo(),
        tipo.getNombre(), tarea.getPrioridad(), tarea.getEstado(), tarea.getFechaLimite(), tarea.getHoraLimite(),
        vencida(tarea), clienteDe(tarea), personas.get(tarea.getResponsableUsuarioId()), tarea.getResultado(),
        tarea.getNotasResultado(), tarea.getCerradaEn(),
        tarea.getCerradaPor() == null ? null : personas.get(tarea.getCerradaPor()), tarea.getReprogramaciones(),
        tarea.getOrigen(), tarea.getCreadoPor() == null ? null : personas.get(tarea.getCreadoPor()),
        tarea.getCreadoEn(), listaEventos, tarea.getVersion(), acceso.puedeGestionar(tarea));
  }

  private List<TareaResumen> resumir(List<TareaSeguimiento> lista) {
    if (lista.isEmpty()) return List.of();
    UUID organizacionId = actorActual.requerido().organizacionId();
    Map<String, TipoTarea> tiposPorCodigo = tipos.findAll().stream()
        .collect(Collectors.toMap(TipoTarea::getCodigo, Function.identity()));
    Map<UUID, Referencia> personas = personas(lista.stream().map(TareaSeguimiento::getResponsableUsuarioId)
        .collect(Collectors.toSet()));
    Map<UUID, ClienteReferencia> refs = clientes.buscarPorIds(organizacionId, lista.stream()
        .map(TareaSeguimiento::getClienteId).filter(Objects::nonNull).collect(Collectors.toSet()));
    return lista.stream().map(tarea -> {
      ClienteReferencia cliente = tarea.getClienteId() == null ? null : refs.get(tarea.getClienteId());
      return new TareaResumen(tarea.getId(), tarea.getTitulo(), tarea.getTipoCodigo(),
          tiposPorCodigo.get(tarea.getTipoCodigo()).getNombre(), tarea.getPrioridad(), tarea.getEstado(),
          tarea.getFechaLimite(), tarea.getHoraLimite(), vencida(tarea),
          cliente == null ? null : new ClienteDeTarea(cliente.id(), cliente.codigo(), cliente.razonSocial()),
          personas.get(tarea.getResponsableUsuarioId()), tarea.getReprogramaciones(), tarea.getOrigen(),
          tarea.getVersion());
    }).toList();
  }

  private ClienteDeTarea clienteDe(TareaSeguimiento tarea) {
    if (tarea.getClienteId() == null) return null;
    return clientes.buscar(tarea.getOrganizacionId(), tarea.getClienteId())
        .map(c -> new ClienteDeTarea(c.id(), c.codigo(), c.razonSocial())).orElse(null);
  }

  /**nombres de los usuarios indicados, para mostrarlos en las respuestas*/
  @Transactional(readOnly = true)
  public Map<UUID, Referencia> personas(Collection<UUID> ids) {
    return directorio.buscarPorIds(actorActual.requerido().organizacionId(), ids).values().stream()
        .collect(Collectors.toMap(UsuarioResumen::id, u -> new Referencia(u.id(), u.nombre())));
  }

  private boolean vencida(TareaSeguimiento tarea) {
    return tarea.estaVencida(LocalDate.now(reloj), LocalTime.now(reloj));
  }

  private static void verificarVersion(TareaSeguimiento tarea, Long version) {
    if (!Objects.equals(tarea.getVersion(), version)) {
      throw new ConflictoException("CONFLICTO_VERSION",
          "La tarea fue modificada por otra persona. Recarga los datos y vuelve a intentarlo.");
    }
  }

  private static String descripcionEditable(TareaSeguimiento tarea) {
    return tarea.getTitulo() + " | " + tarea.getPrioridad() + (tarea.getDescripcion() == null ? "" : " | " + tarea.getDescripcion());
  }

  private static String fechaTexto(LocalDate fecha, LocalTime hora) {
    return fecha + (hora == null ? "" : " " + hora);
  }

  private static Map<String, Object> valor(String texto) {
    if (texto == null) return null;
    Map<String, Object> mapa = new LinkedHashMap<>();
    mapa.put("valor", texto);
    return mapa;
  }

  private static String recortar(String texto) {
    return texto == null || texto.length() <= 300 ? texto : texto.substring(0, 299) + "…";
  }

  static String limpio(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
}
