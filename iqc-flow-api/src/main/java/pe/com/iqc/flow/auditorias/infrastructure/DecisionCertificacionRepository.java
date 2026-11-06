package pe.com.iqc.flow.auditorias.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.auditorias.domain.DecisionCertificacion;

public interface DecisionCertificacionRepository extends JpaRepository<DecisionCertificacion, UUID> {
  List<DecisionCertificacion> findByExpedienteIdOrderByFechaDecisionAscCreadoEnAsc(UUID expedienteId);

  Optional<DecisionCertificacion> findFirstByExpedienteIdOrderByCreadoEnDesc(UUID expedienteId);
}
