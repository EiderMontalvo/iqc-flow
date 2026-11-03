package pe.com.iqc.flow.seguimientos.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.seguimientos.domain.HistorialTarea;

public interface HistorialTareaRepository extends JpaRepository<HistorialTarea, UUID> {
  List<HistorialTarea> findByTareaIdOrderByOcurridoEnAsc(UUID tareaId);
}
