package pe.com.iqc.flow.identidad.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.identidad.domain.Usuario;

/**carga usuarios para Spring Security, tanto al iniciar sesión como en cada solicitud*/
@Service
public class ServicioDetallesUsuario implements UserDetailsService {
  private final UsuarioRepository usuarios;
  private final Clock reloj;

  public ServicioDetallesUsuario(UsuarioRepository usuarios, Clock reloj) {
    this.usuarios = usuarios;
    this.reloj = reloj;
  }

  @Override
  @Transactional(readOnly = true)
  public PrincipalUsuario loadUserByUsername(String correo) {
    return usuarios.findByCorreo(Usuario.normalizarCorreo(correo))
        .map(usuario -> PrincipalUsuario.de(usuario, Instant.now(reloj)))
        .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
  }

  /**datos actuales del usuario si sigue activo; vacío si fue deshabilitado o eliminado*/
  @Transactional(readOnly = true)
  public Optional<PrincipalUsuario> cargarVigente(UUID id) {
    return usuarios.findConPermisosById(id)
        .filter(Usuario::estaActivo)
        .map(usuario -> PrincipalUsuario.de(usuario, Instant.now(reloj)));
  }
}
