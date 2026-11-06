package pe.com.iqc.flow.auditorias;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 *API pública para importar el histórico de certificaciones de un sistema anterior. Cada expediente histórico
 *agrupa los certificados de un cliente emitidos juntos (mismo esquema, fecha y ciclo). Es idempotente: un
 *expediente ya importado (misma fuente e id) se devuelve sin cambios
 */
public interface ImportacionCertificaciones {
  ResultadoExpediente importar(ExpedienteLegado expediente);

  /**
   *esquema: INACAL, SAE o IQC_GLOBAL; tipoServicio: CERTIFICACION_INICIAL, RECERTIFICACION o AMPLIACION.
   *Los límites de vigilancia nulos no crean auditoría; con programarTareas se crean las tareas de las futuras
   */
  record ExpedienteLegado(UUID organizacionId, String fuente, String idLegado, UUID clienteId, String esquema,
      String tipoServicio, String alcance, LocalDate fechaCertificacion, LocalDate fechaExpiracion,
      LocalDate limiteVigilancia1, LocalDate limiteVigilancia2, boolean programarTareas, UUID responsableId,
      List<CertificadoLegado> certificados) {
  }

  /**estado: EMITIDO, SUSPENDIDO o RETIRADO; anioDecision nulo para los certificados de India*/
  record CertificadoLegado(String idLegado, String norma, String edicion, String codigo, String estado,
      int revisionAlcance, int ciclo, Integer anioDecision, LocalDate fechaOtorgamiento, LocalDate fechaEmision,
      LocalDate fechaExpiracion, String urlDocumento, boolean emitidoPorIndia, String alcance, List<QrLegado> qrs) {
  }

  /**QR ya impreso: la consulta pública abre consultas.iqcperu.com/{encriptacionId}*/
  record QrLegado(String idQr, String encriptacionId, boolean activo, Instant registradoEn) {
  }

  record ResultadoExpediente(UUID expedienteId, boolean creado, int certificados, int qrs, int vigilancias, int tareas) {
  }
}
