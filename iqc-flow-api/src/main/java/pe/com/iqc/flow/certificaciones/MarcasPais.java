package pe.com.iqc.flow.certificaciones;

import java.util.UUID;

/**
 *API pública de las marcas por país (IQC Perú, IQC Ecuador, IQC Colombia...): nombre visible, membrete y dirección
 *de consulta del QR. La usarán también las plantillas de documentos (cartas, certificados)
 */
public interface MarcasPais {
  /**marca configurada del país; si no hay, la predeterminada del país; si tampoco, la del país de la organización*/
  MarcaVigente marca(UUID organizacionId, String codigoPais);

  /**predeterminada: aún no configurada por MASTER (se usan los valores de fábrica)*/
  record MarcaVigente(String codigoPais, String nombre, String urlConsulta, String sitioWeb, String correo,
      String telefono, String direccion, UUID logoDocumentoId, boolean predeterminada) {

    /**dirección completa que lleva el QR*/
    public String urlVerificacion(String token) { return urlConsulta + token; }
  }
}
