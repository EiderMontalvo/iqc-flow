package pe.com.iqc.flow.catalogos.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.catalogos.domain.Pais;

public interface PaisRepository extends JpaRepository<Pais, String> {
  List<Pais> findByActivoTrueOrderByNombreAsc();
}
