package pe.com.iqc.flow.clientes.application;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.clientes.application.DtosClientes.ClienteDetalle;
import pe.com.iqc.flow.clientes.application.DtosClientes.ClienteResumen;
import pe.com.iqc.flow.clientes.application.DtosClientes.ContactoRespuesta;
import pe.com.iqc.flow.clientes.application.DtosClientes.DireccionRespuesta;
import pe.com.iqc.flow.clientes.application.DtosClientes.ResponsableRespuesta;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudActualizarCliente;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudArchivo;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudCrearCliente;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudReasignarCliente;
import pe.com.iqc.flow.clientes.application.DtosClientes.VerificacionDocumento;
import pe.com.iqc.flow.clientes.domain.Cliente;
import pe.com.iqc.flow.clientes.domain.EstadoCliente;
import pe.com.iqc.flow.clientes.domain.NormalizadorTexto;
import pe.com.iqc.flow.clientes.domain.TipoDocumento;
import pe.com.iqc.flow.clientes.domain.ValidadorDocumento;
import pe.com.iqc.flow.clientes.infrastructure.ClienteRepository;
import pe.com.iqc.flow.clientes.infrastructure.ContactoClienteRepository;
import pe.com.iqc.flow.clientes.infrastructure.DireccionClienteRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;

/**registro, búsqueda, edición, reasignación y archivo de clientes*/
@Service
public class ServicioClientes {
  static final String ENTIDAD = "CLIENTE";
  private static final String PAIS_POR_DEFECTO = "PE";

  private final ClienteRepository clientes;
  private final ContactoClienteRepository contactos;
  private final DireccionClienteRepository direcciones;
  private final AccesoClientes acceso;
  private final DirectorioUsuarios directorio;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioClientes(ClienteRepository clientes, ContactoClienteRepository contactos,
      DireccionClienteRepository direcciones, AccesoClientes acceso, DirectorioUsuarios directorio,
      ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.clientes = clientes;
    this.contactos = contactos;
    this.direcciones = direcciones;
    this.acceso = acceso;
    this.directorio = directorio;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('clientes:leer')")
  public Page<ClienteResumen> buscar(String texto, EstadoCliente estado, UUID responsableId, boolean incluirArchivados,
      int pagina, int tamano) {
    UUID organizacionId = actorActual.requerido().organizacionId();
    Specification<Cliente> filtro = (raiz, consulta, cb) -> {
      List<Predicate> condiciones = new ArrayList<>();
      condiciones.add(cb.equal(raiz.get("organizacionId"), organizacionId));
      if (!incluirArchivados) condiciones.add(cb.isNull(raiz.get("archivadoEn")));
      if (texto != null && !texto.isBlank()) {
        String patron = "%" + escaparComodines(NormalizadorTexto.normalizar(texto)) + "%";
        condiciones.add(cb.or(
            cb.like(raiz.get("textoBusqueda"), patron, '\\'),
            cb.like(cb.lower(raiz.get("codigo")), patron, '\\')));
      }
      if (estado != null) condiciones.add(cb.equal(raiz.get("estado"), estado));
      if (responsableId != null) condiciones.add(cb.equal(raiz.get("responsableUsuarioId"), responsableId));
      return cb.and(condiciones.toArray(Predicate[]::new));
    };
    Page<Cliente> resultado = clientes.findAll(filtro,
        PageRequest.of(pagina, tamano, Sort.by("razonSocial").and(Sort.by("codigo"))));
    Map<UUID, ResponsableRespuesta> responsables = responsables(resultado.getContent().stream()
        .map(Cliente::getResponsableUsuarioId).collect(Collectors.toSet()));
    return resultado.map(cliente -> ClienteResumen.de(cliente, responsables.get(cliente.getResponsableUsuarioId())));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('clientes:leer')")
  public ClienteDetalle obtener(UUID id) { return detalle(acceso.cargar(id)); }

  /**comprueba el formato del documento y si ya pertenece a un cliente vigente, sin registrar nada*/
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('clientes:leer')")
  public VerificacionDocumento verificarDocumento(String codigoPais, TipoDocumento tipo, String numero) {
    String pais = codigoPais == null || codigoPais.isBlank() ? PAIS_POR_DEFECTO : codigoPais;
    String normalizado;
    try {
      normalizado = ValidadorDocumento.normalizar(pais, tipo, numero);
    } catch (ReglaNegocioException ex) {
      return new VerificacionDocumento(false, null, ex.getMessage(), null);
    }
    ClienteResumen existente = clientes.buscarVigentePorDocumento(actorActual.requerido().organizacionId(),
            ValidadorDocumento.normalizarPais(pais), tipo, normalizado)
        .map(this::resumen).orElse(null);
    String mensaje = existente == null
        ? "Documento válido y disponible."
        : "Ya existe el cliente " + existente.codigo() + " (" + existente.razonSocial() + ") con este documento.";
    return new VerificacionDocumento(true, normalizado, mensaje, existente);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:escribir')")
  public ClienteDetalle crear(SolicitudCrearCliente solicitud) {
    UsuarioAutenticado actor = actorActual.requerido();
    UUID responsable = resolverResponsable(solicitud.responsableId(), actor);
    Cliente cliente = Cliente.nuevo(actor.organizacionId(), solicitud.razonSocial(),
        solicitud.codigoPais() == null ? PAIS_POR_DEFECTO : solicitud.codigoPais(), responsable, actor.id());
    if (solicitud.tipoDocumento() != null || solicitud.numeroDocumento() != null) {
      cliente.asignarDocumento(cliente.getCodigoPais(), solicitud.tipoDocumento(), solicitud.numeroDocumento());
      verificarDocumentoLibre(cliente.getOrganizacionId(), cliente.getCodigoPais(), cliente.getTipoDocumento(),
          cliente.getNumeroDocumento(), null);
    }
    cliente.actualizarDatos(cliente.getRazonSocial(), limpio(solicitud.nombreComercial()), limpio(solicitud.sector()),
        limpio(solicitud.sitioWeb()), solicitud.origen(), limpio(solicitud.notas()));
    if (solicitud.estado() != null) cliente.cambiarEstado(solicitud.estado());
    //saveAndFlush inserta de inmediato para obtener el código CLI-000000 que genera la base de datos
    clientes.saveAndFlush(cliente);
    bitacora.registrar(EntradaBitacora.accion("CLIENTE_CREADO")
        .entidad(ENTIDAD, cliente.getId())
        .estadoNuevo(instantanea(cliente)));
    return detalle(cliente);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:escribir')")
  public ClienteDetalle actualizar(UUID id, SolicitudActualizarCliente solicitud) {
    Cliente cliente = acceso.cargarEditable(id);
    verificarVersion(cliente.getVersion(), solicitud.version());
    Map<String, Object> antes = instantanea(cliente);

    boolean cambiaDocumento = solicitud.tipoDocumento() != null || solicitud.numeroDocumento() != null;
    String pais = solicitud.codigoPais() != null ? solicitud.codigoPais() : cliente.getCodigoPais();
    TipoDocumento tipo = solicitud.tipoDocumento() != null ? solicitud.tipoDocumento() : cliente.getTipoDocumento();
    String numero = solicitud.numeroDocumento() != null ? solicitud.numeroDocumento() : cliente.getNumeroDocumento();
    // Se verifica antes de modificar la entidad: si no, Hibernate escribiría el cambio antes de la consulta
    // y la restricción única de la base respondería con un error genérico
    if ((cambiaDocumento || solicitud.codigoPais() != null) && (tipo != null || numero != null)) {
      verificarDocumentoLibre(cliente.getOrganizacionId(), ValidadorDocumento.normalizarPais(pais), tipo,
          ValidadorDocumento.normalizar(pais, tipo, numero), cliente.getId());
    }
    if (cambiaDocumento) {
      cliente.asignarDocumento(pais, tipo, numero);
    } else if (solicitud.codigoPais() != null) {
      cliente.cambiarPais(pais);
    }

    cliente.actualizarDatos(
        solicitud.razonSocial() != null ? solicitud.razonSocial() : cliente.getRazonSocial(),
        opcional(solicitud.nombreComercial(), cliente.getNombreComercial()),
        opcional(solicitud.sector(), cliente.getSector()),
        opcional(solicitud.sitioWeb(), cliente.getSitioWeb()),
        solicitud.origen() != null ? solicitud.origen() : cliente.getOrigen(),
        opcional(solicitud.notas(), cliente.getNotas()));
    if (solicitud.estado() != null) cliente.cambiarEstado(solicitud.estado());
    clientes.flush();
    registrarCambio("CLIENTE_ACTUALIZADO", cliente, antes, null);
    return detalle(cliente);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:reasignar')")
  public ClienteDetalle reasignar(UUID id, SolicitudReasignarCliente solicitud) {
    Cliente cliente = acceso.cargar(id);
    acceso.verificarNoArchivado(cliente);
    verificarVersion(cliente.getVersion(), solicitud.version());
    if (!cliente.getResponsableUsuarioId().equals(solicitud.responsableId())) {
      Map<String, Object> antes = instantanea(cliente);
      cliente.reasignar(validarResponsable(cliente.getOrganizacionId(), solicitud.responsableId()));
      clientes.flush();
      registrarCambio("CLIENTE_REASIGNADO", cliente, antes, null);
    }
    return detalle(cliente);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:archivar')")
  public ClienteDetalle archivar(UUID id, SolicitudArchivo solicitud) {
    Cliente cliente = acceso.cargar(id);
    verificarVersion(cliente.getVersion(), solicitud.version());
    Map<String, Object> antes = instantanea(cliente);
    cliente.archivar(Instant.now(reloj));
    clientes.flush();
    registrarCambio("CLIENTE_ARCHIVADO", cliente, antes, solicitud.motivo());
    return detalle(cliente);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:archivar')")
  public ClienteDetalle restaurar(UUID id, SolicitudArchivo solicitud) {
    Cliente cliente = acceso.cargar(id);
    verificarVersion(cliente.getVersion(), solicitud.version());
    Map<String, Object> antes = instantanea(cliente);
    if (cliente.estaArchivado() && cliente.tieneDocumento()) {
      verificarDocumentoLibre(cliente.getOrganizacionId(), cliente.getCodigoPais(), cliente.getTipoDocumento(),
          cliente.getNumeroDocumento(), cliente.getId());
    }
    cliente.restaurar();
    clientes.flush();
    registrarCambio("CLIENTE_RESTAURADO", cliente, antes, solicitud.motivo());
    return detalle(cliente);
  }

  /**sin responsable indicado (o el propio actor) se asigna a quien registra; otro responsable requiere reasignar*/
  private UUID resolverResponsable(UUID solicitado, UsuarioAutenticado actor) {
    if (solicitado == null || solicitado.equals(actor.id())) return actor.id();
    if (!actorActual.tienePermiso(AccesoClientes.REASIGNAR)) {
      throw new AccessDeniedException("Elegir otro responsable requiere el permiso clientes:reasignar");
    }
    return validarResponsable(actor.organizacionId(), solicitado);
  }

  private UUID validarResponsable(UUID organizacionId, UUID usuarioId) {
    UsuarioResumen usuario = directorio.buscarActivo(organizacionId, usuarioId)
        .orElseThrow(() -> new ReglaNegocioException("RESPONSABLE_INVALIDO",
            "El responsable indicado no existe o está deshabilitado."));
    if (!usuario.tienePermiso(AccesoClientes.ESCRIBIR)) {
      throw new ReglaNegocioException("RESPONSABLE_SIN_PERMISO",
          usuario.nombre() + " no tiene un rol que le permita gestionar clientes.");
    }
    return usuario.id();
  }

  /**lanza DOCUMENTO_DUPLICADO si otro cliente vigente (distinto de excluirId) ya tiene ese documento*/
  private void verificarDocumentoLibre(UUID organizacionId, String codigoPais, TipoDocumento tipo, String numero,
      UUID excluirId) {
    clientes.buscarVigentePorDocumento(organizacionId, codigoPais, tipo, numero)
        .filter(otro -> !otro.getId().equals(excluirId))
        .ifPresent(otro -> {
          throw new ConflictoException("DOCUMENTO_DUPLICADO", "Ya existe el cliente " + otro.getCodigo() + " ("
              + otro.getRazonSocial() + ") con el " + otro.getTipoDocumento() + " " + otro.getNumeroDocumento() + ".");
        });
  }

  private ClienteDetalle detalle(Cliente cliente) {
    ResponsableRespuesta responsable = responsables(Set.of(cliente.getResponsableUsuarioId()))
        .get(cliente.getResponsableUsuarioId());
    List<ContactoRespuesta> listaContactos = contactos
        .findByClienteIdOrderByEsPrincipalDescNombreCompletoAsc(cliente.getId()).stream()
        .map(ContactoRespuesta::de).toList();
    List<DireccionRespuesta> listaDirecciones = direcciones.findByClienteIdOrderByTipoAscNombreAsc(cliente.getId())
        .stream().map(DireccionRespuesta::de).toList();
    return new ClienteDetalle(cliente.getId(), cliente.getCodigo(), cliente.getRazonSocial(),
        cliente.getNombreComercial(), cliente.getCodigoPais(), cliente.getTipoDocumento(), cliente.getNumeroDocumento(),
        cliente.getEstado(), responsable, cliente.getSector(), cliente.getSitioWeb(), cliente.getOrigen(),
        cliente.getNotas(), listaContactos, listaDirecciones, cliente.getCreadoEn(), cliente.getActualizadoEn(),
        cliente.getArchivadoEn(), cliente.getVersion(), acceso.puedeEditar(cliente));
  }

  private ClienteResumen resumen(Cliente cliente) {
    return ClienteResumen.de(cliente, responsables(Set.of(cliente.getResponsableUsuarioId()))
        .get(cliente.getResponsableUsuarioId()));
  }

  private Map<UUID, ResponsableRespuesta> responsables(Set<UUID> ids) {
    return directorio.buscarPorIds(actorActual.requerido().organizacionId(), ids).values().stream()
        .collect(Collectors.toMap(UsuarioResumen::id, usuario -> new ResponsableRespuesta(usuario.id(), usuario.nombre())));
  }

  private void registrarCambio(String accion, Cliente cliente, Map<String, Object> antes, String motivo) {
    bitacora.registrar(EntradaBitacora.accion(accion)
        .entidad(ENTIDAD, cliente.getId())
        .estadoAnterior(antes)
        .estadoNuevo(instantanea(cliente))
        .motivo(motivo));
  }

  private static Map<String, Object> instantanea(Cliente cliente) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("codigo", cliente.getCodigo());
    datos.put("razonSocial", cliente.getRazonSocial());
    datos.put("nombreComercial", cliente.getNombreComercial());
    datos.put("codigoPais", cliente.getCodigoPais());
    datos.put("tipoDocumento", cliente.getTipoDocumento() == null ? null : cliente.getTipoDocumento().name());
    datos.put("numeroDocumento", cliente.getNumeroDocumento());
    datos.put("estado", cliente.getEstado().name());
    datos.put("responsableId", cliente.getResponsableUsuarioId());
    datos.put("sector", cliente.getSector());
    datos.put("sitioWeb", cliente.getSitioWeb());
    datos.put("origen", cliente.getOrigen() == null ? null : cliente.getOrigen().name());
    datos.put("notas", cliente.getNotas());
    datos.put("archivado", cliente.estaArchivado());
    return datos;
  }

  static void verificarVersion(long actual, Long enviada) {
    if (!Objects.equals(actual, enviada)) {
      throw new ConflictoException("CONFLICTO_VERSION",
          "El registro fue modificado por otra persona. Recarga los datos y vuelve a intentarlo.");
    }
  }

  /**null o vacío → sin valor*/
  static String limpio(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }

  /**null → conserva el actual; vacío → borra; otro → nuevo valor*/
  static String opcional(String nuevo, String actual) {
    if (nuevo == null) return actual;
    return limpio(nuevo);
  }

  private static String escaparComodines(String texto) {
    return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
