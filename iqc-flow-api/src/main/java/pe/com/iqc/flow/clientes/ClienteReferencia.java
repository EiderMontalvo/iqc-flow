package pe.com.iqc.flow.clientes;

import java.time.Instant;
import java.util.UUID;

/**datos públicos de un cliente para otros módulos*/
public record ClienteReferencia(UUID id, String codigo, String razonSocial, UUID responsableId, boolean archivado,
    Instant creadoEn) {
}
