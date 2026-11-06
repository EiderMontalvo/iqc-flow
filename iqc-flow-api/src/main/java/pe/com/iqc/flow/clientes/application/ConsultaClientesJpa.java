package pe.com.iqc.flow.clientes.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.clientes.domain.Cliente;
import pe.com.iqc.flow.clientes.domain.EstadoCliente;
import pe.com.iqc.flow.clientes.infrastructure.ClienteRepository;
import pe.com.iqc.flow.clientes.infrastructure.ContactoClienteRepository;

@Service
class ConsultaClientesJpa implements ConsultaClientes {
  private static final Set<EstadoCliente> VIGENTES = Set.of(EstadoCliente.PROSPECTO, EstadoCliente.ACTIVO);

  private final ClienteRepository clientes;
  private final ContactoClienteRepository contactos;

  ConsultaClientesJpa(ClienteRepository clientes, ContactoClienteRepository contactos) {
    this.clientes = clientes;
    this.contactos = contactos;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClienteReferencia> buscar(UUID organizacionId, UUID clienteId) {
    return clientes.findByIdAndOrganizacionId(clienteId, organizacionId).map(ConsultaClientesJpa::referencia);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, ClienteReferencia> buscarPorIds(UUID organizacionId, Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return clientes.findAllById(ids).stream()
        .filter(cliente -> cliente.getOrganizacionId().equals(organizacionId))
        .map(ConsultaClientesJpa::referencia)
        .collect(Collectors.toMap(ClienteReferencia::id, Function.identity()));
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> idsDeResponsable(UUID organizacionId, UUID responsableId) {
    return Set.copyOf(clientes.idsDeResponsable(organizacionId, responsableId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClienteReferencia> listarVigentes(UUID organizacionId, UUID responsableIdOpcional) {
    return clientes.listarVigentes(organizacionId, VIGENTES).stream()
        .filter(cliente -> responsableIdOpcional == null || responsableIdOpcional.equals(cliente.getResponsableUsuarioId()))
        .map(ConsultaClientesJpa::referencia)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean contactoPerteneceACliente(UUID clienteId, UUID contactoId) {
    return contactos.findByIdAndClienteId(contactoId, clienteId).isPresent();
  }

  private static ClienteReferencia referencia(Cliente cliente) {
    return new ClienteReferencia(cliente.getId(), cliente.getCodigo(), cliente.getRazonSocial(),
        cliente.getResponsableUsuarioId(), cliente.estaArchivado(), cliente.getCreadoEn());
  }
}
