package pe.com.iqc.flow.identidad.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;
import pe.com.iqc.flow.identidad.domain.Usuario;
import pe.com.iqc.flow.identidad.infrastructure.UsuarioRepository;

@Service
class DirectorioUsuariosJpa implements DirectorioUsuarios {
  private final UsuarioRepository usuarios;

  DirectorioUsuariosJpa(UsuarioRepository usuarios) { this.usuarios = usuarios; }

  @Override
  @Transactional(readOnly = true)
  public Optional<UsuarioResumen> buscarActivo(UUID organizacionId, UUID usuarioId) {
    return usuarios.findConPermisosById(usuarioId)
        .filter(usuario -> usuario.getOrganizacionId().equals(organizacionId) && usuario.estaActivo())
        .map(DirectorioUsuariosJpa::resumir);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, UsuarioResumen> buscarPorIds(UUID organizacionId, Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return usuarios.findAllById(ids).stream()
        .filter(usuario -> usuario.getOrganizacionId().equals(organizacionId))
        .map(DirectorioUsuariosJpa::resumir)
        .collect(Collectors.toMap(UsuarioResumen::id, Function.identity()));
  }

  private static UsuarioResumen resumir(Usuario usuario) {
    return new UsuarioResumen(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.estaActivo(),
        usuario.permisos());
  }
}
