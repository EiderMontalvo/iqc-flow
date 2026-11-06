package pe.com.iqc.flow.auditorias.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditores.ConsultaAuditores;
import pe.com.iqc.flow.auditores.ConsultaAuditores.AuditorReferencia;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.AuditoriaRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.AuditoriaSinEquipo;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ClienteRef;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.MiembroSolicitado;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudActualizarAuditoria;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudAuditoria;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudEquipo;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.VigilanciaPendiente;
import pe.com.iqc.flow.auditorias.domain.Auditoria;
import pe.com.iqc.flow.auditorias.domain.EstadoAuditoria;
import pe.com.iqc.flow.auditorias.domain.Expediente;
import pe.com.iqc.flow.auditorias.domain.MiembroEquipo;
import pe.com.iqc.flow.auditorias.domain.TipoAuditoria;
import pe.com.iqc.flow.auditorias.domain.TipoServicio;
import pe.com.iqc.flow.auditorias.infrastructure.AuditoriaRepository;
import pe.com.iqc.flow.auditorias.infrastructure.ExpedienteRepository;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;

/**auditorías de un expediente: planificación, ejecución, equipo auditor y vistas de vigilancias*/
@Service
public class ServicioAuditorias {
  private static final String ENTIDAD = "AUDITORIA";
  private static final Set<TipoAuditoria> VIGILANCIAS = Set.of(TipoAuditoria.VIGILANCIA_1, TipoAuditoria.VIGILANCIA_2);
  private static final Set<EstadoAuditoria> PENDIENTES = Set.of(EstadoAuditoria.POR_PROGRAMAR, EstadoAuditoria.PROGRAMADA);

  /**vistas de vigilancias pendientes*/
  public enum VistaVigilancias { PROXIMAS, VENCIDAS }

  private final AuditoriaRepository auditorias;
  private final ExpedienteRepository expedientes;
  private final ServicioExpedientes servicioExpedientes;
  private final PresentadorExpedientes presentador;
  private final ConsultaAuditores auditores;
  private final ConsultaClientes clientes;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioAuditorias(AuditoriaRepository auditorias, ExpedienteRepository expedientes,
      ServicioExpedientes servicioExpedientes, PresentadorExpedientes presentador, ConsultaAuditores auditores,
      ConsultaClientes clientes, ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.auditorias = auditorias;
    this.expedientes = expedientes;
    this.servicioExpedientes = servicioExpedientes;
    this.presentador = presentador;
    this.auditores = auditores;
    this.clientes = clientes;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional
  @PreAuthorize("hasAuthority('expedientes:gestionar')")
  public AuditoriaRespuesta crear(UUID expedienteId, SolicitudAuditoria s) {
    Expediente e = servicioExpedientes.cargar(expedienteId);
    e.exigirAbierto();
    if (s.tipo() == TipoAuditoria.ETAPA_1 && e.getTipoServicio() != TipoServicio.CERTIFICACION_INICIAL) {
      throw new ReglaNegocioException("ETAPA1_NO_APLICA", "La Etapa 1 solo aplica a certificaciones iniciales.");
    }
    if (s.tipo() != TipoAuditoria.EXTRAORDINARIA
        && auditorias.existsByExpedienteIdAndTipoAndEstadoNot(e.getId(), s.tipo(), EstadoAuditoria.CANCELADA)) {
      throw new ConflictoException("AUDITORIA_DUPLICADA", "El expediente ya tiene una auditoría " + s.tipo() + ".");
    }
    Auditoria auditoria = new Auditoria(e.getId(), s.tipo(), s.fechaLimite());
    auditoria.planificar(s.fechaLimite(), s.fechaInicioPlan(), s.fechaFinPlan(), s.diasAuditor(), s.modalidad(),
        ServicioExpedientes.limpio(s.notas()));
    auditorias.saveAndFlush(auditoria);
    registrar("AUDITORIA_CREADA", auditoria, null);
    return respuesta(e, auditoria);
  }

  @Transactional
  @PreAuthorize("hasAuthority('expedientes:gestionar')")
  public AuditoriaRespuesta actualizar(UUID expedienteId, UUID auditoriaId, SolicitudActualizarAuditoria s) {
    Expediente e = servicioExpedientes.cargar(expedienteId);
    Auditoria a = cargar(e, auditoriaId);
    ServicioExpedientes.verificarVersion(a.getVersion(), s.version());
    if (Boolean.TRUE.equals(s.cancelar())) {
      if (s.motivoCancelacion() == null || s.motivoCancelacion().isBlank()) {
        throw new ReglaNegocioException("MOTIVO_REQUERIDO", "Indica el motivo de la cancelación de la auditoría.");
      }
      a.cancelar(s.motivoCancelacion().trim());
      auditorias.flush();
      registrar("AUDITORIA_CANCELADA", a, s.motivoCancelacion().trim());
      return respuesta(e, a);
    }
    a.planificar(
        s.fechaLimite() != null ? s.fechaLimite() : a.getFechaLimite(),
        s.fechaInicioPlan() != null ? s.fechaInicioPlan() : a.getFechaInicioPlan(),
        s.fechaFinPlan() != null ? s.fechaFinPlan() : a.getFechaFinPlan(),
        s.diasAuditor() != null ? s.diasAuditor() : a.getDiasAuditor(),
        s.modalidad() != null ? s.modalidad() : a.getModalidad(),
        ServicioExpedientes.opcional(s.notas(), a.getNotas()));
    if (s.fechaRealInicio() != null) {
      if (s.fechaRealInicio().isAfter(LocalDate.now(reloj))) {
        throw new ReglaNegocioException("FECHA_FUTURA", "La fecha real de la auditoría no puede ser futura.");
      }
      a.registrarEjecucion(s.fechaRealInicio(), s.fechaRealFin());
    }
    if (s.estadoPaquete() != null) {
      if (!presentador.decideIndia(e.getEsquemaCodigo())) {
        throw new ReglaNegocioException("PAQUETE_NO_APLICA", "El seguimiento de paquetes solo aplica al esquema IQC Global.");
      }
      a.cambiarEstadoPaquete(s.estadoPaquete());
    }
    auditorias.flush();
    registrar("AUDITORIA_ACTUALIZADA", a, null);
    return respuesta(e, a);
  }

  /**reemplaza el equipo. Cada auditor debe estar activo y poder cumplir el rol asignado; hay un solo líder*/
  @Transactional
  @PreAuthorize("hasAuthority('expedientes:gestionar')")
  public AuditoriaRespuesta asignarEquipo(UUID expedienteId, UUID auditoriaId, SolicitudEquipo s) {
    Expediente e = servicioExpedientes.cargar(expedienteId);
    Auditoria a = cargar(e, auditoriaId);
    ServicioExpedientes.verificarVersion(a.getVersion(), s.version());
    Map<UUID, AuditorReferencia> refs = auditores.buscarPorIds(e.getOrganizacionId(),
        s.miembros().stream().map(MiembroSolicitado::auditorId).collect(Collectors.toSet()));
    if (s.miembros().stream().map(MiembroSolicitado::auditorId).distinct().count() != s.miembros().size()) {
      throw new ReglaNegocioException("AUDITOR_REPETIDO", "Un auditor solo puede figurar una vez en el equipo.");
    }
    for (MiembroSolicitado m : s.miembros()) {
      AuditorReferencia auditor = refs.get(m.auditorId());
      if (auditor == null || !auditor.activo()) {
        throw new ReglaNegocioException("AUDITOR_INVALIDO", "El auditor " + m.auditorId() + " no existe o está inactivo.");
      }
      if (!auditor.roles().contains(m.rol())) {
        throw new ReglaNegocioException("ROL_NO_PERMITIDO", auditor.nombreCompleto() + " no está registrado para el rol " + m.rol() + ".");
      }
    }
    a.asignarEquipo(s.miembros().stream().map(m -> new MiembroEquipo(m.auditorId(), m.rol())).toList());
    auditorias.flush();
    bitacora.registrar(EntradaBitacora.accion("EQUIPO_AUDITOR_ASIGNADO").entidad(ENTIDAD, a.getId())
        .estadoNuevo(Map.of("miembros", s.miembros().stream().map(m -> m.auditorId() + ":" + m.rol()).toList())));
    return respuesta(e, a);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public List<VigilanciaPendiente> vigilancias(VistaVigilancias vista, int dias) {
    UUID organizacionId = actorActual.requerido().organizacionId();
    LocalDate hoy = LocalDate.now(reloj);
    List<Auditoria> lista = vista == VistaVigilancias.VENCIDAS
        ? auditorias.pendientesEntre(organizacionId, VIGILANCIAS, PENDIENTES, LocalDate.of(1900, 1, 1), hoy.minusDays(1))
        : auditorias.pendientesEntre(organizacionId, VIGILANCIAS, PENDIENTES, hoy, hoy.plusDays(dias));
    Map<UUID, Expediente> porId = expedientesDe(lista);
    Map<UUID, ClienteReferencia> refs = clientesDe(organizacionId, porId);
    return lista.stream().map(a -> {
      Expediente e = porId.get(a.getExpedienteId());
      return new VigilanciaPendiente(a.getId(), a.getTipo(), a.getEstado(), a.getFechaLimite(),
          ChronoUnit.DAYS.between(hoy, a.getFechaLimite()), e.getId(), e.getCodigo(),
          presentador.cliente(refs.get(e.getClienteId())), e.getEsquemaCodigo());
    }).toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public List<AuditoriaSinEquipo> sinEquipo() {
    UUID organizacionId = actorActual.requerido().organizacionId();
    List<Auditoria> lista = auditorias.sinEquipo(organizacionId, PENDIENTES);
    Map<UUID, Expediente> porId = expedientesDe(lista);
    Map<UUID, ClienteReferencia> refs = clientesDe(organizacionId, porId);
    return lista.stream().map(a -> {
      Expediente e = porId.get(a.getExpedienteId());
      ClienteRef cliente = presentador.cliente(refs.get(e.getClienteId()));
      return new AuditoriaSinEquipo(a.getId(), a.getTipo(), a.getEstado(), a.getFechaLimite(), a.getFechaInicioPlan(),
          e.getId(), e.getCodigo(), cliente);
    }).toList();
  }

  private Map<UUID, Expediente> expedientesDe(List<Auditoria> lista) {
    return expedientes.findAllById(lista.stream().map(Auditoria::getExpedienteId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(Expediente::getId, Function.identity()));
  }

  private Map<UUID, ClienteReferencia> clientesDe(UUID organizacionId, Map<UUID, Expediente> porId) {
    return clientes.buscarPorIds(organizacionId,
        porId.values().stream().map(Expediente::getClienteId).collect(Collectors.toSet()));
  }

  private Auditoria cargar(Expediente e, UUID auditoriaId) {
    return auditorias.findByIdAndExpedienteId(auditoriaId, e.getId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Auditoría", auditoriaId));
  }

  private AuditoriaRespuesta respuesta(Expediente e, Auditoria a) {
    return presentador.auditorias(e.getOrganizacionId(), e, List.of(a)).get(0);
  }

  private void registrar(String accion, Auditoria a, String motivo) {
    bitacora.registrar(EntradaBitacora.accion(accion).entidad(ENTIDAD, a.getId())
        .estadoNuevo(Map.of("expedienteId", a.getExpedienteId(), "tipo", a.getTipo().name(), "estado", a.getEstado().name()))
        .motivo(motivo));
  }
}
