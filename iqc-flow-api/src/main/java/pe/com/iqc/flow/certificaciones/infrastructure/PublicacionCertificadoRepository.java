package pe.com.iqc.flow.certificaciones.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.certificaciones.domain.PublicacionCertificado;

public interface PublicacionCertificadoRepository extends JpaRepository<PublicacionCertificado, UUID> {
  List<PublicacionCertificado> findByIntentosFallidosGreaterThanOrderByUltimoIntentoEnDesc(int intentos);

  long countByHuellaIsNotNull();
}
