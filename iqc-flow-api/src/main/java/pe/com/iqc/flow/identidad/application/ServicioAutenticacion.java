package pe.com.iqc.flow.identidad.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.bitacora.ResultadoOperacion;
import pe.com.iqc.flow.compartido.bitacora.TipoActor;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.SolicitudCambioContrasena;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.UsuarioActualRespuesta;
import pe.com.iqc.flow.identidad.domain.PoliticaContrasena;
import pe.com.iqc.flow.identidad.domain.Usuario;
import pe.com.iqc.flow.identidad.infrastructure.PropiedadesSeguridad;
import pe.com.iqc.flow.identidad.infrastructure.UsuarioRepository;
import pe.com.iqc.flow.organizaciones.ServicioOrganizaciones;

/**inicio y cierre de sesión, bloqueo por intentos fallidos y cambio de contraseña*/
@Service
public class ServicioAutenticacion {
  private static final String ENTIDAD = "USUARIO";

  private final UsuarioRepository usuarios;
  private final PasswordEncoder codificador;
  private final Bitacora bitacora;
  private final ActorActual actorActual;
  private final ServicioOrganizaciones organizaciones;
  private final PropiedadesSeguridad propiedades;
  private final Clock reloj;

  public ServicioAutenticacion(UsuarioRepository usuarios, PasswordEncoder codificador, Bitacora bitacora,
      ActorActual actorActual, ServicioOrganizaciones organizaciones, PropiedadesSeguridad propiedades, Clock reloj) {
    this.usuarios = usuarios;
    this.codificador = codificador;
    this.bitacora = bitacora;
    this.actorActual = actorActual;
    this.organizaciones = organizaciones;
    this.propiedades = propiedades;
    this.reloj = reloj;
  }

  @Transactional
  public void registrarInicioExitoso(UUID usuarioId, UUID organizacionId) {
    usuarios.registrarAccesoExitoso(usuarioId, Instant.now(reloj));
    bitacora.registrar(EntradaBitacora.accion("INICIO_SESION")
        .entidad(ENTIDAD, usuarioId)
        .organizacion(organizacionId)
        .actor(usuarioId, TipoActor.USUARIO));
  }

  @Transactional
  public void registrarInicioFallido(String correoIngresado, String tipoFallo) {
    String correo = Usuario.normalizarCorreo(correoIngresado);
    Instant ahora = Instant.now(reloj);
    Optional<Usuario> usuario = usuarios.findByCorreo(correo);
    boolean bloqueaAhora = usuario.isPresent()
        && !usuario.get().estaBloqueado(ahora)
        && usuario.get().getIntentosFallidos() + 1 >= propiedades.intentosMaximos();
    if (usuario.isPresent()) {
      usuarios.registrarIntentoFallido(correo, propiedades.intentosMaximos(),
          ahora.plus(propiedades.duracionBloqueo()), ahora);
    }
    UUID usuarioId = usuario.map(Usuario::getId).orElse(null);
    UUID organizacionId = usuario.map(Usuario::getOrganizacionId).orElse(null);
    String motivo = usuario.isPresent()
        ? tipoFallo
        : tipoFallo + "; correo no registrado: " + recortar(correo, 120);
    bitacora.registrar(EntradaBitacora.accion("INICIO_SESION")
        .entidad(ENTIDAD, usuarioId)
        .organizacion(organizacionId)
        .resultado(ResultadoOperacion.FALLIDO)
        .motivo(motivo));
    if (bloqueaAhora) {
      bitacora.registrar(EntradaBitacora.accion("BLOQUEO_CUENTA")
          .entidad(ENTIDAD, usuarioId)
          .organizacion(organizacionId)
          .motivo(propiedades.intentosMaximos() + " intentos fallidos; bloqueada por " + propiedades.duracionBloqueo()));
    }
  }

  @Transactional
  public void registrarCierreSesion(UUID usuarioId, UUID organizacionId) {
    bitacora.registrar(EntradaBitacora.accion("CIERRE_SESION")
        .entidad(ENTIDAD, usuarioId)
        .organizacion(organizacionId)
        .actor(usuarioId, TipoActor.USUARIO));
  }

  @Transactional(readOnly = true)
  public UsuarioActualRespuesta usuarioActual() {
    Usuario usuario = cargarActual();
    return new UsuarioActualRespuesta(usuario.getId(), usuario.getCorreo(), usuario.getNombre(),
        organizaciones.obtener(usuario.getOrganizacionId()), usuario.codigosRoles(), usuario.permisos(),
        usuario.isDebeCambiarContrasena());
  }

  @Transactional
  public void cambiarContrasena(SolicitudCambioContrasena solicitud) {
    Usuario usuario = cargarActual();
    if (!codificador.matches(solicitud.contrasenaActual(), usuario.getHashContrasena())) {
      throw new ReglaNegocioException("CONTRASENA_ACTUAL_INCORRECTA", "La contraseña actual no es correcta.");
    }
    PoliticaContrasena.validar(solicitud.contrasenaNueva(), usuario.getCorreo());
    if (codificador.matches(solicitud.contrasenaNueva(), usuario.getHashContrasena())) {
      throw new ReglaNegocioException("CONTRASENA_REPETIDA", "La nueva contraseña debe ser distinta de la actual.");
    }
    usuario.cambiarContrasena(codificador.encode(solicitud.contrasenaNueva()), Instant.now(reloj));
    bitacora.registrar(EntradaBitacora.accion("CONTRASENA_CAMBIADA").entidad(ENTIDAD, usuario.getId()));
  }

  private Usuario cargarActual() {
    UUID id = actorActual.requerido().id();
    return usuarios.findConPermisosById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
  }

  private static String recortar(String valor, int maximo) {
    return valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
