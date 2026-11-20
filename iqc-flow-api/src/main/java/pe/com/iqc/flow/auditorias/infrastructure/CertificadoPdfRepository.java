package pe.com.iqc.flow.auditorias.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.auditorias.domain.CertificadoPdf;

public interface CertificadoPdfRepository extends JpaRepository<CertificadoPdf, UUID> {
  List<CertificadoPdf> findByCertificadoIdOrderByNumeroDesc(UUID certificadoId);

  int countByCertificadoId(UUID certificadoId);
}
