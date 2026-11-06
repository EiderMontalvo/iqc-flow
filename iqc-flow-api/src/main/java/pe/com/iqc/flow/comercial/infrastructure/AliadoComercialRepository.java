package pe.com.iqc.flow.comercial.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.comercial.domain.AliadoComercial;

public interface AliadoComercialRepository extends JpaRepository<AliadoComercial, UUID> {
  List<AliadoComercial> findByOrganizacionIdOrderByNombreAsc(UUID organizacionId);

  Optional<AliadoComercial> findByIdAndOrganizacionId(UUID id, UUID organizacionId);

  boolean existsByOrganizacionIdAndCodigoAndIdNot(UUID organizacionId, String codigo, UUID id);

  boolean existsByOrganizacionIdAndCodigo(UUID organizacionId, String codigo);
}
