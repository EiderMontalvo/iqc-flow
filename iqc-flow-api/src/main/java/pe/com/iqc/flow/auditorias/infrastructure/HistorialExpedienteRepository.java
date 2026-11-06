package pe.com.iqc.flow.auditorias.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.auditorias.domain.HistorialExpediente;

public interface HistorialExpedienteRepository extends JpaRepository<HistorialExpediente, UUID> {
  List<HistorialExpediente> findByExpedienteIdOrderByOcurridoEnAsc(UUID expedienteId);
}
