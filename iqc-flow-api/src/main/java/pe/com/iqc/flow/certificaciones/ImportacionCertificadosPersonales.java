package pe.com.iqc.flow.certificaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 *API pública para importar certificados de personas de un sistema anterior. Reejecutable: un certificado ya
 *importado (misma fuente e id) no se duplica; la persona se reutiliza por id de origen o por documento
 */
public interface ImportacionCertificadosPersonales {
  record PersonaLegado(String fuente, String idLegado, String codigoPais, String tipoDocumento, String numeroDocumento,
      String nombres, String correo, String telefono) {
  }

  /**tokenPdf: el de la URL ya publicada (consultas.iqcperu.com/personal-pdf/{token}); nulo si no tenía*/
  record CertificadoLegado(String idLegado, String codigo, String curso, String normaReferencia, String normaAnio,
      String subtitulo, String descripcion, BigDecimal duracionHoras, String modalidad, LocalDate fechaInicio,
      LocalDate fechaFin, LocalDate fechaEmision, String entrenador, String urlDocumento, UUID tokenPdf, boolean anulado,
      String motivoAnulacion) {
  }

  /**valida el documento de identidad con las reglas del país; devuelve el motivo si no es válido, o null*/
  static String errorDocumento(String codigoPais, String tipoDocumento, String numeroDocumento) {
    try {
      pe.com.iqc.flow.certificaciones.domain.DocumentoPersona.validar(codigoPais, tipoDocumento, numeroDocumento);
      return null;
    } catch (RuntimeException e) {
      return e.getMessage();
    }
  }

  /**importa un certificado con su persona; devuelve false si ya estaba importado*/
  boolean importar(PersonaLegado persona, CertificadoLegado certificado, UUID organizacionId);
}
