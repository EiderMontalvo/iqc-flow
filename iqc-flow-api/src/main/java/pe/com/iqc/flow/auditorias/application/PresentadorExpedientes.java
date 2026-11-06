package pe.com.iqc.flow.auditorias.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.auditores.ConsultaAuditores;
import pe.com.iqc.flow.auditores.ConsultaAuditores.AuditorReferencia;
import pe.com.iqc.flow.auditores.RolAuditor;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.AliadoRef;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.AuditoriaRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ClienteRef;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ExpedienteDetalle;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ExpedienteResumen;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.HistorialRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.MiembroRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.NormaRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.Referencia;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SedeRespuesta;
import pe.com.iqc.flow.auditorias.domain.Auditoria;
import pe.com.iqc.flow.auditorias.domain.EstadoAuditoria;
import pe.com.iqc.flow.auditorias.domain.Expediente;
import pe.com.iqc.flow.auditorias.domain.FlujoExpediente;
import pe.com.iqc.flow.auditorias.domain.HistorialExpediente;
import pe.com.iqc.flow.auditorias.domain.MiembroEquipo;
import pe.com.iqc.flow.auditorias.domain.NormaExpediente;
import pe.com.iqc.flow.auditorias.domain.DeclaracionConflicto;
import pe.com.iqc.flow.auditorias.infrastructure.AuditoriaRepository;
import pe.com.iqc.flow.auditorias.infrastructure.DeclaracionConflictoRepository;
import pe.com.iqc.flow.auditorias.infrastructure.HistorialExpedienteRepository;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.EsquemaCatalogo;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.comercial.ServicioAliados;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;

/**arma las respuestas con nombres legibles (cliente, normas, auditores, usuarios) y los avisos de cada auditoría*/
@Component
class PresentadorExpedientes {
  private final AuditoriaRepository auditorias;
  private final DeclaracionConflictoRepository declaraciones;
  private final HistorialExpedienteRepository historial;
  private final ConsultaClientes clientes;
  private final ConsultaAuditores auditores;
  private final ServicioCatalogos catalogos;
  private final ServicioAliados aliados;
  private final DirectorioUsuarios directorio;

  PresentadorExpedientes(AuditoriaRepository auditorias, DeclaracionConflictoRepository declaraciones,
      HistorialExpedienteRepository historial,
      ConsultaClientes clientes, ConsultaAuditores auditores, ServicioCatalogos catalogos, ServicioAliados aliados,
      DirectorioUsuarios directorio) {
    this.auditorias = auditorias;
    this.declaraciones = declaraciones;
    this.historial = historial;
    this.clientes = clientes;
    this.auditores = auditores;
    this.catalogos = catalogos;
    this.aliados = aliados;
    this.directorio = directorio;
  }

  boolean decideIndia(String esquemaCodigo) {
    return catalogos.esquema(esquemaCodigo).map(e -> Boolean.FALSE.equals(e.decideIqcPeru())).orElse(false);
  }

  List<ExpedienteResumen> resumir(UUID organizacionId, List<Expediente> lista) {
    Map<UUID, ClienteReferencia> refs = clientes.buscarPorIds(organizacionId,
        lista.stream().map(Expediente::getClienteId).collect(Collectors.toSet()));
    Map<UUID, Referencia> personas = personas(organizacionId,
        lista.stream().map(Expediente::getResponsableUsuarioId).collect(Collectors.toSet()));
    return lista.stream().map(e -> new ExpedienteResumen(e.getId(), e.getCodigo(), cliente(refs.get(e.getClienteId())),
        e.getEsquemaCodigo(), e.getTipoServicio(), e.getEstado(),
        normas(e.getNormas()).stream().map(NormaRespuesta::designacion).toList(),
        personas.get(e.getResponsableUsuarioId()), e.getFechaCertificacion(), e.getFechaExpiracion(),
        e.getEstadoCambiadoEn(), e.getVersion())).toList();
  }

  ExpedienteDetalle detalle(Expediente e) {
    UUID organizacionId = e.getOrganizacionId();
    List<Auditoria> lista = auditorias.findByExpedienteIdOrderByCreadoEnAsc(e.getId());
    List<HistorialExpediente> eventos = historial.findByExpedienteIdOrderByOcurridoEnAsc(e.getId());
    Set<UUID> usuarios = new HashSet<>();
    usuarios.add(e.getResponsableUsuarioId());
    eventos.stream().map(HistorialExpediente::getActorUsuarioId).filter(Objects::nonNull).forEach(usuarios::add);
    Map<UUID, Referencia> personas = personas(organizacionId, usuarios);
    EsquemaCatalogo esquema = catalogos.esquema(e.getEsquemaCodigo()).orElseThrow();
    boolean decideIndia = Boolean.FALSE.equals(esquema.decideIqcPeru());
    Map<UUID, ConsultaClientes.DireccionReferencia> direcciones = clientes.direccionesDeCliente(e.getClienteId());
    List<SedeRespuesta> sedes = e.getSedes().stream().map(direcciones::get).filter(Objects::nonNull)
        .map(d -> new SedeRespuesta(d.id(), d.tipo(), d.nombre(), d.direccion())).toList();
    AliadoRef aliado = e.getAliadoId() == null ? null : aliados.buscar(organizacionId, e.getAliadoId())
        .map(a -> new AliadoRef(a.id(), a.codigo(), a.nombre())).orElse(null);
    return new ExpedienteDetalle(e.getId(), e.getCodigo(),
        cliente(clientes.buscar(organizacionId, e.getClienteId()).orElse(null)), esquema.codigo(), esquema.nombre(),
        decideIndia, e.getTipoServicio(), e.getEstado(), e.getEstadoCambiadoEn(), normas(e.getNormas()), sedes,
        e.getAlcance(), aliado, e.getComisionPorcentaje(), e.getNumeroContrato(), e.getCodigoExterno(),
        personas.get(e.getResponsableUsuarioId()), e.getFechaCertificacion(), e.getFechaExpiracion(), e.getNotas(),
        auditorias(organizacionId, e, lista),
        eventos.stream().map(h -> new HistorialRespuesta(h.getEstadoDesde(), h.getEstadoHacia(), h.getMotivo(),
            h.getActorUsuarioId() == null ? null : personas.get(h.getActorUsuarioId()), h.getOcurridoEn())).toList(),
        FlujoExpediente.siguientesManuales(e.getEstado(), decideIndia, e.getTipoServicio()), e.getCreadoEn(), e.getVersion());
  }

  List<AuditoriaRespuesta> auditorias(UUID organizacionId, Expediente expediente, Collection<Auditoria> lista) {
    Map<UUID, AuditorReferencia> refs = auditores.buscarPorIds(organizacionId, lista.stream()
        .flatMap(a -> a.getEquipo().stream()).map(MiembroEquipo::auditorId).collect(Collectors.toSet()));
    List<NormaRespuesta> normas = normas(expediente.getNormas());
    Map<UUID, List<DeclaracionConflicto>> declaradas = lista.isEmpty() ? Map.of()
        : declaraciones.findByAuditoriaIdIn(lista.stream().map(Auditoria::getId).toList()).stream()
            .collect(Collectors.groupingBy(DeclaracionConflicto::getAuditoriaId));
    return lista.stream().map(a -> new AuditoriaRespuesta(a.getId(), a.getTipo(), a.getEstado(), a.getFechaLimite(),
        a.getFechaInicioPlan(), a.getFechaFinPlan(), a.getFechaRealInicio(), a.getFechaRealFin(), a.getDiasAuditor(),
        a.getModalidad(), a.getEstadoPaquete(), a.getNotas(),
        a.getEquipo().stream()
            .sorted(Comparator.comparing(MiembroEquipo::rol))
            .map(m -> new MiembroRespuesta(m.auditorId(),
                refs.containsKey(m.auditorId()) ? refs.get(m.auditorId()).nombreCompleto() : null, m.rol()))
            .toList(),
        advertencias(a, refs, normas, declaradas.getOrDefault(a.getId(), List.of())), a.getVersion())).toList();
  }

  List<NormaRespuesta> normas(Set<NormaExpediente> normas) {
    return normas.stream()
        .map(n -> catalogos.buscarEdicion(n.normaCodigo(), n.edicion())
            .map(c -> new NormaRespuesta(c.normaCodigo(), c.nombreCorto(), c.edicion(), c.designacion(), c.codigoSistema()))
            .orElse(new NormaRespuesta(n.normaCodigo(), n.normaCodigo(), n.edicion(), n.normaCodigo() + ":" + n.edicion(), null)))
        .sorted(Comparator.comparing(n -> n.codigoSistema() == null ? "99" : n.codigoSistema()))
        .toList();
  }

  ClienteRef cliente(ClienteReferencia ref) {
    return ref == null ? null : new ClienteRef(ref.id(), ref.codigo(), ref.razonSocial());
  }

  Map<UUID, Referencia> personas(UUID organizacionId, Collection<UUID> ids) {
    return directorio.buscarPorIds(organizacionId, ids).values().stream()
        .collect(Collectors.toMap(UsuarioResumen::id, u -> new Referencia(u.id(), u.nombre())));
  }

  private static List<String> advertencias(Auditoria a, Map<UUID, AuditorReferencia> refs, List<NormaRespuesta> normas,
      List<DeclaracionConflicto> declaradas) {
    List<String> avisos = new ArrayList<>();
    boolean pendiente = a.getEstado() == EstadoAuditoria.POR_PROGRAMAR || a.getEstado() == EstadoAuditoria.PROGRAMADA;
    if (pendiente && a.getEquipo().isEmpty()) avisos.add("Sin equipo auditor asignado.");
    else if (pendiente && !a.tieneLider()) avisos.add("Sin auditor líder.");
    for (MiembroEquipo m : a.getEquipo()) {
      AuditorReferencia auditor = refs.get(m.auditorId());
      if (auditor == null || (m.rol() != RolAuditor.LIDER && m.rol() != RolAuditor.AUDITOR)) continue;
      for (NormaRespuesta norma : normas) {
        if (!auditor.normas().contains(norma.norma())) {
          avisos.add(auditor.nombreCompleto() + " no tiene registrada competencia en " + norma.nombreCorto() + ".");
        }
      }
    }
    if (pendiente) {
      Map<UUID, Boolean> conflicto = declaradas.stream()
          .collect(Collectors.toMap(DeclaracionConflicto::getAuditorId, DeclaracionConflicto::isTieneConflicto));
      for (MiembroEquipo m : a.getEquipo()) {
        String nombre = refs.containsKey(m.auditorId()) ? refs.get(m.auditorId()).nombreCompleto() : m.auditorId().toString();
        if (!conflicto.containsKey(m.auditorId())) avisos.add("Falta la declaración de conflicto de interés de " + nombre + ".");
        else if (conflicto.get(m.auditorId())) avisos.add(nombre + " declaró conflicto de interés: retíralo del equipo.");
      }
    }
    LocalDate limite = a.getFechaLimite();
    if (limite != null && a.getFechaInicioPlan() != null && a.getFechaInicioPlan().isAfter(limite)) {
      avisos.add("La fecha planificada es posterior a la fecha límite (" + limite + ").");
    }
    return avisos;
  }
}
