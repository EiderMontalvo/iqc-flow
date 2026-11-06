package pe.com.iqc.flow.seguimientos.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.seguimientos.domain.TipoTarea;

public interface TipoTareaRepository extends JpaRepository<TipoTarea, String> {
  List<TipoTarea> findByActivoTrueOrderByOrdenAsc();
}
