package pe.com.iqc.flow.clientes;

import java.time.Instant;
import java.util.UUID;

/**datos públicos de un cliente para otros módulos. tipoDocumento y numeroDocumento pueden ser nulos (prospectos)*/
public record ClienteReferencia(UUID id, String codigo, String razonSocial, UUID responsableId, boolean archivado,
    Instant creadoEn, String codigoPais, String tipoDocumento, String numeroDocumento) {
}
