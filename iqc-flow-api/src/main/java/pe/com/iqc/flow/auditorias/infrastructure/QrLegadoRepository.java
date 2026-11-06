package pe.com.iqc.flow.auditorias.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.auditorias.domain.QrLegado;

public interface QrLegadoRepository extends JpaRepository<QrLegado, String> {
  List<QrLegado> findByCertificadoId(UUID certificadoId);
}
