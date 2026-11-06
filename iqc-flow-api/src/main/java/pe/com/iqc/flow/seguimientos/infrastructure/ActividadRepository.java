package pe.com.iqc.flow.seguimientos.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.seguimientos.domain.Actividad;

public interface ActividadRepository extends JpaRepository<Actividad, UUID> {
  Page<Actividad> findByOrganizacionIdAndClienteIdOrderByOcurridaEnDesc(UUID organizacionId, UUID clienteId,
      Pageable paginacion);

  /**fecha de la última actividad de cada cliente de la organización: [clienteId, máxima ocurrida_en]*/
  @Query("select a.clienteId, max(a.ocurridaEn) from Actividad a where a.organizacionId = :organizacionId group by a.clienteId")
  List<Object[]> ultimaActividadPorCliente(UUID organizacionId);

  boolean existsByClienteIdAndOcurridaEnAfter(UUID clienteId, Instant desde);
}
