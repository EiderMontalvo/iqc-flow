package pe.com.iqc.flow.auditorias.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditores.ConsultaAuditores;
import pe.com.iqc.flow.auditores.ConsultaAuditores.AuditorReferencia;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DeclaracionPendiente;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DeclaracionRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DeclaracionesAuditoria;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DecisorPosible;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudDeclaracion;
import pe.com.iqc.flow.auditorias.domain.Auditoria;
import pe.com.iqc.flow.auditorias.domain.DeclaracionConflicto;
import pe.com.iqc.flow.auditorias.domain.EstadoAuditoria;
import pe.com.iqc.flow.auditorias.domain.Expediente;
import pe.com.iqc.flow.auditorias.domain.MiembroEquipo;
import pe.com.iqc.flow.auditorias.infrastructure.AuditoriaRepository;
import pe.com.iqc.flow.auditorias.infrastructure.DeclaracionConflictoRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;

/**
 *imparcialidad (DDC-PRO-003): declaraciones de conflicto de interés del equipo auditor y control automático
 *de que quien decide no haya participado en ninguna auditoría del cliente
 */
@Service
public class ServicioImparcialidad {
  static final String DECIDIR = "decisiones:registrar";

  private final ServicioExpedientes expedientes;
  private final AuditoriaRepository auditorias;
  private final DeclaracionConflictoRepository declaraciones;
  private final ConsultaAuditores auditores;
  private final DirectorioUsuarios directorio;
  private final ServicioDocumentos documentos;
  private final PresentadorExpedientes presentador;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioImparcialidad(ServicioExpedientes expedientes, AuditoriaRepository auditorias,
      DeclaracionConflictoRepository declaraciones, ConsultaAuditores auditores, DirectorioUsuarios directorio,
      ServicioDocumentos documentos, PresentadorExpedientes presentador, ActorActual actorActual, Bitacora bitacora,
      Clock reloj) {
    this.expedientes = expedientes;
    this.auditorias = auditorias;
    this.declaraciones = declaraciones;
    this.auditores = auditores;
    this.directorio = directorio;
    this.documentos = documentos;
    this.presentador = presentador;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  /**usuarios de IQC Flow vinculados a auditores que integraron cualquier auditoría (no cancelada) del cliente*/
  public Set<UUID> usuariosConParticipacion(Expediente e) {
    List<UUID> ids = auditorias.auditoresDelCliente(e.getOrganizacionId(), e.getClienteId());
    return auditores.buscarPorIds(e.getOrganizacionId(), ids).values().stream()
        .map(AuditorReferencia::usuarioId).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  /**bloquea la decisión si el decisor participó en alguna auditoría del cliente*/
  public void exigirImparcial(Expediente e, UsuarioResumen decisor) {
    if (usuariosConParticipacion(e).contains(decisor.id())) {
      throw new ReglaNegocioException("CONFLICTO_IMPARCIALIDAD", decisor.nombre()
          + " participó en una auditoría de este cliente y no puede decidir. Deriva la decisión al Comité de"
          + " Imparcialidad (vía COMITE_IMPARCIALIDAD, con su acta adjunta) o elige otro decisor habilitado.");
    }
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public List<DecisorPosible> decisores(UUID expedienteId) {
    Expediente e = expedientes.cargar(expedienteId);
    Set<UUID> conParticipacion = usuariosConParticipacion(e);
    return directorio.activosConPermiso(e.getOrganizacionId(), DECIDIR).stream()
        .map(u -> conParticipacion.contains(u.id())
            ? new DecisorPosible(u.id(), u.nombre(), false, "Participó en una auditoría de este cliente.")
            : new DecisorPosible(u.id(), u.nombre(), true, null))
        .sorted(Comparator.comparing(DecisorPosible::habilitado).reversed())
        .toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public DeclaracionesAuditoria declaraciones(UUID expedienteId, UUID auditoriaId) {
    Expediente e = expedientes.cargar(expedienteId);
    Auditoria a = auditoria(e, auditoriaId);
    List<DeclaracionConflicto> lista = declaraciones.findByAuditoriaId(a.getId());
    Map<UUID, AuditorReferencia> refs = auditores.buscarPorIds(e.getOrganizacionId(),
        a.getEquipo().stream().map(MiembroEquipo::auditorId).collect(Collectors.toSet()));
    Map<UUID, MiembroEquipo> equipo = a.getEquipo().stream()
        .collect(Collectors.toMap(MiembroEquipo::auditorId, Function.identity()));
    Set<UUID> declarados = new HashSet<>();
    List<DeclaracionRespuesta> respuestas = lista.stream()
        .filter(d -> equipo.containsKey(d.getAuditorId()))
        .peek(d -> declarados.add(d.getAuditorId()))
        .map(d -> respuesta(e.getOrganizacionId(), d, refs.get(d.getAuditorId()), equipo.get(d.getAuditorId())))
        .toList();
    List<DeclaracionPendiente> pendientes = a.getEquipo().stream()
        .filter(m -> !declarados.contains(m.auditorId()))
        .map(m -> new DeclaracionPendiente(m.auditorId(), nombre(refs.get(m.auditorId())), m.rol()))
        .toList();
    return new DeclaracionesAuditoria(respuestas, pendientes);
  }

  /**
   *registra o reemplaza la declaración de un miembro del equipo. Si quien registra es el propio auditor
   *(usuario vinculado), es su firma; si no, requiere expedientes:gestionar y la declaración firmada adjunta
   */
  @Transactional
  @PreAuthorize("isAuthenticated()")
  public DeclaracionRespuesta declarar(UUID expedienteId, UUID auditoriaId, UUID auditorId, SolicitudDeclaracion s) {
    UsuarioAutenticado actor = actorActual.requerido();
    Expediente e = expedientes.cargar(expedienteId);
    Auditoria a = auditoria(e, auditoriaId);
    if (a.getEstado() == EstadoAuditoria.CANCELADA || a.getEstado() == EstadoAuditoria.EJECUTADA) {
      throw new ReglaNegocioException("DECLARACION_CERRADA",
          "Las declaraciones se registran antes de ejecutar la auditoría (está " + a.getEstado() + ").");
    }
    MiembroEquipo miembro = a.getEquipo().stream().filter(m -> m.auditorId().equals(auditorId)).findFirst()
        .orElseThrow(() -> new ReglaNegocioException("AUDITOR_FUERA_DEL_EQUIPO",
            "El auditor no integra el equipo de esta auditoría."));
    AuditorReferencia auditor = auditores.buscarPorIds(e.getOrganizacionId(), Set.of(auditorId)).get(auditorId);
    boolean firmaPropia = auditor != null && actor.id().equals(auditor.usuarioId());
    if (!firmaPropia && !actorActual.tienePermiso("expedientes:gestionar")) {
      throw new AccessDeniedException("Solo el propio auditor o quien gestiona expedientes registra la declaración");
    }
    UUID documentoId = s.documentoId() == null ? null
        : documentos.exigir(e.getOrganizacionId(), s.documentoId(), CategoriaDocumento.DECLARACION_CONFLICTO).id();
    DeclaracionConflicto declaracion = declaraciones.findByAuditoriaIdAndAuditorId(a.getId(), auditorId)
        .map(existente -> {
          ServicioExpedientes.verificarVersion(existente.getVersion(), s.version());
          return existente;
        })
        .orElseGet(() -> new DeclaracionConflicto(a.getId(), auditorId));
    declaracion.declarar(Boolean.TRUE.equals(s.tieneConflicto()), ServicioExpedientes.limpio(s.detalle()),
        firmaPropia ? DeclaracionConflicto.Modo.FIRMA_PROPIA : DeclaracionConflicto.Modo.EN_NOMBRE_DEL_AUDITOR,
        documentoId, actor.id(), Instant.now(reloj));
    declaraciones.saveAndFlush(declaracion);
    bitacora.registrar(EntradaBitacora.accion("DECLARACION_CONFLICTO_REGISTRADA").entidad("AUDITORIA", a.getId())
        .estadoNuevo(Map.of("auditorId", auditorId.toString(), "tieneConflicto", declaracion.isTieneConflicto(),
            "modo", declaracion.getModo().name())));
    return respuesta(e.getOrganizacionId(), declaracion, auditor, miembro);
  }

  private Auditoria auditoria(Expediente e, UUID auditoriaId) {
    return auditorias.findByIdAndExpedienteId(auditoriaId, e.getId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Auditoría", auditoriaId));
  }

  private DeclaracionRespuesta respuesta(UUID organizacionId, DeclaracionConflicto d, AuditorReferencia auditor,
      MiembroEquipo miembro) {
    return new DeclaracionRespuesta(d.getId(), d.getAuditorId(), nombre(auditor), miembro.rol(), d.isTieneConflicto(),
        d.getDetalle(), d.getModo(), d.getDocumentoId(),
        presentador.personas(organizacionId, Set.of(d.getRegistradoPor())).get(d.getRegistradoPor()),
        d.getDeclaradoEn(), d.getVersion());
  }

  private static String nombre(AuditorReferencia auditor) { return auditor == null ? null : auditor.nombreCompleto(); }
}
