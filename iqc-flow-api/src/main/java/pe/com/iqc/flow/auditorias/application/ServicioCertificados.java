package pe.com.iqc.flow.auditorias.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.CertificadoRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.CondicionesEntrega;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudCertificadoExterno;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudCondicionesEntrega;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudCorregirCertificado;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudEntrega;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudFirma;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudRevision;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudTraduccion;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.Referencia;
import pe.com.iqc.flow.auditorias.domain.Certificado;
import pe.com.iqc.flow.auditorias.domain.Certificado.Estado;
import pe.com.iqc.flow.auditorias.domain.Certificado.TipoFirma;
import pe.com.iqc.flow.auditorias.domain.CodigoCertificado;
import pe.com.iqc.flow.auditorias.domain.DecisionCertificacion;
import pe.com.iqc.flow.auditorias.domain.EstadoExpediente;
import pe.com.iqc.flow.auditorias.domain.Expediente;
import pe.com.iqc.flow.auditorias.domain.HistorialExpediente;
import pe.com.iqc.flow.auditorias.domain.NormaExpediente;
import pe.com.iqc.flow.auditorias.domain.TipoDecision;
import pe.com.iqc.flow.auditorias.infrastructure.CertificadoRepository;
import pe.com.iqc.flow.auditorias.infrastructure.DecisionCertificacionRepository;
import pe.com.iqc.flow.auditorias.infrastructure.ExpedienteRepository;
import pe.com.iqc.flow.auditorias.infrastructure.HistorialExpedienteRepository;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaEdicion;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;
import pe.com.iqc.flow.organizaciones.ServicioOrganizaciones;

/**
 *certificados: borradores con código generado tras una decisión favorable, checklist de emisión, firma según la
 *política de la organización y emisión; registro de los certificados de India; condiciones de entrega y entrega
 */
@Service
public class ServicioCertificados {
  static final String ENTIDAD = "CERTIFICADO";
  private static final Set<Estado> EN_PREPARACION = EnumSet.of(Estado.BORRADOR, Estado.REVISADO);
  private static final Set<Estado> CON_HISTORIA = EnumSet.of(Estado.EMITIDO, Estado.SUSPENDIDO, Estado.REEMPLAZADO);
  private static final Set<EstadoExpediente> RECIBIDO_DE_INDIA =
      EnumSet.of(EstadoExpediente.CERTIFICADO_RECIBIDO, EstadoExpediente.TRADUCIDO);

  private final CertificadoRepository certificados;
  private final DecisionCertificacionRepository decisiones;
  private final ExpedienteRepository expedientesRepo;
  private final HistorialExpedienteRepository historial;
  private final ServicioExpedientes expedientes;
  private final PresentadorExpedientes presentador;
  private final ServicioCatalogos catalogos;
  private final ServicioDocumentos documentos;
  private final ServicioOrganizaciones organizaciones;
  private final ParametrosOrganizacion parametros;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioCertificados(CertificadoRepository certificados, DecisionCertificacionRepository decisiones,
      ExpedienteRepository expedientesRepo, HistorialExpedienteRepository historial, ServicioExpedientes expedientes,
      PresentadorExpedientes presentador, ServicioCatalogos catalogos, ServicioDocumentos documentos,
      ServicioOrganizaciones organizaciones, ParametrosOrganizacion parametros, ActorActual actorActual,
      Bitacora bitacora, Clock reloj) {
    this.certificados = certificados;
    this.decisiones = decisiones;
    this.expedientesRepo = expedientesRepo;
    this.historial = historial;
    this.expedientes = expedientes;
    this.presentador = presentador;
    this.catalogos = catalogos;
    this.documentos = documentos;
    this.organizaciones = organizaciones;
    this.parametros = parametros;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public List<CertificadoRespuesta> listar(UUID expedienteId) {
    Expediente e = expedientes.cargar(expedienteId);
    return respuestas(e.getOrganizacionId(), certificados.findByExpedienteIdOrderByNormaCodigoAscCreadoEnAsc(e.getId()));
  }

  /**
   *genera los borradores de una decisión favorable (otorgamiento, renovación, ampliación, transferencia o
   *reducción). La revisión del alcance y el ciclo continúan los del último certificado del cliente para esa norma
   */
  List<Certificado> generarBorradores(Expediente e, DecisionCertificacion decision, Collection<NormaExpediente> normas) {
    String pais = organizaciones.obtener(e.getOrganizacionId()).codigoPais();
    List<Certificado> creados = new ArrayList<>();
    for (NormaExpediente norma : normas) {
      Certificado anterior = decision.getTipo() == TipoDecision.REDUCCION
          ? vigente(e, norma.normaCodigo())
          : certificados.findFirstByClienteIdAndEsquemaCodigoAndNormaCodigoAndEstadoInOrderByCicloDescRevisionAlcanceDescCreadoEnDesc(
              e.getClienteId(), e.getEsquemaCodigo(), norma.normaCodigo(), CON_HISTORIA).orElse(null);
      int revision;
      int ciclo;
      int anio = decision.getFechaDecision().getYear();
      switch (decision.getTipo()) {
        case RENOVACION -> {
          // Como en los certificados reales: se conserva el año del otorgamiento original y sube el ciclo
          revision = anterior == null ? 1 : anterior.getRevisionAlcance();
          ciclo = anterior == null ? 2 : Math.min(anterior.getCiclo() + 1, 9);
          if (anterior != null && anterior.getAnioDecision() != null) anio = anterior.getAnioDecision();
        }
        case AMPLIACION, REDUCCION -> {
          revision = anterior == null ? 1 : Math.min(anterior.getRevisionAlcance() + 1, 9);
          ciclo = anterior == null ? 1 : anterior.getCiclo();
          if (anterior != null && anterior.getAnioDecision() != null) anio = anterior.getAnioDecision();
        }
        default -> {
          revision = 1;
          ciclo = 1;
          anterior = null;
        }
      }
      String codigo = codigo(e, pais, norma, anio, revision, ciclo);
      exigirCodigoLibre(e.getOrganizacionId(), codigo, norma.normaCodigo(), null);
      String alcance = decision.getTipo() == TipoDecision.REDUCCION ? decision.getNuevoAlcance() : e.getAlcance();
      creados.add(certificados.save(Certificado.borrador(e, norma, codigo, anio, revision, ciclo, alcance,
          decision.getId(), anterior)));
    }
    certificados.flush();
    return creados;
  }

  /**certificado vigente (emitido o suspendido) de esa norma en el expediente*/
  Certificado vigente(Expediente e, String norma) {
    return certificados.findByExpedienteIdOrderByNormaCodigoAscCreadoEnAsc(e.getId()).stream()
        .filter(c -> c.getNormaCodigo().equals(norma) && Certificado.VIGENTES.contains(c.getEstado()))
        .findFirst()
        .orElseThrow(() -> new ReglaNegocioException("SIN_CERTIFICADO_VIGENTE",
            "El expediente no tiene un certificado vigente de " + norma + "."));
  }

  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoRespuesta corregir(UUID expedienteId, UUID certificadoId, SolicitudCorregirCertificado s) {
    Expediente e = expedientes.cargar(expedienteId);
    Certificado c = cargar(e, certificadoId);
    ServicioExpedientes.verificarVersion(c.getVersion(), s.version());
    int revision = s.revisionAlcance() != null ? s.revisionAlcance() : c.getRevisionAlcance();
    int ciclo = s.ciclo() != null ? s.ciclo() : c.getCiclo();
    String pais = organizaciones.obtener(e.getOrganizacionId()).codigoPais();
    String codigo = codigo(e, pais, new NormaExpediente(c.getNormaCodigo(), c.getEdicion()), c.getAnioDecision(),
        revision, ciclo);
    exigirCodigoLibre(e.getOrganizacionId(), codigo, c.getNormaCodigo(), c.getId());
    String anterior = c.getCodigo();
    c.corregirBorrador(revision, ciclo, ServicioExpedientes.opcional(s.alcance(), c.getAlcance()), codigo);
    certificados.flush();
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_CORREGIDO").entidad(ENTIDAD, c.getId())
        .estadoAnterior(Map.of("codigo", anterior)).estadoNuevo(Map.of("codigo", codigo)));
    return respuesta(e.getOrganizacionId(), c);
  }

  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoRespuesta revisar(UUID expedienteId, UUID certificadoId, SolicitudRevision s) {
    Expediente e = expedientes.cargar(expedienteId);
    Certificado c = cargar(e, certificadoId);
    ServicioExpedientes.verificarVersion(c.getVersion(), s.version());
    if (!Boolean.TRUE.equals(s.checklistConforme())) {
      throw new ReglaNegocioException("CHECKLIST_NO_CONFORME",
          "Corrige el borrador hasta que el checklist de emisión (DDC-MAN-001) esté conforme.");
    }
    c.revisar(actorActual.requerido().id(), Instant.now(reloj));
    certificados.flush();
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_REVISADO").entidad(ENTIDAD, c.getId())
        .estadoNuevo(Map.of("codigo", c.getCodigo(), "estado", c.getEstado().name())));
    return respuesta(e.getOrganizacionId(), c);
  }

  /**
   *firma como Gerencia o Jefe Técnico. Con las firmas que exige la política de la organización el certificado
   *se emite; cuando se emite el último del expediente, este pasa a EMITIDO
   */
  @Transactional
  @PreAuthorize("hasAnyAuthority('certificados:firmar-gerencia', 'certificados:firmar-jefe-tecnico')")
  public CertificadoRespuesta firmar(UUID expedienteId, UUID certificadoId, SolicitudFirma s) {
    UsuarioAutenticado actor = actorActual.requerido();
    String permiso = s.tipo() == TipoFirma.GERENCIA ? "certificados:firmar-gerencia" : "certificados:firmar-jefe-tecnico";
    if (!actorActual.tienePermiso(permiso)) {
      throw new AccessDeniedException("Firmar como " + s.tipo() + " requiere el permiso " + permiso);
    }
    Expediente e = expedientes.cargar(expedienteId);
    Certificado c = cargar(e, certificadoId);
    ServicioExpedientes.verificarVersion(c.getVersion(), s.version());
    Instant ahora = Instant.now(reloj);
    c.firmar(s.tipo(), actor.id(), ahora);
    String politica = parametros.valor(e.getOrganizacionId(), ParametroOrganizacion.POLITICA_FIRMA_CERTIFICADOS);
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_FIRMADO").entidad(ENTIDAD, c.getId())
        .estadoNuevo(Map.of("codigo", c.getCodigo(), "firma", s.tipo().name(), "politica", politica)));
    if (c.firmasCompletas(politica)) emitir(e, c, ahora);
    certificados.flush();
    return respuesta(e.getOrganizacionId(), c);
  }

  private void emitir(Expediente e, Certificado c, Instant ahora) {
    LocalDate hoy = LocalDate.now(reloj);
    DecisionCertificacion decision = c.getDecisionId() == null ? null : decisiones.findById(c.getDecisionId()).orElse(null);
    if (decision != null && hoy.isBefore(decision.getFechaDecision())) {
      throw new ReglaNegocioException("EMISION_ANTES_DE_DECISION",
          "La fecha de emisión no puede ser anterior a la de la decisión (" + decision.getFechaDecision() + ").");
    }
    c.emitir(hoy, e.getFechaCertificacion(), e.getFechaExpiracion());
    sustituirAnteriores(c);
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_EMITIDO").entidad(ENTIDAD, c.getId())
        .estadoNuevo(Map.of("codigo", c.getCodigo(), "versionDocumento", c.getVersionDocumento(),
            "fechaExpiracion", c.getFechaExpiracion().toString())));
    if (e.getEstado() == EstadoExpediente.EN_EMISION) {
      certificados.flush();
      boolean quedanPorEmitir = certificados.findByExpedienteIdOrderByNormaCodigoAscCreadoEnAsc(e.getId()).stream()
          .anyMatch(otro -> EN_PREPARACION.contains(otro.getEstado()));
      if (!quedanPorEmitir) {
        e.avanzarPorSistema(EstadoExpediente.EMITIDO, false, ahora);
        expedientesRepo.flush();
        historial.save(new HistorialExpediente(e.getId(), EstadoExpediente.EN_EMISION, EstadoExpediente.EMITIDO,
            "Certificados firmados y emitidos", actorActual.requerido().id(), ahora));
      }
    }
  }

  /**al emitirse, el certificado sustituye a los anteriores vigentes del cliente para la misma norma y esquema*/
  private void sustituirAnteriores(Certificado nuevo) {
    for (Certificado anterior : certificados.findByClienteIdAndEsquemaCodigoAndNormaCodigoAndEstadoIn(
        nuevo.getClienteId(), nuevo.getEsquemaCodigo(), nuevo.getNormaCodigo(), Certificado.VIGENTES)) {
      if (!anterior.getId().equals(nuevo.getId())) {
        anterior.reemplazar();
        bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_REEMPLAZADO").entidad(ENTIDAD, anterior.getId())
            .estadoNuevo(Map.of("reemplazadoPor", nuevo.getCodigo())));
      }
    }
  }

  /**IQC Global: registra el certificado emitido por India con su código*/
  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoRespuesta registrarExterno(UUID expedienteId, SolicitudCertificadoExterno s) {
    Expediente e = expedientes.cargar(expedienteId);
    if (!presentador.decideIndia(e.getEsquemaCodigo())) {
      throw new ReglaNegocioException("CERTIFICADO_EXTERNO_NO_APLICA",
          "En INACAL y SAE el certificado se genera con la decisión de IQC Perú.");
    }
    if (!RECIBIDO_DE_INDIA.contains(e.getEstado())) {
      throw new ReglaNegocioException("CERTIFICADO_NO_RECIBIDO",
          "Registra el certificado cuando el expediente esté en CERTIFICADO_RECIBIDO o TRADUCIDO.");
    }
    NormaExpediente norma = e.getNormas().stream().filter(n -> n.normaCodigo().equals(s.norma())).findFirst()
        .orElseThrow(() -> new ReglaNegocioException("NORMA_INVALIDA", "La norma " + s.norma() + " no está en el expediente."));
    boolean yaRegistrado = certificados.findByExpedienteIdOrderByNormaCodigoAscCreadoEnAsc(e.getId()).stream()
        .anyMatch(c -> c.getNormaCodigo().equals(s.norma()) && Certificado.VIGENTES.contains(c.getEstado()));
    if (yaRegistrado) {
      throw new ConflictoException("CERTIFICADO_DUPLICADO", "El expediente ya tiene un certificado de " + s.norma() + ".");
    }
    String codigo = s.codigo().trim().toUpperCase();
    exigirCodigoLibre(e.getOrganizacionId(), codigo, s.norma(), null);
    UUID traduccion = s.traduccionDocumentoId() == null ? null : documentos.exigir(e.getOrganizacionId(),
        s.traduccionDocumentoId(), CategoriaDocumento.TRADUCCION_CERTIFICADO).id();
    Certificado c = Certificado.externo(e, norma, codigo, s.fechaEmision(),
        s.fechaOtorgamiento() != null ? s.fechaOtorgamiento() : e.getFechaCertificacion(),
        s.fechaExpiracion() != null ? s.fechaExpiracion() : e.getFechaExpiracion(), traduccion);
    certificados.saveAndFlush(c);
    sustituirAnteriores(c);
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_EXTERNO_REGISTRADO").entidad(ENTIDAD, c.getId())
        .estadoNuevo(Map.of("codigo", codigo, "norma", s.norma())));
    return respuesta(e.getOrganizacionId(), c);
  }

  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoRespuesta adjuntarTraduccion(UUID expedienteId, UUID certificadoId, SolicitudTraduccion s) {
    Expediente e = expedientes.cargar(expedienteId);
    Certificado c = cargar(e, certificadoId);
    ServicioExpedientes.verificarVersion(c.getVersion(), s.version());
    c.adjuntarTraduccion(documentos.exigir(e.getOrganizacionId(), s.documentoId(),
        CategoriaDocumento.TRADUCCION_CERTIFICADO).id());
    certificados.flush();
    bitacora.registrar(EntradaBitacora.accion("TRADUCCION_ADJUNTADA").entidad(ENTIDAD, c.getId())
        .estadoNuevo(Map.of("documentoId", s.documentoId().toString())));
    return respuesta(e.getOrganizacionId(), c);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public CondicionesEntrega condiciones(UUID expedienteId) { return condiciones(expedientes.cargar(expedienteId)); }

  /**conformidad de pagos y encuesta: solo quien gestiona entregas (Administración) puede liberarlas*/
  @Transactional
  @PreAuthorize("hasAuthority('entregas:gestionar')")
  public CondicionesEntrega definirCondiciones(UUID expedienteId, SolicitudCondicionesEntrega s) {
    Expediente e = expedientes.cargar(expedienteId);
    ServicioExpedientes.verificarVersion(e.getVersion(), s.version());
    Map<String, Object> antes = Map.of("pagosConformes", e.isPagosConformes(), "encuestaAplicada", e.isEncuestaAplicada());
    e.definirCondicionesEntrega(s.pagosConformes(), s.encuestaAplicada(), actorActual.requerido().id(), Instant.now(reloj));
    expedientesRepo.flush();
    bitacora.registrar(EntradaBitacora.accion("CONDICIONES_ENTREGA_ACTUALIZADAS").entidad(ServicioExpedientes.ENTIDAD, e.getId())
        .estadoAnterior(antes)
        .estadoNuevo(Map.of("pagosConformes", s.pagosConformes(), "encuestaAplicada", s.encuestaAplicada())));
    return condiciones(e);
  }

  /**registra la entrega de todos los certificados del expediente; exige pagos conformes y la encuesta aplicada*/
  @Transactional
  @PreAuthorize("hasAuthority('entregas:gestionar')")
  public List<CertificadoRespuesta> entregar(UUID expedienteId, SolicitudEntrega s) {
    Expediente e = expedientes.cargar(expedienteId);
    ServicioExpedientes.verificarVersion(e.getVersion(), s.version());
    CondicionesEntrega condiciones = condiciones(e);
    if (!condiciones.puedeEntregarse()) {
      throw new ReglaNegocioException("ENTREGA_BLOQUEADA", "No se puede entregar: " + String.join("; ", condiciones.pendientes()) + ".");
    }
    Instant ahora = Instant.now(reloj);
    List<Certificado> lista = certificados.findByExpedienteIdOrderByNormaCodigoAscCreadoEnAsc(e.getId()).stream()
        .filter(c -> Certificado.VIGENTES.contains(c.getEstado())).toList();
    lista.forEach(c -> c.entregar(s.medio(), ahora));
    EstadoExpediente anterior = e.getEstado();
    e.avanzarPorSistema(EstadoExpediente.ENTREGADO, presentador.decideIndia(e.getEsquemaCodigo()), ahora);
    expedientesRepo.flush();
    historial.save(new HistorialExpediente(e.getId(), anterior, EstadoExpediente.ENTREGADO,
        "Entrega " + s.medio().name().toLowerCase(), actorActual.requerido().id(), ahora));
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADOS_ENTREGADOS").entidad(ServicioExpedientes.ENTIDAD, e.getId())
        .estadoNuevo(Map.of("medio", s.medio().name(), "certificados", lista.stream().map(Certificado::getCodigo).toList())));
    return respuestas(e.getOrganizacionId(), lista);
  }

  private CondicionesEntrega condiciones(Expediente e) {
    List<String> pendientes = new ArrayList<>();
    if (!e.isPagosConformes()) pendientes.add("los pagos y documentos administrativos no están conformes");
    if (!e.isEncuestaAplicada()) pendientes.add("falta aplicar la encuesta de satisfacción");
    boolean decideIndia = presentador.decideIndia(e.getEsquemaCodigo());
    EstadoExpediente listo = decideIndia ? EstadoExpediente.TRADUCIDO : EstadoExpediente.EMITIDO;
    if (e.getEstado() != listo) pendientes.add("el expediente debe estar en " + listo + " (está en " + e.getEstado() + ")");
    Set<String> conCertificado = new HashSet<>();
    certificados.findByExpedienteIdOrderByNormaCodigoAscCreadoEnAsc(e.getId()).stream()
        .filter(c -> Certificado.VIGENTES.contains(c.getEstado())).forEach(c -> conCertificado.add(c.getNormaCodigo()));
    List<String> sinCertificado = e.getNormas().stream().map(NormaExpediente::normaCodigo)
        .filter(n -> !conCertificado.contains(n)).sorted().toList();
    if (!sinCertificado.isEmpty()) pendientes.add("faltan certificados emitidos de " + String.join(", ", sinCertificado));
    Referencia por = e.getCondicionesPor() == null ? null
        : presentador.personas(e.getOrganizacionId(), Set.of(e.getCondicionesPor())).get(e.getCondicionesPor());
    return new CondicionesEntrega(e.isPagosConformes(), e.isEncuestaAplicada(), por, e.getCondicionesEn(),
        pendientes.isEmpty(), pendientes);
  }

  private String codigo(Expediente e, String pais, NormaExpediente norma, Integer anio, int revision, int ciclo) {
    NormaEdicion edicion = catalogos.buscarEdicion(norma.normaCodigo(), norma.edicion())
        .orElseThrow(() -> new ReglaNegocioException("NORMA_INVALIDA", "La norma " + norma.normaCodigo() + " no está en el catálogo."));
    if (anio == null) throw new ReglaNegocioException("ANIO_DECISION_REQUERIDO", "El certificado no tiene año de decisión.");
    return CodigoCertificado.generar(pais, e.getEsquemaCodigo(), e.getNumeroContrato(), anio, edicion.codigoSistema(),
        revision, ciclo, edicion.sufijoCodigo());
  }

  private void exigirCodigoLibre(UUID organizacionId, String codigo, String norma, UUID excluido) {
    if (certificados.existeCodigoActivo(organizacionId, codigo, norma, excluido == null ? new UUID(0, 0) : excluido)) {
      throw new ConflictoException("CODIGO_DUPLICADO",
          "Ya existe un certificado activo de " + norma + " con el código " + codigo + ". Revisa el contrato, la revisión o el ciclo.");
    }
  }

  private Certificado cargar(Expediente e, UUID certificadoId) {
    return certificados.findByIdAndExpedienteId(certificadoId, e.getId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Certificado", certificadoId));
  }

  CertificadoRespuesta respuesta(UUID organizacionId, Certificado c) { return respuestas(organizacionId, List.of(c)).get(0); }

  List<CertificadoRespuesta> respuestas(UUID organizacionId, List<Certificado> lista) {
    Set<UUID> usuarios = new HashSet<>();
    lista.forEach(c -> {
      usuarios.add(c.getRevisadoPor());
      usuarios.add(c.getFirmaGerenciaPor());
      usuarios.add(c.getFirmaJefeTecnicoPor());
    });
    usuarios.remove(null);
    Map<UUID, Referencia> personas = presentador.personas(organizacionId, usuarios);
    String politica = parametros.valor(organizacionId, ParametroOrganizacion.POLITICA_FIRMA_CERTIFICADOS);
    Map<String, String> designaciones = new LinkedHashMap<>();
    return lista.stream().map(c -> new CertificadoRespuesta(c.getId(), c.getCodigo(), c.getNormaCodigo(),
        designaciones.computeIfAbsent(c.getNormaCodigo() + ":" + c.getEdicion(), k -> catalogos
            .buscarEdicion(c.getNormaCodigo(), c.getEdicion()).map(NormaEdicion::designacion).orElse(k)),
        c.getEsquemaCodigo(), c.isEmitidoPorIndia(), c.getRevisionAlcance(), c.getCiclo(), c.getAlcance(), c.getEstado(),
        c.getDecisionId(), c.getReemplazaId(), referencia(personas, c.getRevisadoPor()),
        referencia(personas, c.getFirmaGerenciaPor()), referencia(personas, c.getFirmaJefeTecnicoPor()),
        firmasPendientes(c, politica), c.getFechaOtorgamiento(), c.getFechaEmision(), c.getFechaExpiracion(),
        c.getVersionDocumento(), c.getTokenVerificacion(), c.getTraduccionDocumentoId(), c.getEntregadoEn(),
        c.getMedioEntrega(), c.getVersion())).toList();
  }

  private static List<TipoFirma> firmasPendientes(Certificado c, String politica) {
    if (!EN_PREPARACION.contains(c.getEstado())) return List.of();
    List<TipoFirma> pendientes = new ArrayList<>();
    if (c.getFirmaGerenciaPor() == null) pendientes.add(TipoFirma.GERENCIA);
    if ("FIRMA_GERENCIA_Y_JEFE_TECNICO".equals(politica) && c.getFirmaJefeTecnicoPor() == null) {
      pendientes.add(TipoFirma.JEFE_TECNICO);
    }
    return pendientes;
  }

  private static Referencia referencia(Map<UUID, Referencia> personas, UUID id) {
    return id == null ? null : Objects.requireNonNullElse(personas.get(id), new Referencia(id, null));
  }
}
