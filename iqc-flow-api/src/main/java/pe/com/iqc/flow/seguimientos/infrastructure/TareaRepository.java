package pe.com.iqc.flow.seguimientos.infrastructure;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import pe.com.iqc.flow.seguimientos.domain.EstadoTarea;
import pe.com.iqc.flow.seguimientos.domain.TareaSeguimiento;

public interface TareaRepository extends JpaRepository<TareaSeguimiento, UUID>, JpaSpecificationExecutor<TareaSeguimiento> {
  Optional<TareaSeguimiento> findByIdAndOrganizacionId(UUID id, UUID organizacionId);

  Optional<TareaSeguimiento> findFirstByOrganizacionIdAndClaveDeduplicacionAndEstadoIn(UUID organizacionId,
      String claveDeduplicacion, Collection<EstadoTarea> estados);

  /**
   *tareas abiertas ya vencidas que aún no se notificaron. SKIP LOCKED: si dos servidores revisan a la vez,
   *cada tarea la procesa solo uno
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query("""
      select t from TareaSeguimiento t
      where t.estado in :abiertas and t.avisoVencimientoEn is null
        and (t.fechaLimite < :hoy or (t.fechaLimite = :hoy and t.horaLimite is not null and t.horaLimite < :ahora))
      order by t.fechaLimite
      """)
  List<TareaSeguimiento> bloquearVencidasSinAviso(Collection<EstadoTarea> abiertas, LocalDate hoy, LocalTime ahora,
      Pageable lote);

  @Query("""
      select count(t) from TareaSeguimiento t
      where t.organizacionId = :organizacionId and t.responsableUsuarioId = :responsableId and t.estado in :estados
        and t.fechaLimite between :desde and :hasta
      """)
  long contarPorFechas(UUID organizacionId, UUID responsableId, Collection<EstadoTarea> estados, LocalDate desde,
      LocalDate hasta);

  @Query("""
      select count(t) from TareaSeguimiento t
      where t.organizacionId = :organizacionId and t.responsableUsuarioId = :responsableId and t.estado in :abiertas
        and (t.fechaLimite < :hoy or (t.fechaLimite = :hoy and t.horaLimite is not null and t.horaLimite < :ahora))
      """)
  long contarVencidas(UUID organizacionId, UUID responsableId, Collection<EstadoTarea> abiertas, LocalDate hoy,
      LocalTime ahora);

  long countByOrganizacionIdAndResponsableUsuarioIdAndEstado(UUID organizacionId, UUID responsableId, EstadoTarea estado);

  /**clientes que tienen al menos una tarea abierta (no se consideran "sin movimiento")*/
  @Query("select distinct t.clienteId from TareaSeguimiento t where t.organizacionId = :organizacionId and t.clienteId is not null and t.estado in :abiertas")
  List<UUID> clientesConTareasAbiertas(UUID organizacionId, Collection<EstadoTarea> abiertas);
}
