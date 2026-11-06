package pe.com.iqc.flow.auditorias.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.CertificadoRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DecisionRegistrada;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DecisionRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudDecision;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.Referencia;
import pe.com.iqc.flow.auditorias.domain.Certificado;
import pe.com.iqc.flow.auditorias.domain.DecisionCertificacion;
import pe.com.iqc.flow.auditorias.domain.DecisionCertificacion.Clasificacion;
import pe.com.iqc.flow.auditorias.domain.DecisionCertificacion.Via;
import pe.com.iqc.flow.auditorias.domain.EstadoExpediente;
import pe.com.iqc.flow.auditorias.domain.Expediente;
import pe.com.iqc.flow.auditorias.domain.HistorialExpediente;
import pe.com.iqc.flow.auditorias.domain.NormaExpediente;
import pe.com.iqc.flow.auditorias.domain.TipoDecision;
import pe.com.iqc.flow.auditorias.infrastructure.CertificadoRepository;
import pe.com.iqc.flow.auditorias.infrastructure.DecisionCertificacionRepository;
import pe.com.iqc.flow.auditorias.infrastructure.ExpedienteRepository;
import pe.com.iqc.flow.auditorias.infrastructure.HistorialExpedienteRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas.NuevaTareaAutomatica;

/**
 *decisiones de certificación (DDC-PRO-003, solo INACAL y SAE: en IQC Global decide India). Aplica el control
 *de imparcialidad y los efectos de cada tipo sobre el expediente, sus certificados y las tareas de plazos
 */
@Service
public class ServicioDecisiones {
  private static final String ENTIDAD = "DECISION";
  private static final String TAREA = "SEGUIMIENTO_SUSPENSION";

  private final DecisionCertificacionRepository decisiones;
  private final CertificadoRepository certificados;
  private final ExpedienteRepository expedientesRepo;
  private final HistorialExpedienteRepository historial;
  private final ServicioExpedientes expedientes;
  private final ServicioCertificados servicioCertificados;
  private final ServicioImparcialidad imparcialidad;
  private final PresentadorExpedientes presentador;
  private final DirectorioUsuarios directorio;
  private final ServicioDocumentos documentos;
  private final ProgramadorTareas programador;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioDecisiones(DecisionCertificacionRepository decisiones, CertificadoRepository certificados,
      ExpedienteRepository expedientesRepo, HistorialExpedienteRepository historial, ServicioExpedientes expedientes,
      ServicioCertificados servicioCertificados, ServicioImparcialidad imparcialidad, PresentadorExpedientes presentador,
      DirectorioUsuarios directorio, ServicioDocumentos documentos, ProgramadorTareas programador,
      ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.decisiones = decisiones;
    this.certificados = certificados;
    this.expedientesRepo = expedientesRepo;
    this.historial = historial;
    this.expedientes = expedientes;
    this.servicioCertificados = servicioCertificados;
    this.imparcialidad = imparcialidad;
    this.presentador = presentador;
    this.directorio = directorio;
    this.documentos = documentos;
    this.programador = programador;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public List<DecisionRespuesta> listar(UUID expedienteId) {
    Expediente e = expedientes.cargar(expedienteId);
    List<DecisionCertificacion> lista = decisiones.findByExpedienteIdOrderByFechaDecisionAscCreadoEnAsc(e.getId());
    Set<UUID> usuarios = new HashSet<>();
    lista.forEach(d -> {
      usuarios.add(d.getDecisorUsuarioId());
      usuarios.add(d.getRegistradoPor());
    });
    usuarios.remove(null);
    Map<UUID, Referencia> personas = presentador.personas(e.getOrganizacionId(), usuarios);
    return lista.stream().map(d -> respuesta(d, personas)).toList();
  }

  @Transactional
  @PreAuthorize("hasAuthority('decisiones:registrar')")
  public DecisionRegistrada registrar(UUID expedienteId, SolicitudDecision s) {
    UsuarioAutenticado actor = actorActual.requerido();
    Expediente e = expedientes.cargar(expedienteId);
    ServicioExpedientes.verificarVersion(e.getVersion(), s.version());
    e.exigirAbierto();
    TipoDecision tipo = s.tipo();
    validar(e, s, tipo);
    List<NormaExpediente> normas = normas(e, s.normas());
    UUID checklist = null;
    if (tipo.exigeChecklist()) {
      if (s.checklistDocumentoId() == null) {
        throw new ReglaNegocioException("CHECKLIST_REQUERIDO",
            "Adjunta el checklist de suspensión (Anexo 1 de DDC-PRO-003) antes de " + (tipo == TipoDecision.RETIRO ? "retirar." : "suspender."));
      }
      checklist = documentos.exigir(e.getOrganizacionId(), s.checklistDocumentoId(), CategoriaDocumento.CHECKLIST_SUSPENSION).id();
    }
    UUID decisor = null;
    UUID acta = null;
    if (s.via() == Via.DECISOR) {
      UsuarioResumen usuario = directorio.buscarActivo(e.getOrganizacionId(), s.decisorId() != null ? s.decisorId() : actor.id())
          .orElseThrow(() -> new ReglaNegocioException("DECISOR_INVALIDO", "El decisor no existe o está deshabilitado."));
      if (!usuario.tienePermiso(ServicioImparcialidad.DECIDIR)) {
        throw new ReglaNegocioException("DECISOR_SIN_PERMISO", usuario.nombre() + " no está autorizado para decidir certificaciones.");
      }
      imparcialidad.exigirImparcial(e, usuario);
      decisor = usuario.id();
    } else {
      if (s.actaDocumentoId() == null) {
        throw new ReglaNegocioException("ACTA_REQUERIDA", "Adjunta el acta del Comité de Imparcialidad.");
      }
      acta = documentos.exigir(e.getOrganizacionId(), s.actaDocumentoId(), CategoriaDocumento.ACTA_COMITE_IMPARCIALIDAD).id();
    }
    DecisionCertificacion decision = decisiones.saveAndFlush(new DecisionCertificacion(e.getId(), tipo, s.clasificacion(),
        s.via(), decisor, acta, ServicioExpedientes.limpio(s.expertoImparcialidad()),
        ServicioExpedientes.limpio(s.recomendacionExperto()), checklist, s.fechaDecision(), s.fundamento().trim(),
        ServicioExpedientes.limpio(s.nuevoAlcance()), normas.stream().map(NormaExpediente::normaCodigo).toList(), actor.id()));
    List<Certificado> afectados = aplicar(e, decision, normas);
    bitacora.registrar(EntradaBitacora.accion("DECISION_REGISTRADA").entidad(ENTIDAD, decision.getId())
        .estadoNuevo(instantanea(e, decision, afectados)));
    Set<UUID> involucrados = new HashSet<>(Set.of(actor.id()));
    if (decisor != null) involucrados.add(decisor);
    Map<UUID, Referencia> personas = presentador.personas(e.getOrganizacionId(), involucrados);
    List<CertificadoRespuesta> respuestas = afectados.isEmpty() ? List.of()
        : servicioCertificados.respuestas(e.getOrganizacionId(), afectados);
    return new DecisionRegistrada(respuesta(decision, personas), e.getEstado(), e.getVersion(), respuestas);
  }

  private void validar(Expediente e, SolicitudDecision s, TipoDecision tipo) {
    if (presentador.decideIndia(e.getEsquemaCodigo())) {
      throw new ReglaNegocioException("DECISION_INDIA",
          "En el esquema IQC Global decide India: registra el certificado que envía (certificados externos).");
    }
    if (!tipo.aplicaA(e.getTipoServicio())) {
      throw new ReglaNegocioException("TIPO_NO_APLICA", "Una decisión de " + tipo + " no aplica a un expediente de " + e.getTipoServicio() + ".");
    }
    if (tipo.esInicial() && e.getEstado() != EstadoExpediente.EN_DECISION) {
      throw new ReglaNegocioException("EXPEDIENTE_NO_EN_DECISION",
          "La decisión de " + tipo + " se registra con el expediente EN_DECISION (está " + e.getEstado() + ").");
    }
    if (!tipo.esInicial() && e.getEstado() != EstadoExpediente.EMITIDO && e.getEstado() != EstadoExpediente.ENTREGADO) {
      throw new ReglaNegocioException("EXPEDIENTE_SIN_CERTIFICADOS",
          "La decisión de " + tipo + " actúa sobre certificados emitidos del expediente.");
    }
    if (tipo.esFavorable() && s.clasificacion() == Clasificacion.OBSERVADO) {
      throw new ReglaNegocioException("REVISION_OBSERVADA",
          "Con la revisión OBSERVADA no se puede decidir " + tipo + ": registra la decisión cuando se levanten las observaciones.");
    }
    if (s.fechaDecision().isAfter(LocalDate.now(reloj))) {
      throw new ReglaNegocioException("FECHA_FUTURA", "La fecha de la decisión no puede ser futura.");
    }
    if (tipo == TipoDecision.REDUCCION && (s.nuevoAlcance() == null || s.nuevoAlcance().isBlank())) {
      throw new ReglaNegocioException("ALCANCE_REQUERIDO", "Indica el nuevo alcance reducido.");
    }
  }

  private static List<NormaExpediente> normas(Expediente e, Set<String> solicitadas) {
    List<NormaExpediente> todas = e.getNormas().stream()
        .sorted((a, b) -> a.normaCodigo().compareTo(b.normaCodigo())).toList();
    if (solicitadas == null || solicitadas.isEmpty()) return todas;
    Set<String> delExpediente = todas.stream().map(NormaExpediente::normaCodigo).collect(Collectors.toSet());
    if (!delExpediente.containsAll(solicitadas)) {
      throw new ReglaNegocioException("NORMA_INVALIDA", "La decisión solo puede aplicarse a normas del expediente.");
    }
    return todas.stream().filter(n -> solicitadas.contains(n.normaCodigo())).toList();
  }

  /**efectos de la decisión; devuelve los certificados creados o modificados*/
  private List<Certificado> aplicar(Expediente e, DecisionCertificacion d, List<NormaExpediente> normas) {
    Instant ahora = Instant.now(reloj);
    UUID actor = actorActual.requerido().id();
    List<Certificado> afectados = new ArrayList<>();
    switch (d.getTipo()) {
      case OTORGAMIENTO, RENOVACION, AMPLIACION, TRANSFERENCIA -> {
        afectados.addAll(servicioCertificados.generarBorradores(e, d, normas));
        avanzar(e, EstadoExpediente.EN_EMISION, "Decisión de " + d.getTipo() + " (" + d.getClasificacion() + ")", actor, ahora);
      }
      case RECHAZO -> avanzar(e, EstadoExpediente.RECHAZADO, "Decisión de rechazo: " + d.getFundamento(), actor, ahora);
      case MANTENIMIENTO -> normas.forEach(n -> {
        Certificado c = servicioCertificados.vigente(e, n.normaCodigo());
        if (c.getEstado() != Certificado.Estado.EMITIDO) {
          throw new ReglaNegocioException("CERTIFICADO_SUSPENDIDO",
              "El certificado " + c.getCodigo() + " está suspendido: corresponde una restauración, no un mantenimiento.");
        }
      });
      case REDUCCION -> {
        afectados.addAll(servicioCertificados.generarBorradores(e, d, normas));
        e.reducirAlcance(d.getNuevoAlcance());
      }
      case SUSPENSION -> {
        normas.forEach(n -> {
          Certificado c = servicioCertificados.vigente(e, n.normaCodigo());
          c.suspender();
          afectados.add(c);
        });
        programarPlazosSuspension(e, d, afectados);
      }
      case RESTAURACION -> normas.forEach(n -> {
        Certificado c = servicioCertificados.vigente(e, n.normaCodigo());
        c.restaurar();
        afectados.add(c);
      });
      case RETIRO, CANCELACION_VOLUNTARIA -> normas.forEach(n -> {
        Certificado c = servicioCertificados.vigente(e, n.normaCodigo());
        c.retirar(d.getTipo() == TipoDecision.CANCELACION_VOLUNTARIA);
        afectados.add(c);
      });
    }
    certificados.flush();
    expedientesRepo.flush();
    if (d.getTipo() == TipoDecision.RESTAURACION || d.getTipo() == TipoDecision.RETIRO
        || d.getTipo() == TipoDecision.CANCELACION_VOLUNTARIA) {
      cerrarPlazosSiNoQuedanSuspendidos(e, d);
    }
    return afectados;
  }

  private void avanzar(Expediente e, EstadoExpediente nuevo, String motivo, UUID actor, Instant ahora) {
    EstadoExpediente anterior = e.getEstado();
    e.avanzarPorSistema(nuevo, false, ahora);
    expedientesRepo.flush();
    historial.save(new HistorialExpediente(e.getId(), anterior, nuevo, motivo, actor, ahora));
  }

  /**
   *DDC-PRO-003: la restauración debe darse dentro de 6 meses; si la suspensión no se resuelve en 1 año (o 2 meses
   *antes del vencimiento, si queda menos) corresponde el retiro. Se crean tareas para el responsable del expediente
   */
  private void programarPlazosSuspension(Expediente e, DecisionCertificacion d, List<Certificado> suspendidos) {
    LocalDate restauracion = d.getFechaDecision().plusMonths(6);
    LocalDate retiro = d.getFechaDecision().plusYears(1);
    LocalDate vencimiento = suspendidos.stream().map(Certificado::getFechaExpiracion).filter(Objects::nonNull)
        .min(LocalDate::compareTo).orElse(null);
    if (vencimiento != null && vencimiento.minusMonths(2).isBefore(retiro)) retiro = vencimiento.minusMonths(2);
    String codigos = suspendidos.stream().map(Certificado::getCodigo).collect(Collectors.joining(", "));
    programador.crearTareaAutomatica(new NuevaTareaAutomatica(e.getOrganizacionId(),
        "Verificar la restauración de " + e.getCodigo(),
        "Certificados suspendidos: " + codigos + ". La restauración debe resolverse antes del " + restauracion + ".",
        TAREA, ServicioExpedientes.ENTIDAD, e.getId(), e.getClienteId(), e.getResponsableUsuarioId(), restauracion,
        clave(e, "restauracion")));
    programador.crearTareaAutomatica(new NuevaTareaAutomatica(e.getOrganizacionId(),
        "Decidir el retiro de " + e.getCodigo() + " si la suspensión no se resolvió",
        "Certificados suspendidos: " + codigos + ". Sin restauración, corresponde el retiro el " + retiro + ".",
        TAREA, ServicioExpedientes.ENTIDAD, e.getId(), e.getClienteId(), e.getResponsableUsuarioId(), retiro,
        clave(e, "retiro")));
  }

  private void cerrarPlazosSiNoQuedanSuspendidos(Expediente e, DecisionCertificacion d) {
    boolean quedanSuspendidos = certificados.findByExpedienteIdOrderByNormaCodigoAscCreadoEnAsc(e.getId()).stream()
        .anyMatch(c -> c.getEstado() == Certificado.Estado.SUSPENDIDO);
    if (quedanSuspendidos) return;
    String motivo = "Suspensión resuelta por decisión de " + d.getTipo() + " del " + d.getFechaDecision();
    programador.cancelarTareaAutomatica(e.getOrganizacionId(), clave(e, "restauracion"), motivo);
    programador.cancelarTareaAutomatica(e.getOrganizacionId(), clave(e, "retiro"), motivo);
  }

  private static String clave(Expediente e, String plazo) { return "expediente:" + e.getId() + ":suspension:" + plazo; }

  private static Map<String, Object> instantanea(Expediente e, DecisionCertificacion d, List<Certificado> afectados) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("expediente", e.getCodigo());
    datos.put("tipo", d.getTipo().name());
    datos.put("clasificacion", d.getClasificacion().name());
    datos.put("via", d.getVia().name());
    datos.put("decisorId", d.getDecisorUsuarioId());
    datos.put("fechaDecision", d.getFechaDecision().toString());
    datos.put("normas", d.getNormas().stream().sorted().toList());
    datos.put("estadoExpediente", e.getEstado().name());
    datos.put("certificados", afectados.stream().map(c -> c.getCodigo() + ":" + c.getEstado()).toList());
    return datos;
  }

  private static DecisionRespuesta respuesta(DecisionCertificacion d, Map<UUID, Referencia> personas) {
    return new DecisionRespuesta(d.getId(), d.getTipo(), d.getClasificacion(), d.getVia(),
        d.getDecisorUsuarioId() == null ? null : personas.get(d.getDecisorUsuarioId()), d.getActaDocumentoId(),
        d.getExpertoImparcialidad(), d.getRecomendacionExperto(), d.getChecklistDocumentoId(), d.getFechaDecision(),
        d.getFundamento(), d.getNuevoAlcance(), d.getNormas(), personas.get(d.getRegistradoPor()), d.getCreadoEn());
  }
}
