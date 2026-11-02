package pe.com.iqc.flow.identidad.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.identidad.domain.Rol;

public interface RolRepository extends JpaRepository<Rol, UUID> {
  @Query("select r from Rol r where r.codigo in :codigos and (r.organizacionId is null or r.organizacionId = :organizacionId)")
  List<Rol> buscarDisponibles(Collection<String> codigos, UUID organizacionId);

  @EntityGraph(attributePaths = "permisos")
  @Query("select r from Rol r where r.organizacionId is null or r.organizacionId = :organizacionId order by r.nombre")
  List<Rol> listarDisponibles(UUID organizacionId);
}
