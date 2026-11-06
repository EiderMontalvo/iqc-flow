package pe.com.iqc.flow.clientes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**API pública de clientes para otros módulos (solo lectura, sin controles de permisos: los aplica quien la usa)*/
public interface ConsultaClientes {
  Optional<ClienteReferencia> buscar(UUID organizacionId, UUID clienteId);

  Map<UUID, ClienteReferencia> buscarPorIds(UUID organizacionId, Collection<UUID> ids);

  /**clientes no archivados de los que el usuario es responsable*/
  Set<UUID> idsDeResponsable(UUID organizacionId, UUID responsableId);

  /**prospectos y clientes activos no archivados; con responsable, solo los suyos*/
  List<ClienteReferencia> listarVigentes(UUID organizacionId, UUID responsableIdOpcional);

  boolean contactoPerteneceACliente(UUID clienteId, UUID contactoId);

  /**direcciones (fiscal, sedes, correspondencia) del cliente, indexadas por id*/
  Map<UUID, DireccionReferencia> direccionesDeCliente(UUID clienteId);

  record DireccionReferencia(UUID id, String tipo, String nombre, String direccion, String codigoPais) {
  }
}
