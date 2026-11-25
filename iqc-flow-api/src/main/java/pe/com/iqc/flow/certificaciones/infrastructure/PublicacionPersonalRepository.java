package pe.com.iqc.flow.certificaciones.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.certificaciones.domain.PublicacionPersonal;

public interface PublicacionPersonalRepository extends JpaRepository<PublicacionPersonal, UUID> {
  long countByHuellaIsNotNull();

  long countByIntentosFallidosGreaterThan(int intentos);
}
