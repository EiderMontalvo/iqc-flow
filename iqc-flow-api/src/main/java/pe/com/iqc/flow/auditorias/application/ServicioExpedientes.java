package pe.com.iqc.flow.auditorias.application;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ExpedienteDetalle;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ExpedienteResumen;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.NormaSolicitada;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.ResumenExpedientes;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudActualizarExpediente;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudCertificacion;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudCrearExpediente;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.SolicitudTransicion;
import pe.com.iqc.flow.auditorias.domain.Auditoria;
import pe.com.iqc.flow.auditorias.domain.CicloCertificacion;
import pe.com.iqc.flow.auditorias.domain.EstadoAuditoria;
import pe.com.iqc.flow.auditorias.domain.EstadoExpediente;
import pe.com.iqc.flow.auditorias.domain.Expediente;
import pe.com.iqc.flow.auditorias.domain.FlujoExpediente;
import pe.com.iqc.flow.auditorias.domain.HistorialExpediente;
import pe.com.iqc.flow.auditorias.domain.NormaExpediente;
import pe.com.iqc.flow.auditorias.domain.TipoAuditoria;
import pe.com.iqc.flow.auditorias.domain.TipoServicio;
import pe.com.iqc.flow.auditorias.infrastructure.AuditoriaRepository;
import pe.com.iqc.flow.auditorias.infrastructure.ExpedienteRepository;
import pe.com.iqc.flow.auditorias.infrastructure.HistorialExpedienteRepository;
import pe.com.iqc.flow.auditorias.infrastructure.PropiedadesExpedientes;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.EsquemaCatalogo;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaEdicion;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.comercial.ServicioAliados;
import pe.com.iqc.flow.comercial.ServicioAliados.AliadoRespuesta;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas.NuevaTareaAutomatica;

/**expedientes de certificación: alta, edición, flujo de estados y ciclo de vigilancias*/
@Service
public class ServicioExpedientes {
  static final String ENTIDAD = "EXPEDIENTE";
  private static final String GESTIONAR = "expedientes:gestionar";

  private final ExpedienteRepository expedientes;
  private final AuditoriaRepository auditorias;
  private final HistorialExpedienteRepository historial;
  private final PresentadorExpedientes presentador;
  private final ConsultaClientes clientes;
  private final ServicioCatalogos catalogos;
  private final ServicioAliados aliados;
  private final DirectorioUsuarios directorio;
  private final ProgramadorTareas programador;
  private final PropiedadesExpedientes propiedades;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioExpedientes(ExpedienteRepository expedientes, AuditoriaRepository auditorias,
      HistorialExpedienteRepository historial, PresentadorExpedientes presentador, ConsultaClientes clientes,
      ServicioCatalogos catalogos, ServicioAliados aliados, DirectorioUsuarios directorio,
      ProgramadorTareas programador, PropiedadesExpedientes propiedades, ActorActual actorActual, Bitacora bitacora,
      Clock reloj) {
    this.expedientes = expedientes;
    this.auditorias = auditorias;
    this.historial = historial;
    this.presentador = presentador;
    this.clientes = clientes;
    this.catalogos = catalogos;
    this.aliados = aliados;
    this.directorio = directorio;
    this.programador = programador;
    this.propiedades = propiedades;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public Page<ExpedienteResumen> listar(String texto, UUID clienteId, String esquema, EstadoExpediente estado,
      TipoServicio tipoServicio, UUID responsableId, int pagina, int tamano) {
    UUID organizacionId = actorActual.requerido().organizacionId();
    Specification<Expediente> filtro = (raiz, consulta, cb) -> {
      List<Predicate> c = new ArrayList<>();
      c.add(cb.equal(raiz.get("organizacionId"), organizacionId));
      if (texto != null && !texto.isBlank()) {
        String patron = "%" + texto.trim().toLowerCase() + "%";
        c.add(cb.or(cb.like(cb.lower(raiz.get("codigo")), patron), cb.like(cb.lower(raiz.get("numeroContrato")), patron),
            cb.like(cb.lower(raiz.get("codigoExterno")), patron)));
      }
      if (clienteId != null) c.add(cb.equal(raiz.get("clienteId"), clienteId));
      if (esquema != null && !esquema.isBlank()) c.add(cb.equal(raiz.get("esquemaCodigo"), esquema));
      if (estado != null) c.add(cb.equal(raiz.get("estado"), estado));
      if (tipoServicio != null) c.add(cb.equal(raiz.get("tipoServicio"), tipoServicio));
      if (responsableId != null) c.add(cb.equal(raiz.get("responsableUsuarioId"), responsableId));
      return cb.and(c.toArray(Predicate[]::new));
    };
    Page<Expediente> resultado = expedientes.findAll(filtro,
        PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadoEn")));
    return new PageImpl<>(presentador.resumir(organizacionId, resultado.getContent()), resultado.getPageable(),
        resultado.getTotalElements());
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public ExpedienteDetalle obtener(UUID id) { return presentador.detalle(cargar(id)); }

  @Transactional
  @PreAuthorize("hasAuthority('expedientes:gestionar')")
  public ExpedienteDetalle crear(SolicitudCrearExpediente s) {
    UsuarioAutenticado actor = actorActual.requerido();
    ClienteReferencia cliente = clientes.buscar(actor.organizacionId(), s.clienteId())
        .orElseThrow(() -> new ReglaNegocioException("CLIENTE_INVALIDO", "El cliente indicado no existe."));
    if (cliente.archivado()) {
      throw new ReglaNegocioException("CLIENTE_ARCHIVADO", "No se pueden abrir expedientes para un cliente archivado.");
    }
    EsquemaCatalogo esquema = catalogos.esquema(s.esquema())
        .orElseThrow(() -> new ReglaNegocioException("ESQUEMA_INVALIDO", "Esquema de acreditación desconocido: " + s.esquema()));
    UUID responsable = validarResponsable(actor, s.responsableId());
    boolean decideIndia = Boolean.FALSE.equals(esquema.decideIqcPeru());
    Instant ahora = Instant.now(reloj);
    Expediente expediente = Expediente.nuevo(actor.organizacionId(), cliente.id(), esquema.codigo(), s.tipoServicio(),
        FlujoExpediente.estadoInicial(decideIndia), responsable, actor.id(), ahora);
    expediente.definirNormas(normas(s.normas()));
    expediente.definirSedes(sedes(cliente.id(), s.sedes()));
    AliadoRespuesta aliado = aliado(actor.organizacionId(), s.aliadoId());
    expediente.actualizarDatos(limpio(s.alcance()), aliado == null ? null : aliado.id(),
        s.comisionPorcentaje() != null ? s.comisionPorcentaje() : aliado == null ? null : aliado.comisionPorcentaje(),
        limpio(s.numeroContrato()), limpio(s.codigoExterno()), responsable, limpio(s.notas()));
    expedientes.saveAndFlush(expediente);
    historial.save(new HistorialExpediente(expediente.getId(), null, expediente.getEstado(), "Expediente creado",
        actor.id(), ahora));
    bitacora.registrar(EntradaBitacora.accion("EXPEDIENTE_CREADO").entidad(ENTIDAD, expediente.getId())
        .estadoNuevo(instantanea(expediente)));
    return presentador.detalle(expediente);
  }

  @Transactional
  @PreAuthorize("hasAuthority('expedientes:gestionar')")
  public ExpedienteDetalle actualizar(UUID id, SolicitudActualizarExpediente s) {
    Expediente e = cargar(id);
    verificarVersion(e.getVersion(), s.version());
    e.exigirAbierto();
    UsuarioAutenticado actor = actorActual.requerido();
    Map<String, Object> antes = instantanea(e);
    if (s.normas() != null) e.definirNormas(normas(s.normas()));
    if (s.sedes() != null) e.definirSedes(sedes(e.getClienteId(), s.sedes()));
    UUID aliadoId = s.aliadoId() != null ? aliado(actor.organizacionId(), s.aliadoId()).id() : e.getAliadoId();
    UUID responsable = s.responsableId() != null ? validarResponsable(actor, s.responsableId()) : e.getResponsableUsuarioId();
    e.actualizarDatos(opcional(s.alcance(), e.getAlcance()), aliadoId,
        s.comisionPorcentaje() != null ? s.comisionPorcentaje() : e.getComisionPorcentaje(),
        opcional(s.numeroContrato(), e.getNumeroContrato()), opcional(s.codigoExterno(), e.getCodigoExterno()),
        responsable, opcional(s.notas(), e.getNotas()));
    expedientes.flush();
    bitacora.registrar(EntradaBitacora.accion("EXPEDIENTE_ACTUALIZADO").entidad(ENTIDAD, e.getId())
        .estadoAnterior(antes).estadoNuevo(instantanea(e)));
    return presentador.detalle(e);
  }

  @Transactional
  @PreAuthorize("hasAuthority('expedientes:gestionar')")
  public ExpedienteDetalle cambiarEstado(UUID id, SolicitudTransicion s) {
    Expediente e = cargar(id);
    verificarVersion(e.getVersion(), s.version());
    if (s.estado() == EstadoExpediente.CANCELADO && (s.motivo() == null || s.motivo().isBlank())) {
      throw new ReglaNegocioException("MOTIVO_REQUERIDO", "Indica el motivo de la cancelación.");
    }
    EstadoExpediente anterior = e.getEstado();
    Instant ahora = Instant.now(reloj);
    e.cambiarEstado(s.estado(), presentador.decideIndia(e.getEsquemaCodigo()), ahora);
    expedientes.flush();
    historial.save(new HistorialExpediente(e.getId(), anterior, s.estado(), limpio(s.motivo()),
        actorActual.requerido().id(), ahora));
    bitacora.registrar(EntradaBitacora.accion("EXPEDIENTE_ESTADO_CAMBIADO").entidad(ENTIDAD, e.getId())
        .estadoAnterior(Map.of("estado", anterior.name())).estadoNuevo(Map.of("estado", s.estado().name()))
        .motivo(limpio(s.motivo())));
    return presentador.detalle(e);
  }

  /**
   *registra la fecha efectiva de otorgamiento, calcula el ciclo y crea las vigilancias por programar con
   *sus tareas de seguimiento ("Programar vigilancia N" y "Preparar recertificación") con anticipación
   */
  @Transactional
  @PreAuthorize("hasAuthority('expedientes:gestionar')")
  public ExpedienteDetalle registrarCertificacion(UUID id, SolicitudCertificacion s) {
    Expediente e = cargar(id);
    verificarVersion(e.getVersion(), s.version());
    if (s.fechaCertificacion().isAfter(LocalDate.now(reloj))) {
      throw new ReglaNegocioException("FECHA_FUTURA", "La fecha de certificación no puede ser futura.");
    }
    EsquemaCatalogo esquema = catalogos.esquema(e.getEsquemaCodigo()).orElseThrow();
    CicloCertificacion ciclo = CicloCertificacion.calcular(s.fechaCertificacion(), esquema.aniosCiclo(),
        Math.min(esquema.vigilanciasPorCiclo(), 2));
    e.registrarCertificacion(ciclo);
    for (int i = 0; i < ciclo.limitesVigilancia().size(); i++) {
      TipoAuditoria tipo = CicloCertificacion.tipoVigilancia(i + 1);
      LocalDate limite = ciclo.limitesVigilancia().get(i);
      if (!auditorias.existsByExpedienteIdAndTipoAndEstadoNot(e.getId(), tipo, EstadoAuditoria.CANCELADA)) {
        auditorias.save(new Auditoria(e.getId(), tipo, limite));
      }
      programador.crearTareaAutomatica(new NuevaTareaAutomatica(e.getOrganizacionId(),
          "Programar " + (i + 1) + "ª vigilancia de " + e.getCodigo(),
          "La " + (i + 1) + "ª vigilancia debe realizarse antes del " + limite + ".",
          "PROGRAMAR_AUDITORIA", ENTIDAD, e.getId(), e.getClienteId(), e.getResponsableUsuarioId(),
          limite.minusDays(propiedades.diasAnticipacionVigilancia()),
          "expediente:" + e.getId() + ":vigilancia:" + (i + 1)));
    }
    programador.crearTareaAutomatica(new NuevaTareaAutomatica(e.getOrganizacionId(),
        "Preparar recertificación de " + e.getCodigo(),
        "La certificación expira el " + ciclo.expiracion() + ": la renovación debe completarse antes.",
        "PROGRAMAR_AUDITORIA", ENTIDAD, e.getId(), e.getClienteId(), e.getResponsableUsuarioId(),
        ciclo.expiracion().minusDays(propiedades.diasAnticipacionRecertificacion()),
        "expediente:" + e.getId() + ":recertificacion"));
    expedientes.flush();
    bitacora.registrar(EntradaBitacora.accion("CERTIFICACION_REGISTRADA").entidad(ENTIDAD, e.getId())
        .estadoNuevo(Map.of("fechaCertificacion", ciclo.fechaCertificacion().toString(),
            "fechaExpiracion", ciclo.expiracion().toString(),
            "vigilancias", ciclo.limitesVigilancia().stream().map(LocalDate::toString).toList())));
    return presentador.detalle(e);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public ResumenExpedientes resumen() {
    UUID organizacionId = actorActual.requerido().organizacionId();
    Map<EstadoExpediente, Long> porEstado = new EnumMap<>(EstadoExpediente.class);
    for (Object[] fila : expedientes.contarPorEstado(organizacionId)) {
      porEstado.put((EstadoExpediente) fila[0], (Long) fila[1]);
    }
    LocalDate hoy = LocalDate.now(reloj);
    Set<TipoAuditoria> vigilancias = Set.of(TipoAuditoria.VIGILANCIA_1, TipoAuditoria.VIGILANCIA_2);
    Set<EstadoAuditoria> pendientes = Set.of(EstadoAuditoria.POR_PROGRAMAR, EstadoAuditoria.PROGRAMADA);
    return new ResumenExpedientes(porEstado,
        auditorias.pendientesEntre(organizacionId, vigilancias, pendientes, LocalDate.of(1900, 1, 1), hoy.minusDays(1)).size(),
        auditorias.pendientesEntre(organizacionId, vigilancias, pendientes, hoy, hoy.plusDays(30)).size(),
        auditorias.sinEquipo(organizacionId, pendientes).size());
  }

  public Expediente cargar(UUID id) {
    return expedientes.findByIdAndOrganizacionId(id, actorActual.requerido().organizacionId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Expediente", id));
  }

  private List<NormaExpediente> normas(List<NormaSolicitada> solicitadas) {
    Set<String> vistas = new HashSet<>();
    List<NormaExpediente> resultado = new ArrayList<>();
    for (NormaSolicitada n : solicitadas) {
      NormaEdicion edicion = catalogos.buscarEdicion(n.norma(), n.edicion())
          .orElseThrow(() -> new ReglaNegocioException("NORMA_INVALIDA",
              "La norma " + n.norma() + ":" + n.edicion() + " no existe en el catálogo."));
      if (!edicion.vigente()) {
        throw new ReglaNegocioException("EDICION_NO_VIGENTE", edicion.designacion() + " no está vigente para nuevas auditorías.");
      }
      if (!vistas.add(n.norma())) {
        throw new ReglaNegocioException("NORMA_REPETIDA", "La norma " + edicion.nombreCorto() + " está repetida.");
      }
      resultado.add(new NormaExpediente(n.norma(), n.edicion()));
    }
    return resultado;
  }

  private Set<UUID> sedes(UUID clienteId, Set<UUID> solicitadas) {
    if (solicitadas == null || solicitadas.isEmpty()) return Set.of();
    Set<UUID> delCliente = clientes.direccionesDeCliente(clienteId).keySet();
    if (!delCliente.containsAll(solicitadas)) {
      throw new ReglaNegocioException("SEDE_INVALIDA", "Las sedes deben ser direcciones registradas del cliente.");
    }
    return solicitadas;
  }

  private AliadoRespuesta aliado(UUID organizacionId, UUID aliadoId) {
    if (aliadoId == null) return null;
    return aliados.buscar(organizacionId, aliadoId)
        .orElseThrow(() -> new ReglaNegocioException("ALIADO_INVALIDO", "El aliado comercial indicado no existe."));
  }

  private UUID validarResponsable(UsuarioAutenticado actor, UUID solicitado) {
    if (solicitado == null || solicitado.equals(actor.id())) return actor.id();
    UsuarioResumen usuario = directorio.buscarActivo(actor.organizacionId(), solicitado)
        .orElseThrow(() -> new ReglaNegocioException("RESPONSABLE_INVALIDO", "El responsable no existe o está deshabilitado."));
    if (!usuario.tienePermiso(GESTIONAR)) {
      throw new ReglaNegocioException("RESPONSABLE_SIN_PERMISO", usuario.nombre() + " no puede gestionar expedientes.");
    }
    return usuario.id();
  }

  static void verificarVersion(long actual, Long enviada) {
    if (!Objects.equals(actual, enviada)) {
      throw new ConflictoException("CONFLICTO_VERSION",
          "El registro fue modificado por otra persona. Recarga los datos y vuelve a intentarlo.");
    }
  }

  private static Map<String, Object> instantanea(Expediente e) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("codigo", e.getCodigo());
    datos.put("clienteId", e.getClienteId());
    datos.put("esquema", e.getEsquemaCodigo());
    datos.put("tipoServicio", e.getTipoServicio().name());
    datos.put("estado", e.getEstado().name());
    datos.put("normas", e.getNormas().stream().map(n -> n.normaCodigo() + ":" + n.edicion()).sorted().toList());
    datos.put("sedes", e.getSedes().stream().map(UUID::toString).sorted().toList());
    datos.put("aliadoId", e.getAliadoId());
    datos.put("comisionPorcentaje", e.getComisionPorcentaje() == null ? null : e.getComisionPorcentaje().toPlainString());
    datos.put("numeroContrato", e.getNumeroContrato());
    datos.put("codigoExterno", e.getCodigoExterno());
    datos.put("responsableId", e.getResponsableUsuarioId());
    return datos;
  }

  static String limpio(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }

  static String opcional(String nuevo, String actual) { return nuevo == null ? actual : limpio(nuevo); }
}
