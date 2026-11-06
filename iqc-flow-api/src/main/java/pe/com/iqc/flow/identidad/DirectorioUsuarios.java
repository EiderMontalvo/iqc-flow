package pe.com.iqc.flow.identidad;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**API pública de identidad para otros módulos: consulta de usuarios internos*/
public interface DirectorioUsuarios {
  /**usuario activo de la organización, con sus permisos actuales*/
  Optional<UsuarioResumen> buscarActivo(UUID organizacionId, UUID usuarioId);

  /**usuarios de la organización con esos identificadores (activos o no), indexados por id*/
  Map<UUID, UsuarioResumen> buscarPorIds(UUID organizacionId, Collection<UUID> ids);

  /**usuarios activos de la organización que tienen el permiso por alguno de sus roles*/
  List<UsuarioResumen> activosConPermiso(UUID organizacionId, String permiso);
}
