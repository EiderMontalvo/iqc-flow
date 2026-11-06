package pe.com.iqc.flow.catalogos.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.catalogos.domain.EsquemaAcreditacion;

public interface EsquemaAcreditacionRepository extends JpaRepository<EsquemaAcreditacion, String> {
  List<EsquemaAcreditacion> findByActivoTrueOrderByNombreAsc();
}
