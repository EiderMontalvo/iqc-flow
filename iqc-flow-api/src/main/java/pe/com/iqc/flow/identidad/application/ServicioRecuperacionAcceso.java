package pe.com.iqc.flow.identidad.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.application.DtosIdentidad.UsuarioRespuesta;
import pe.com.iqc.flow.identidad.domain.PoliticaContrasena;
import pe.com.iqc.flow.identidad.domain.Usuario;
import pe.com.iqc.flow.identidad.infrastructure.UsuarioRepository;
import pe.com.iqc.flow.notificaciones.NuevaNotificacion;
import pe.com.iqc.flow.notificaciones.ServicioNotificaciones;

/**
 *«Olvidé mi contraseña» mientras no hay correo configurado: la solicitud avisa a quienes gestionan usuarios y estos
 *asignan una contraseña temporal, que la persona debe cambiar al ingresar. La respuesta pública es siempre la misma,
 *exista o no el correo, para no revelar qué cuentas existen
 */
@Service
public class ServicioRecuperacionAcceso {
  private final UsuarioRepository usuarios;
  private final DirectorioUsuarios directorio;
  private final ServicioNotificaciones notificaciones;
  private final PasswordEncoder codificador;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioRecuperacionAcceso(UsuarioRepository usuarios, DirectorioUsuarios directorio, ServicioNotificaciones notificaciones,
      PasswordEncoder codificador, ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.usuarios = usuarios;
    this.directorio = directorio;
    this.notificaciones = notificaciones;
    this.codificador = codificador;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  /**avisa a quienes gestionan usuarios en la organización de esa cuenta; como mucho una vez al día por cuenta*/
  @Transactional
  public void solicitar(String correo) {
    if (correo == null || correo.isBlank()) return;
    usuarios.findByCorreo(Usuario.normalizarCorreo(correo)).filter(Usuario::estaActivo).ifPresent(u -> {
      String dia = LocalDate.now(reloj.withZone(ZoneId.of("America/Lima"))).toString();
      directorio.activosConPermiso(u.getOrganizacionId(), "usuarios:gestionar").stream()
          .filter(admin -> !admin.id().equals(u.getId()))
          .forEach(admin -> notificaciones.notificar(new NuevaNotificacion(u.getOrganizacionId(), admin.id(), "ACCESO_SOLICITADO",
              "Restablecer contraseña de " + u.getNombre(),
              u.getNombre() + " (" + u.getCorreo() + ") olvidó su contraseña. Asígnale una contraseña temporal en Usuarios → Editar.",
              "USUARIO", u.getId(), "olvido:" + u.getId() + ":" + admin.id() + ":" + dia)));
      bitacora.registrar(EntradaBitacora.accion("CONTRASENA_OLVIDADA").entidad("USUARIO", u.getId()).organizacion(u.getOrganizacionId()));
    });
  }

  /**contraseña temporal asignada por un administrador: desbloquea la cuenta y obliga a cambiarla al ingresar*/
  @Transactional
  @PreAuthorize("hasAuthority('usuarios:gestionar')")
  public UsuarioRespuesta asignarTemporal(UUID id, String contrasena, long version) {
    var actor = actorActual.requerido();
    Usuario u = usuarios.findById(id).filter(x -> x.getOrganizacionId().equals(actor.organizacionId()))
        .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    if (u.getVersion() != version) throw new ConflictoException("CONFLICTO_VERSION",
        "El usuario fue modificado por otra persona. Recarga los datos y vuelve a intentarlo.");
    PoliticaContrasena.validar(contrasena, u.getCorreo());
    u.asignarContrasenaTemporal(codificador.encode(contrasena), Instant.now(reloj));
    bitacora.registrar(EntradaBitacora.accion("CONTRASENA_TEMPORAL_ASIGNADA").entidad("USUARIO", u.getId())
        .estadoNuevo(Map.of("debeCambiarContrasena", true)));
    return UsuarioRespuesta.de(usuarios.saveAndFlush(u));
  }
}
