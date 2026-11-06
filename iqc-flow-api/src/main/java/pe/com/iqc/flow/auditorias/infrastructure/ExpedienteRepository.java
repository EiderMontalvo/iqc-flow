package pe.com.iqc.flow.auditorias.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.auditorias.domain.Expediente;

public interface ExpedienteRepository extends JpaRepository<Expediente, UUID>, JpaSpecificationExecutor<Expediente> {
  Optional<Expediente> findByOrganizacionIdAndFuenteLegadoAndIdLegado(UUID organizacionId, String fuenteLegado,
      String idLegado);

  Optional<Expediente> findByIdAndOrganizacionId(UUID id, UUID organizacionId);

  /**[estado, cantidad] de los expedientes de la organización*/
  @Query("select e.estado, count(e) from Expediente e where e.organizacionId = :organizacionId group by e.estado")
  List<Object[]> contarPorEstado(UUID organizacionId);
}
