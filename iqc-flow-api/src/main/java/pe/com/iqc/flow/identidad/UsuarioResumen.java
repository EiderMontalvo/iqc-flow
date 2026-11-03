package pe.com.iqc.flow.identidad;

import java.util.Set;
import java.util.UUID;

/**datos públicos de un usuario interno para otros módulos*/
public record UsuarioResumen(UUID id, String nombre, String correo, boolean activo, Set<String> permisos) {
  public boolean tienePermiso(String permiso) { return permisos.contains(permiso); }
}
