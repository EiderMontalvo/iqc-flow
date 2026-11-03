package pe.com.iqc.flow.clientes.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.clientes.domain.DireccionCliente;
import pe.com.iqc.flow.clientes.domain.TipoDireccion;

public interface DireccionClienteRepository extends JpaRepository<DireccionCliente, UUID> {
  List<DireccionCliente> findByClienteIdOrderByTipoAscNombreAsc(UUID clienteId);

  Optional<DireccionCliente> findByIdAndClienteId(UUID id, UUID clienteId);

  boolean existsByClienteIdAndTipoAndIdNot(UUID clienteId, TipoDireccion tipo, UUID exceptoId);

  boolean existsByClienteIdAndTipo(UUID clienteId, TipoDireccion tipo);
}
