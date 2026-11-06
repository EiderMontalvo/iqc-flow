package pe.com.iqc.flow.auditorias.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.auditorias.domain.Certificado;

public interface CertificadoRepository extends JpaRepository<Certificado, UUID> {
  List<Certificado> findByExpedienteIdOrderByNormaCodigoAscCreadoEnAsc(UUID expedienteId);

  Optional<Certificado> findByIdAndExpedienteId(UUID id, UUID expedienteId);

  /**otro certificado activo (en preparación o vigente) con el mismo código para la misma norma*/
  @Query("""
      select count(c) > 0 from Certificado c
      where c.organizacionId = :organizacionId and c.codigo = :codigo and c.normaCodigo = :norma and c.id <> :excluido
        and c.estado not in (pe.com.iqc.flow.auditorias.domain.Certificado.Estado.REEMPLAZADO,
                             pe.com.iqc.flow.auditorias.domain.Certificado.Estado.CANCELADO)
      """)
  boolean existeCodigoActivo(UUID organizacionId, String codigo, String norma, UUID excluido);

  /**último certificado emitido (vigente o ya sustituido) del cliente para la norma y el esquema, para continuar su ciclo*/
  Optional<Certificado> findFirstByClienteIdAndEsquemaCodigoAndNormaCodigoAndEstadoInOrderByCicloDescRevisionAlcanceDescCreadoEnDesc(
      UUID clienteId, String esquemaCodigo, String normaCodigo, Collection<Certificado.Estado> estados);

  List<Certificado> findByClienteIdAndEsquemaCodigoAndNormaCodigoAndEstadoIn(UUID clienteId, String esquemaCodigo,
      String normaCodigo, Collection<Certificado.Estado> estados);
}
