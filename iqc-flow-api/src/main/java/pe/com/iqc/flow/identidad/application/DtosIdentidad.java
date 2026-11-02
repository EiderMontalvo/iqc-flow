package pe.com.iqc.flow.identidad.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.identidad.domain.EstadoUsuario;
import pe.com.iqc.flow.identidad.domain.Rol;
import pe.com.iqc.flow.identidad.domain.Usuario;
import pe.com.iqc.flow.organizaciones.OrganizacionResumen;

/**solicitudes y respuestas de la API de identidad. */
public final class DtosIdentidad {
  private DtosIdentidad() {
  }

  public record SolicitudCrearUsuario(
      @NotBlank @Email @Size(max = 320) String correo,
      @NotBlank @Size(max = 200) String nombre,
      @NotBlank @Size(max = 128) String contrasenaInicial,
      Set<@NotBlank String> roles) {
  }

  /**los campos nulos no se modifican. La versión es obligatoria para detectar ediciones simultáneas. */
  public record SolicitudActualizarUsuario(
      @Size(min = 1, max = 200) String nombre,
      EstadoUsuario estado,
      @NotNull Long version) {
  }

  public record SolicitudAsignarRoles(
      @NotNull Set<@NotBlank String> roles,
      @NotNull Long version) {
  }

  public record SolicitudCambioContrasena(
      @NotBlank String contrasenaActual,
      @NotBlank @Size(max = 128) String contrasenaNueva) {
  }

  public record UsuarioRespuesta(
      UUID id,
      String correo,
      String nombre,
      EstadoUsuario estado,
      Set<String> roles,
      boolean debeCambiarContrasena,
      boolean mfaHabilitado,
      Instant ultimoAccesoEn,
      Instant creadoEn,
      long version) {
    static UsuarioRespuesta de(Usuario usuario) {
      return new UsuarioRespuesta(usuario.getId(), usuario.getCorreo(), usuario.getNombre(), usuario.getEstado(),
          usuario.codigosRoles(), usuario.isDebeCambiarContrasena(), usuario.isMfaHabilitado(),
          usuario.getUltimoAccesoEn(), usuario.getCreadoEn(), usuario.getVersion());
    }
  }

  public record RolRespuesta(String codigo, String nombre, String descripcion, Set<String> permisos) {
    static RolRespuesta de(Rol rol) {
      return new RolRespuesta(rol.getCodigo(), rol.getNombre(), rol.getDescripcion(),
          rol.getPermisos().stream().map(permiso -> permiso.getCodigo())
              .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)));
    }
  }

  public record UsuarioActualRespuesta(
      UUID id,
      String correo,
      String nombre,
      OrganizacionResumen organizacion,
      Set<String> roles,
      Set<String> permisos,
      boolean debeCambiarContrasena) {
  }
}
