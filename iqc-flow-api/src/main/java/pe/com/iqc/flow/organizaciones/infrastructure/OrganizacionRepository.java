package pe.com.iqc.flow.organizaciones.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.organizaciones.domain.Organizacion;

public interface OrganizacionRepository extends JpaRepository<Organizacion, UUID> {
  Optional<Organizacion> findByCodigo(String codigo);
}
