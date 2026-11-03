package pe.com.iqc.flow.clientes.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.clientes.domain.ContactoCliente;

public interface ContactoClienteRepository extends JpaRepository<ContactoCliente, UUID> {
  List<ContactoCliente> findByClienteIdOrderByEsPrincipalDescNombreCompletoAsc(UUID clienteId);

  Optional<ContactoCliente> findByIdAndClienteId(UUID id, UUID clienteId);

  /**quita la marca de principal a los demás contactos del cliente antes de asignarla a otro. */
  @Modifying(flushAutomatically = true)
  @Query("update ContactoCliente c set c.esPrincipal = false where c.clienteId = :clienteId and c.id <> :exceptoId and c.esPrincipal = true")
  int quitarPrincipal(UUID clienteId, UUID exceptoId);
}
