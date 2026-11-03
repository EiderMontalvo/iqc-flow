package pe.com.iqc.flow.identidad.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.RolRespuesta;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.SolicitudActualizarUsuario;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.SolicitudAsignarRoles;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.SolicitudCrearUsuario;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.UsuarioRespuesta;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.EstadoUsuario;
import pe.com.iqc.flow.identidad.domain.PoliticaContrasena;
import pe.com.iqc.flow.identidad.domain.Rol;
import pe.com.iqc.flow.identidad.domain.Usuario;
import pe.com.iqc.flow.identidad.infrastructure.RolRepository;
import pe.com.iqc.flow.identidad.infrastructure.UsuarioRepository;

/**administración de usuarios internos y sus roles, limitada a la organización del actor*/
@Service
public class ServicioUsuarios {
  private static final String ENTIDAD = "USUARIO";

  private final UsuarioRepository usuarios;
  private final RolRepository roles;
  private final PasswordEncoder codificador;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioUsuarios(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder codificador,
      ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.codificador = codificador;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('usuarios:leer')")
  public Page<UsuarioRespuesta> listar(String texto, int pagina, int tamano) {
    String patron = "%" + (texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT)) + "%";
    return usuarios.buscar(actorActual.requerido().organizacionId(), patron,
            PageRequest.of(pagina, tamano, Sort.by("nombre")))
        .map(UsuarioRespuesta::de);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('usuarios:leer')")
  public UsuarioRespuesta obtener(UUID id) { return UsuarioRespuesta.de(cargar(id)); }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('usuarios:leer')")
  public List<RolRespuesta> listarRoles() {
    return roles.listarDisponibles(actorActual.requerido().organizacionId()).stream().map(RolRespuesta::de).toList();
  }

  @Transactional
  @PreAuthorize("hasAuthority('usuarios:gestionar')")
  public UsuarioRespuesta crear(SolicitudCrearUsuario solicitud) {
    UsuarioAutenticado actor = actorActual.requerido();
    Set<String> codigosRoles = solicitud.roles() == null ? Set.of() : solicitud.roles();
    if (!codigosRoles.isEmpty() && !actorActual.tienePermiso("roles:asignar")) {
      throw new AccessDeniedException("Asignar roles al crear un usuario requiere el permiso roles:asignar");
    }
    String correo = Usuario.normalizarCorreo(solicitud.correo());
    if (usuarios.existsByCorreo(correo)) {
      throw new ConflictoException("CORREO_DUPLICADO", "Ya existe un usuario con el correo " + correo + ".");
    }
    PoliticaContrasena.validar(solicitud.contrasenaInicial(), correo);
    Usuario usuario = usuarios.save(Usuario.nuevo(actor.organizacionId(), correo, solicitud.nombre(),
        codificador.encode(solicitud.contrasenaInicial()), resolverRoles(codigosRoles, actor.organizacionId()),
        Instant.now(reloj)));
    bitacora.registrar(EntradaBitacora.accion("USUARIO_CREADO")
        .entidad(ENTIDAD, usuario.getId())
        .estadoNuevo(instantanea(usuario)));
    return UsuarioRespuesta.de(usuario);
  }

  @Transactional
  @PreAuthorize("hasAuthority('usuarios:gestionar')")
  public UsuarioRespuesta actualizar(UUID id, SolicitudActualizarUsuario solicitud) {
    Usuario usuario = cargar(id);
    verificarVersion(usuario, solicitud.version());
    Map<String, Object> antes = instantanea(usuario);
    if (solicitud.estado() == EstadoUsuario.DESHABILITADO && usuario.estaActivo()) {
      if (usuario.getId().equals(actorActual.requerido().id())) {
        throw new ReglaNegocioException("NO_PUEDE_DESHABILITARSE", "No puedes deshabilitar tu propio usuario.");
      }
      protegerUltimoMaster(usuario);
    }
    if (solicitud.nombre() != null) usuario.renombrar(solicitud.nombre());
    if (solicitud.estado() != null) usuario.cambiarEstado(solicitud.estado());
    usuarios.flush();
    bitacora.registrar(EntradaBitacora.accion("USUARIO_ACTUALIZADO")
        .entidad(ENTIDAD, usuario.getId())
        .estadoAnterior(antes)
        .estadoNuevo(instantanea(usuario)));
    return UsuarioRespuesta.de(usuario);
  }

  @Transactional
  @PreAuthorize("hasAuthority('roles:asignar')")
  public UsuarioRespuesta asignarRoles(UUID id, SolicitudAsignarRoles solicitud) {
    Usuario usuario = cargar(id);
    verificarVersion(usuario, solicitud.version());
    Map<String, Object> antes = instantanea(usuario);
    if (usuario.tieneRol(CodigosRol.MASTER) && !solicitud.roles().contains(CodigosRol.MASTER)
        && usuario.estaActivo()) {
      protegerUltimoMaster(usuario);
    }
    usuario.reemplazarRoles(resolverRoles(solicitud.roles(), usuario.getOrganizacionId()));
    usuarios.flush();
    bitacora.registrar(EntradaBitacora.accion("ROLES_ASIGNADOS")
        .entidad(ENTIDAD, usuario.getId())
        .estadoAnterior(antes)
        .estadoNuevo(instantanea(usuario)));
    return UsuarioRespuesta.de(usuario);
  }

  private Usuario cargar(UUID id) {
    UUID organizacionId = actorActual.requerido().organizacionId();
    return usuarios.findConPermisosById(id)
        .filter(usuario -> usuario.getOrganizacionId().equals(organizacionId))
        .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
  }

  private Set<Rol> resolverRoles(Set<String> codigos, UUID organizacionId) {
    if (codigos.isEmpty()) return Set.of();
    List<Rol> encontrados = roles.buscarDisponibles(codigos, organizacionId);
    Set<String> inexistentes = new TreeSet<>(codigos);
    encontrados.forEach(rol -> inexistentes.remove(rol.getCodigo()));
    if (!inexistentes.isEmpty()) {
      throw new ReglaNegocioException("ROL_INEXISTENTE", "Roles inexistentes: " + String.join(", ", inexistentes));
    }
    return Set.copyOf(encontrados);
  }

  private void protegerUltimoMaster(Usuario usuario) {
    if (usuario.tieneRol(CodigosRol.MASTER) && usuarios.contarPorRolYEstado(
        usuario.getOrganizacionId(), CodigosRol.MASTER, EstadoUsuario.ACTIVO) <= 1) {
      throw new ReglaNegocioException("ULTIMO_MASTER",
          "La organización debe conservar al menos un usuario MASTER activo.");
    }
  }

  private static void verificarVersion(Usuario usuario, long version) {
    if (usuario.getVersion() != version) {
      throw new ConflictoException("CONFLICTO_VERSION",
          "El usuario fue modificado por otra persona. Recarga los datos y vuelve a intentarlo.");
    }
  }

  /**estado auditable del usuario. Nunca incluye el hash de la contraseña*/
  private static Map<String, Object> instantanea(Usuario usuario) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("correo", usuario.getCorreo());
    datos.put("nombre", usuario.getNombre());
    datos.put("estado", usuario.getEstado().name());
    datos.put("roles", usuario.codigosRoles().stream().collect(Collectors.toList()));
    return datos;
  }
}
