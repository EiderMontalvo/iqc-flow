package pe.com.iqc.flow.clientes.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.clientes.domain.Cliente;
import pe.com.iqc.flow.clientes.domain.EstadoCliente;
import pe.com.iqc.flow.clientes.domain.TipoDocumento;

public interface ClienteRepository extends JpaRepository<Cliente, UUID>, JpaSpecificationExecutor<Cliente> {
  Optional<Cliente> findByIdAndOrganizacionId(UUID id, UUID organizacionId);

  /**cliente vigente (no archivado) con ese documento, si existe. */
  @Query("""
      select c from Cliente c
      where c.organizacionId = :organizacionId and c.codigoPais = :codigoPais
        and c.tipoDocumento = :tipoDocumento and c.numeroDocumento = :numeroDocumento
        and c.archivadoEn is null
      """)
  Optional<Cliente> buscarVigentePorDocumento(UUID organizacionId, String codigoPais, TipoDocumento tipoDocumento,
      String numeroDocumento);

  @Query("""
      select c.id from Cliente c
      where c.organizacionId = :organizacionId and c.responsableUsuarioId = :responsableId and c.archivadoEn is null
      """)
  List<UUID> idsDeResponsable(UUID organizacionId, UUID responsableId);

  @Query("""
      select c from Cliente c
      where c.organizacionId = :organizacionId and c.archivadoEn is null and c.estado in :estados
      order by c.razonSocial
      """)
  List<Cliente> listarVigentes(UUID organizacionId, Collection<EstadoCliente> estados);
}
