package pe.com.iqc.flow.auditorias;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 *API pública (solo lectura, sin permisos: los aplica quien la usa) de los certificados que pueden mostrarse en la
 *consulta pública: los que ya fueron emitidos alguna vez (emitidos, suspendidos, retirados, cancelados o reemplazados)
 */
public interface CertificadosPublicables {
  /**todos los certificados publicables de todas las organizaciones, para la publicación periódica*/
  List<CertificadoPublicable> listar();

  Optional<CertificadoPublicable> buscar(UUID organizacionId, UUID certificadoId);

  /**certificados publicables de un cliente (para cartas y documentos dirigidos a él)*/
  List<CertificadoPublicable> porCliente(UUID organizacionId, UUID clienteId);

  /**
   *paisMarca: marca elegida a mano, o nula para usar la del país del cliente. qrsLegados: identificadores de QR ya
   *impresos (activos), el primero es el que se vuelve a imprimir
   */
  record CertificadoPublicable(UUID id, UUID organizacionId, UUID clienteId, String codigo, String esquema,
      String norma, String edicion, String alcance, String estado, LocalDate fechaOtorgamiento, LocalDate fechaEmision,
      LocalDate fechaExpiracion, String paisMarca, UUID tokenVerificacion, List<String> qrsLegados,
      UUID documentoPdfId, String urlDocumentoLegado) {

    /**identificador que lleva el QR: el ya impreso si existe (para no romperlo), o el token propio del certificado*/
    public String tokenQr() {
      return qrsLegados.isEmpty() ? tokenVerificacion.toString() : qrsLegados.get(0);
    }
  }
}
