package pe.com.iqc.flow.auditores.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.com.iqc.flow.auditores.domain.Auditor;

public interface AuditorRepository extends JpaRepository<Auditor, UUID>, JpaSpecificationExecutor<Auditor> {
  Optional<Auditor> findByIdAndOrganizacionId(UUID id, UUID organizacionId);

  Optional<Auditor> findByUsuarioId(UUID usuarioId);
}
