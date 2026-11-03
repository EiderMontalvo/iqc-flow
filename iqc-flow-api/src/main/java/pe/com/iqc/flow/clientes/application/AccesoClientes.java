package pe.com.iqc.flow.clientes.application;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.clientes.domain.Cliente;
import pe.com.iqc.flow.clientes.infrastructure.ClienteRepository;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;

/**
 *reglas de acceso a clientes (decisión D7): con clientes:leer se ven todos los clientes de la
 *organización; se edita solo si se es el responsable o se tiene clientes:escribir-todos.
 */
@Component
class AccesoClientes {
  static final String LEER = "clientes:leer";
  static final String ESCRIBIR = "clientes:escribir";
  static final String ESCRIBIR_TODOS = "clientes:escribir-todos";
  static final String REASIGNAR = "clientes:reasignar";
  static final String ARCHIVAR = "clientes:archivar";

  private final ClienteRepository clientes;
  private final ActorActual actorActual;

  AccesoClientes(ClienteRepository clientes, ActorActual actorActual) {
    this.clientes = clientes;
    this.actorActual = actorActual;
  }

  /**cliente de la organización del actor; los de otras organizaciones se tratan como inexistentes*/
  Cliente cargar(UUID id) {
    return clientes.findByIdAndOrganizacionId(id, actorActual.requerido().organizacionId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
  }

  Cliente cargarEditable(UUID id) {
    Cliente cliente = cargar(id);
    verificarEdicion(cliente);
    return cliente;
  }

  void verificarEdicion(Cliente cliente) {
    if (!esResponsableOEditorGeneral(cliente)) {
      throw new AccessDeniedException("Solo el responsable del cliente puede modificarlo");
    }
    verificarNoArchivado(cliente);
  }

  void verificarNoArchivado(Cliente cliente) {
    if (cliente.estaArchivado()) {
      throw new ReglaNegocioException("CLIENTE_ARCHIVADO", "El cliente está archivado. Restáuralo para modificarlo.");
    }
  }

  boolean puedeEditar(Cliente cliente) {
    return !cliente.estaArchivado() && actorActual.tienePermiso(ESCRIBIR) && esResponsableOEditorGeneral(cliente);
  }

  private boolean esResponsableOEditorGeneral(Cliente cliente) {
    return cliente.getResponsableUsuarioId().equals(actorActual.requerido().id())
        || actorActual.tienePermiso(ESCRIBIR_TODOS);
  }
}
