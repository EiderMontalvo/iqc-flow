package pe.com.iqc.flow.documentos.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.documentos.domain.Documento;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {
  Optional<Documento> findByIdAndOrganizacionId(UUID id, UUID organizacionId);
}
