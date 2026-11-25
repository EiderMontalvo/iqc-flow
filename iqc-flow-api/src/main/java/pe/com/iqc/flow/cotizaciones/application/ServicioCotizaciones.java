package pe.com.iqc.flow.cotizaciones.application;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaCatalogo;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.PaisCatalogo;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.infrastructure.Correlativos;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.ClienteCotizacion;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.CotizacionDetalle;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.CotizacionResumen;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.ItemRespuesta;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.Referencia;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.ResumenCotizaciones;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.SolicitudAnulacion;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.SolicitudCotizacion;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.SolicitudEnvio;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.SolicitudRespuesta;
import pe.com.iqc.flow.cotizaciones.domain.Cotizacion;
import pe.com.iqc.flow.cotizaciones.domain.EstadoCotizacion;
import pe.com.iqc.flow.cotizaciones.domain.ItemCotizacion;
import pe.com.iqc.flow.cotizaciones.infrastructure.CotizacionRepository;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas;

/**
 *cotizaciones: borrador → enviada → aceptada o rechazada (o anulada). Numeración correlativa COT-AAAA-0001, importes
 *con el impuesto del país del cliente y tarea automática si una enviada no tiene respuesta en N días
 */
@Service
public class ServicioCotizaciones {
  static final String ENTIDAD = "COTIZACION";
  /**clave de la tarea automática de una cotización enviada sin respuesta*/
  public static String claveRecordatorio(UUID cotizacionId) { return "cotizacion-sin-respuesta:" + cotizacionId; }

  private final CotizacionRepository cotizaciones;
  private final ConsultaClientes clientes;
  private final ServicioCatalogos catalogos;
  private final DirectorioUsuarios usuarios;
  private final ParametrosOrganizacion parametros;
  private final ProgramadorTareas tareas;
  private final Correlativos correlativos;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final JdbcTemplate jdbc;
  private final Clock reloj;

  public ServicioCotizaciones(CotizacionRepository cotizaciones, ConsultaClientes clientes, ServicioCatalogos catalogos,
      DirectorioUsuarios usuarios, ParametrosOrganizacion parametros, ProgramadorTareas tareas, Correlativos correlativos,
      ActorActual actorActual, Bitacora bitacora, JdbcTemplate jdbc, Clock reloj) {
    this.cotizaciones = cotizaciones;
    this.clientes = clientes;
    this.catalogos = catalogos;
    this.usuarios = usuarios;
    this.parametros = parametros;
    this.tareas = tareas;
    this.correlativos = correlativos;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.jdbc = jdbc;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cotizaciones:leer')")
  public Page<CotizacionResumen> listar(EstadoCotizacion estado, boolean sinRespuesta, String texto, UUID clienteId, int pagina, int tamano) {
    UUID org = actorActual.requerido().organizacionId();
    Set<UUID> porTexto = texto == null || texto.isBlank() ? null : clientes.idsPorTexto(org, texto.trim());
    String numero = texto == null ? null : texto.trim().toUpperCase(Locale.ROOT);
    Specification<Cotizacion> filtro = (raiz, consulta, cb) -> {
      List<Predicate> p = new ArrayList<>();
      p.add(cb.equal(raiz.get("organizacionId"), org));
      if (estado != null) p.add(cb.equal(raiz.get("estado"), estado));
      if (sinRespuesta) p.add(cb.equal(raiz.get("estado"), EstadoCotizacion.ENVIADA));
      if (clienteId != null) p.add(cb.equal(raiz.get("clienteId"), clienteId));
      if (porTexto != null) {
        Predicate porNumero = cb.like(raiz.get("numero"), "%" + numero + "%");
        p.add(porTexto.isEmpty() ? porNumero : cb.or(porNumero, raiz.get("clienteId").in(porTexto)));
      }
      return cb.and(p.toArray(Predicate[]::new));
    };
    Page<Cotizacion> page = cotizaciones.findAll(filtro, PageRequest.of(pagina, tamano,
        Sort.by(Sort.Order.desc("fechaEmision"), Sort.Order.desc("numero"))));
    Map<UUID, ClienteReferencia> cli = clientes.buscarPorIds(org, page.map(Cotizacion::getClienteId).toSet());
    Map<UUID, UsuarioResumen> per = usuarios.buscarPorIds(org, page.map(Cotizacion::getResponsableId).toSet());
    Map<String, String> normas = nombresNormas();
    return page.map(c -> new CotizacionResumen(c.getId(), c.getNumero(), cliente(cli.get(c.getClienteId()), c.getClienteId()),
        c.getTipoServicio(), c.getItems().stream().map(i -> i.getNormaCodigo() == null ? null : normas.getOrDefault(i.getNormaCodigo(), i.getNormaCodigo()))
            .filter(java.util.Objects::nonNull).distinct().toList(),
        c.getMoneda(), c.getTotal(), c.getEstado(), c.getFechaEmision(), c.getEnviadaEn(), c.getRespondidaEn(),
        diasSinRespuesta(c), referencia(per.get(c.getResponsableId()), c.getResponsableId()), c.getVersion()));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cotizaciones:leer')")
  public CotizacionDetalle obtener(UUID id) { return detalle(cargar(id)); }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cotizaciones:leer')")
  public ResumenCotizaciones resumen() {
    UUID org = actorActual.requerido().organizacionId();
    LocalDate hoy = LocalDate.now(reloj), inicioMes = hoy.withDayOfMonth(1), haceUnAnio = hoy.minusMonths(12);
    int dias = diasSinRespuesta(org);
    Long[] n = jdbc.queryForObject("""
        SELECT count(*) FILTER (WHERE estado = 'BORRADOR'),
               count(*) FILTER (WHERE estado = 'ENVIADA'),
               count(*) FILTER (WHERE estado = 'ENVIADA' AND enviada_en <= ?),
               count(*) FILTER (WHERE estado = 'ACEPTADA' AND respondida_en >= ?),
               count(*) FILTER (WHERE enviada_en >= ?),
               count(*) FILTER (WHERE estado = 'ACEPTADA' AND respondida_en >= ?),
               count(*) FILTER (WHERE estado = 'RECHAZADA' AND respondida_en >= ?)
        FROM cotizaciones WHERE organizacion_id = ?""",
        (rs, i) -> new Long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)},
        hoy.minusDays(dias), inicioMes, inicioMes, haceUnAnio, haceUnAnio, org);
    long respondidas = n[5] + n[6];
    return new ResumenCotizaciones(n[0], n[1], n[2], n[3], n[4],
        respondidas == 0 ? null : (int) Math.round(n[5] * 100.0 / respondidas), dias);
  }

  @Transactional
  @PreAuthorize("hasAuthority('cotizaciones:gestionar')")
  public CotizacionDetalle crear(SolicitudCotizacion s) {
    UsuarioAutenticado actor = actorActual.requerido();
    LocalDate hoy = LocalDate.now(reloj);
    String numero = correlativos.siguienteCodigo(actor.organizacionId(), "COT", hoy.getYear());
    Cotizacion c = new Cotizacion(actor.organizacionId(), numero, actor.id(), hoy);
    aplicar(c, s, actor);
    cotizaciones.saveAndFlush(c);
    bitacora.registrar(EntradaBitacora.accion("COTIZACION_CREADA").entidad(ENTIDAD, c.getId()).estadoNuevo(Map.of("numero", numero)));
    return detalle(c);
  }

  @Transactional
  @PreAuthorize("hasAuthority('cotizaciones:gestionar')")
  public CotizacionDetalle editar(UUID id, SolicitudCotizacion s) {
    Cotizacion c = cargar(id);
    version(c, s.version() == null ? -1 : s.version());
    aplicar(c, s, actorActual.requerido());
    cotizaciones.saveAndFlush(c);
    bitacora.registrar(EntradaBitacora.accion("COTIZACION_EDITADA").entidad(ENTIDAD, c.getId()));
    return detalle(c);
  }

  /**copia editable (nuevo número) de cualquier cotización: para corregir una enviada o cotizar de nuevo*/
  @Transactional
  @PreAuthorize("hasAuthority('cotizaciones:gestionar')")
  public CotizacionDetalle duplicar(UUID id) {
    Cotizacion o = cargar(id);
    UsuarioAutenticado actor = actorActual.requerido();
    LocalDate hoy = LocalDate.now(reloj);
    Cotizacion c = new Cotizacion(actor.organizacionId(), correlativos.siguienteCodigo(actor.organizacionId(), "COT", hoy.getYear()), actor.id(), hoy);
    c.actualizar(o.getClienteId(), o.getContactoNombre(), o.getContactoCorreo(), o.getEsquemaCodigo(), o.getTipoServicio(),
        o.getAlcance(), o.getNumeroSedes(), o.getSedesDetalle(), o.getMoneda(), o.getNombreImpuesto(), o.getTasaImpuesto(),
        o.getTarifaDia(), o.getValidezDias(), o.getCondicionesPago(), o.getNotas(), o.getResponsableId(),
        o.getItems().stream().map(i -> new ItemCotizacion(i.getOrden(), i.getNormaCodigo(), i.getEdicion(), i.getDescripcion(),
            i.getDiasAuditor(), i.getMonto())).toList());
    cotizaciones.saveAndFlush(c);
    bitacora.registrar(EntradaBitacora.accion("COTIZACION_DUPLICADA").entidad(ENTIDAD, c.getId()).estadoNuevo(Map.of("origen", o.getNumero())));
    return detalle(c);
  }

  @Transactional
  @PreAuthorize("hasAuthority('cotizaciones:gestionar')")
  public CotizacionDetalle enviar(UUID id, SolicitudEnvio s) {
    Cotizacion c = cargar(id);
    version(c, s.version());
    if (s.fecha().isAfter(LocalDate.now(reloj))) throw new ReglaNegocioException("FECHA_FUTURA", "La fecha de envío no puede ser futura.");
    c.enviar(s.fecha(), limpio(s.destinatarios()));
    cotizaciones.saveAndFlush(c);
    bitacora.registrar(EntradaBitacora.accion("COTIZACION_ENVIADA").entidad(ENTIDAD, c.getId())
        .estadoNuevo(Map.of("fecha", s.fecha().toString())));
    return detalle(c);
  }

  @Transactional
  @PreAuthorize("hasAuthority('cotizaciones:gestionar')")
  public CotizacionDetalle responder(UUID id, SolicitudRespuesta s) {
    Cotizacion c = cargar(id);
    version(c, s.version());
    if (s.fecha().isAfter(LocalDate.now(reloj))) throw new ReglaNegocioException("FECHA_FUTURA", "La fecha de respuesta no puede ser futura.");
    if (!s.aceptada() && (s.motivo() == null || s.motivo().isBlank())) {
      throw new ReglaNegocioException("MOTIVO_REQUERIDO", "Indica por qué la rechazó el cliente: sirve para mejorar las propuestas.");
    }
    c.responder(s.aceptada(), s.fecha(), limpio(s.motivo()));
    cotizaciones.saveAndFlush(c);
    tareas.cancelarTareaAutomatica(c.getOrganizacionId(), claveRecordatorio(c.getId()), "El cliente respondió la cotización");
    bitacora.registrar(EntradaBitacora.accion(s.aceptada() ? "COTIZACION_ACEPTADA" : "COTIZACION_RECHAZADA").entidad(ENTIDAD, c.getId()));
    return detalle(c);
  }

  @Transactional
  @PreAuthorize("hasAuthority('cotizaciones:gestionar')")
  public CotizacionDetalle anular(UUID id, SolicitudAnulacion s) {
    Cotizacion c = cargar(id);
    version(c, s.version());
    c.anular(s.motivo().trim(), LocalDate.now(reloj));
    cotizaciones.saveAndFlush(c);
    tareas.cancelarTareaAutomatica(c.getOrganizacionId(), claveRecordatorio(c.getId()), "Cotización anulada");
    bitacora.registrar(EntradaBitacora.accion("COTIZACION_ANULADA").entidad(ENTIDAD, c.getId()).motivo(s.motivo().trim()));
    return detalle(c);
  }

  /**días configurados por MASTER para considerar «sin respuesta» una cotización enviada*/
  int diasSinRespuesta(UUID organizacionId) {
    try { return Integer.parseInt(parametros.valor(organizacionId, ParametroOrganizacion.COTIZACION_DIAS_SIN_RESPUESTA)); }
    catch (NumberFormatException e) { return 7; }
  }

  private void aplicar(Cotizacion c, SolicitudCotizacion s, UsuarioAutenticado actor) {
    ClienteReferencia cliente = clientes.buscar(actor.organizacionId(), s.clienteId()).filter(x -> !x.archivado())
        .orElseThrow(() -> new ReglaNegocioException("CLIENTE_INVALIDO", "El cliente no existe o está archivado."));
    UUID responsable = s.responsableId() == null ? actor.id() : s.responsableId();
    if (usuarios.buscarActivo(actor.organizacionId(), responsable).isEmpty()) {
      throw new ReglaNegocioException("RESPONSABLE_INVALIDO", "El responsable no existe o está deshabilitado.");
    }
    if (s.esquemaCodigo() != null && !s.esquemaCodigo().isBlank() && catalogos.esquema(s.esquemaCodigo()).isEmpty()) {
      throw new ReglaNegocioException("ESQUEMA_INVALIDO", "El esquema de acreditación no existe.");
    }
    // Moneda e impuesto del país del cliente (Perú si no tiene país en el catálogo)
    PaisCatalogo pais = catalogos.pais(cliente.codigoPais() == null ? "PE" : cliente.codigoPais())
        .or(() -> catalogos.pais("PE")).orElseThrow();
    Set<String> normas = catalogos.normas().stream().map(NormaCatalogo::codigo).collect(Collectors.toSet());
    List<ItemCotizacion> lineas = new ArrayList<>();
    int orden = 1;
    for (var i : s.items()) {
      String norma = limpio(i.normaCodigo());
      if (norma != null && !normas.contains(norma)) throw new ReglaNegocioException("NORMA_INVALIDA", "La norma " + norma + " no está en el catálogo.");
      if (norma != null && i.edicion() != null && !i.edicion().isBlank() && catalogos.buscarEdicion(norma, i.edicion().trim()).isEmpty()) {
        throw new ReglaNegocioException("EDICION_INVALIDA", "La edición " + i.edicion() + " no corresponde a la norma.");
      }
      lineas.add(new ItemCotizacion(orden++, norma, limpio(i.edicion()), i.descripcion().trim(),
          i.diasAuditor().setScale(2, java.math.RoundingMode.HALF_UP), i.monto().setScale(2, java.math.RoundingMode.HALF_UP)));
    }
    c.actualizar(cliente.id(), limpio(s.contactoNombre()), limpio(s.contactoCorreo()), limpio(s.esquemaCodigo()), s.tipoServicio(),
        limpio(s.alcance()), s.numeroSedes() == null ? 1 : s.numeroSedes(), limpio(s.sedesDetalle()), pais.moneda(),
        pais.nombreImpuesto(), pais.tasaImpuesto(), s.tarifaDia(), s.validezDias() == null ? 30 : s.validezDias(),
        limpio(s.condicionesPago()), limpio(s.notas()), responsable, lineas);
  }

  private Cotizacion cargar(UUID id) {
    return cotizaciones.findByIdAndOrganizacionId(id, actorActual.requerido().organizacionId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Cotización", id));
  }

  private static void version(Cotizacion c, long version) {
    if (c.getVersion() != version) {
      throw new ConflictoException("CONFLICTO_VERSION", "La cotización fue modificada por otra persona. Recarga los datos y vuelve a intentarlo.");
    }
  }

  private Integer diasSinRespuesta(Cotizacion c) {
    return c.getEstado() == EstadoCotizacion.ENVIADA && c.getEnviadaEn() != null
        ? (int) ChronoUnit.DAYS.between(c.getEnviadaEn(), LocalDate.now(reloj)) : null;
  }

  private CotizacionDetalle detalle(Cotizacion c) {
    UUID org = c.getOrganizacionId();
    Set<UUID> ids = new HashSet<>(List.of(c.getResponsableId()));
    if (c.getCreadoPor() != null) ids.add(c.getCreadoPor());
    Map<UUID, UsuarioResumen> per = usuarios.buscarPorIds(org, ids);
    ClienteReferencia cli = clientes.buscar(org, c.getClienteId()).orElse(null);
    Map<String, String> normas = nombresNormas();
    BigDecimal dias = c.getItems().stream().map(ItemCotizacion::getDiasAuditor).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CotizacionDetalle(c.getId(), c.getNumero(), cliente(cli, c.getClienteId()), c.getContactoNombre(), c.getContactoCorreo(),
        c.getEsquemaCodigo(), c.getTipoServicio(), c.getAlcance(), c.getNumeroSedes(), c.getSedesDetalle(), c.getMoneda(),
        c.getNombreImpuesto(), c.getTasaImpuesto(), c.getTarifaDia(), c.getSubtotal(), c.getImpuesto(), c.getTotal(), dias,
        c.getValidezDias(), c.getFechaEmision().plusDays(c.getValidezDias()), c.getCondicionesPago(), c.getNotas(), c.getEstado(),
        c.getFechaEmision(), c.getEnviadaEn(), c.getDestinatarios(), c.getRespondidaEn(), c.getMotivoRespuesta(), diasSinRespuesta(c),
        referencia(per.get(c.getResponsableId()), c.getResponsableId()),
        c.getCreadoPor() == null ? null : referencia(per.get(c.getCreadoPor()), c.getCreadoPor()),
        c.getItems().stream().map(i -> new ItemRespuesta(i.getId(), i.getNormaCodigo(),
            i.getNormaCodigo() == null ? null : normas.getOrDefault(i.getNormaCodigo(), i.getNormaCodigo()), i.getEdicion(),
            i.getDescripcion(), i.getDiasAuditor(), i.getMonto())).toList(), c.getVersion());
  }

  private Map<String, String> nombresNormas() {
    return catalogos.normas().stream().collect(Collectors.toMap(NormaCatalogo::codigo, NormaCatalogo::nombreCorto, (a, b) -> a));
  }

  private static ClienteCotizacion cliente(ClienteReferencia c, UUID id) {
    return c == null ? new ClienteCotizacion(id, "Cliente no disponible", null, null, null)
        : new ClienteCotizacion(c.id(), c.razonSocial(), c.tipoDocumento(), c.numeroDocumento(), c.codigoPais());
  }

  private static Referencia referencia(UsuarioResumen u, UUID id) { return new Referencia(id, u == null ? "Usuario no disponible" : u.nombre()); }

  private static String limpio(String texto) { return texto == null || texto.isBlank() ? null : texto.trim(); }
}
