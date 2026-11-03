package pe.com.iqc.flow.notificaciones.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.notificaciones.domain.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {
  boolean existsByClaveDeduplicacion(String claveDeduplicacion);

  Optional<Notificacion> findByIdAndDestinatarioUsuarioId(UUID id, UUID destinatarioUsuarioId);

  @Query("""
      select n from Notificacion n
      where n.destinatarioUsuarioId = :destinatarioId and (:soloNoLeidas = false or n.leidaEn is null)
      order by n.creadoEn desc
      """)
  Page<Notificacion> listar(UUID destinatarioId, boolean soloNoLeidas, Pageable paginacion);

  long countByDestinatarioUsuarioIdAndLeidaEnIsNull(UUID destinatarioUsuarioId);

  @Modifying
  @Query("update Notificacion n set n.leidaEn = :ahora where n.destinatarioUsuarioId = :destinatarioId and n.leidaEn is null")
  int marcarTodasLeidas(UUID destinatarioId, Instant ahora);
}
