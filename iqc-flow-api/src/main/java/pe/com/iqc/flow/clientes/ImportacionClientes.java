package pe.com.iqc.flow.clientes;

import java.util.UUID;

/**
 *API pública para importar clientes de un sistema anterior. Es idempotente: si ya existe un cliente con ese
 *origen (fuente + id) lo devuelve sin modificarlo; si existe uno con el mismo documento, le vincula el origen
 */
public interface ImportacionClientes {
  Resultado importar(ClienteLegado cliente);

  /**
   *estado: ACTIVO, INACTIVO o PROSPECTO. Un cliente sin documento válido no puede quedar ACTIVO y queda como
   *PROSPECTO con la observación correspondiente
   */
  record ClienteLegado(UUID organizacionId, String fuente, String idLegado, String razonSocial, String codigoPais,
      String numeroDocumento, String direccion, String notas, String estado, UUID responsableId) {
  }

  record Resultado(UUID clienteId, boolean creado, boolean conDocumento, String observacion) {
  }
}
