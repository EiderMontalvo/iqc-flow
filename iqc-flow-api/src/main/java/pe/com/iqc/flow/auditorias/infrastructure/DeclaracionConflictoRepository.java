package pe.com.iqc.flow.auditorias.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.auditorias.domain.DeclaracionConflicto;

public interface DeclaracionConflictoRepository extends JpaRepository<DeclaracionConflicto, UUID> {
  List<DeclaracionConflicto> findByAuditoriaId(UUID auditoriaId);

  List<DeclaracionConflicto> findByAuditoriaIdIn(Collection<UUID> auditoriaIds);

  Optional<DeclaracionConflicto> findByAuditoriaIdAndAuditorId(UUID auditoriaId, UUID auditorId);
}
