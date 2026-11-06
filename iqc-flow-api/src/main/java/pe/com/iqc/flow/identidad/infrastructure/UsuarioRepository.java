package pe.com.iqc.flow.identidad.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.identidad.domain.EstadoUsuario;
import pe.com.iqc.flow.identidad.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
  @EntityGraph(attributePaths = {"roles", "roles.permisos"})
  Optional<Usuario> findByCorreo(String correo);

  @EntityGraph(attributePaths = {"roles", "roles.permisos"})
  Optional<Usuario> findConPermisosById(UUID id);

  boolean existsByCorreo(String correo);

  @EntityGraph(attributePaths = {"roles", "roles.permisos"})
  @Query("""
      select distinct u from Usuario u join u.roles r join r.permisos p
      where u.organizacionId = :organizacionId and p.codigo = :permiso and u.estado = :estado
      order by u.nombre
      """)
  java.util.List<Usuario> activosConPermiso(UUID organizacionId, String permiso, EstadoUsuario estado);

  @Query("""
      select u from Usuario u
      where u.organizacionId = :organizacionId
        and (lower(u.nombre) like :patron or u.correo like :patron)
      """)
  Page<Usuario> buscar(UUID organizacionId, String patron, Pageable paginacion);

  @Query("""
      select count(distinct u) from Usuario u join u.roles r
      where u.organizacionId = :organizacionId and r.codigo = :codigoRol and u.estado = :estado
      """)
  long contarPorRolYEstado(UUID organizacionId, String codigoRol, EstadoUsuario estado);

  @Query("select count(u) > 0 from Usuario u join u.roles r where r.codigo = :codigoRol")
  boolean existeConRol(String codigoRol);

  /**
   *suma un intento fallido de forma atómica. Al alcanzar el máximo bloquea la cuenta y reinicia
   *el contador. Los intentos durante un bloqueo vigente no cuentan ni lo prolongan
   */
  @Modifying
  @Query("""
      update Usuario u set
        u.intentosFallidos = case when u.intentosFallidos + 1 >= :maximo then 0 else u.intentosFallidos + 1 end,
        u.bloqueadoHasta = case when u.intentosFallidos + 1 >= :maximo then :bloqueoHasta else u.bloqueadoHasta end
      where u.correo = :correo and (u.bloqueadoHasta is null or u.bloqueadoHasta <= :ahora)
      """)
  int registrarIntentoFallido(String correo, int maximo, Instant bloqueoHasta, Instant ahora);

  @Modifying
  @Query("update Usuario u set u.intentosFallidos = 0, u.bloqueadoHasta = null, u.ultimoAccesoEn = :ahora where u.id = :id")
  int registrarAccesoExitoso(UUID id, Instant ahora);
}
