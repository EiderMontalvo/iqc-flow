package pe.com.iqc.flow.cotizaciones.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.cotizaciones.domain.Cotizacion;

public interface CotizacionRepository extends JpaRepository<Cotizacion, UUID>, JpaSpecificationExecutor<Cotizacion> {
  Optional<Cotizacion> findByIdAndOrganizacionId(UUID id, UUID organizacionId);

  /**enviadas sin respuesta desde antes de la fecha límite y sin recordatorio todavía (para la tarea automática)*/
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select c from Cotizacion c where c.estado = pe.com.iqc.flow.cotizaciones.domain.EstadoCotizacion.ENVIADA
        and c.recordatorioEn is null and c.enviadaEn <= :limite order by c.enviadaEn""")
  List<Cotizacion> bloquearSinRespuesta(LocalDate limite, Pageable pagina);
}
