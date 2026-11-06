package pe.com.iqc.flow.auditorias.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.auditorias.domain.Auditoria;
import pe.com.iqc.flow.auditorias.domain.EstadoAuditoria;
import pe.com.iqc.flow.auditorias.domain.TipoAuditoria;

public interface AuditoriaRepository extends JpaRepository<Auditoria, UUID> {
  List<Auditoria> findByExpedienteIdOrderByCreadoEnAsc(UUID expedienteId);

  Optional<Auditoria> findByIdAndExpedienteId(UUID id, UUID expedienteId);

  boolean existsByExpedienteIdAndTipoAndEstadoNot(UUID expedienteId, TipoAuditoria tipo, EstadoAuditoria estado);

  /**vigilancias pendientes de la organización con fecha límite en el rango*/
  @Query("""
      select a from Auditoria a, Expediente e
      where a.expedienteId = e.id and e.organizacionId = :organizacionId
        and a.tipo in :tipos and a.estado in :pendientes and a.fechaLimite between :desde and :hasta
      order by a.fechaLimite
      """)
  List<Auditoria> pendientesEntre(UUID organizacionId, Collection<TipoAuditoria> tipos,
      Collection<EstadoAuditoria> pendientes, LocalDate desde, LocalDate hasta);

  /**auditorías por programar o programadas que aún no tienen ningún auditor asignado*/
  @Query("""
      select a from Auditoria a, Expediente e
      where a.expedienteId = e.id and e.organizacionId = :organizacionId
        and a.estado in :pendientes and a.equipo is empty
      order by a.fechaLimite nulls last, a.fechaInicioPlan nulls last
      """)
  List<Auditoria> sinEquipo(UUID organizacionId, Collection<EstadoAuditoria> pendientes);
}
