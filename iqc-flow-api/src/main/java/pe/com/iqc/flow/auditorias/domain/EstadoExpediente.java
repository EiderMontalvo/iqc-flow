package pe.com.iqc.flow.auditorias.domain;

/**
 *estados del expediente. Hay dos recorridos según quién decide la certificación (ver FlujoExpediente):
 *- IQC Global (decide India): solicitud → proposal → paquetes de Fase I y II → observado o certificado → traducción.
 *- INACAL y SAE (decide IQC Perú, DDC-PRO-003): planificación → etapas → verificación → decisión → emisión.
 *Los estados EN_EMISION, RECHAZADO, EMITIDO y ENTREGADO los asigna el sistema (decisión, firma, entrega)
 */
public enum EstadoExpediente {
  // Recorrido IQC Global
  SOLICITUD_ENVIADA,
  PROPOSAL_RECIBIDO,
  PROPOSAL_FIRMADO,
  PAQUETE_FASE1_RECIBIDO,
  PAQUETE_FASE1_ENVIADO,
  PAQUETE_FASE2_RECIBIDO,
  PAQUETE_FASE2_ENVIADO,
  OBSERVADO,
  CERTIFICADO_RECIBIDO,
  TRADUCIDO,
  // Recorrido INACAL / SAE
  EN_PLANIFICACION,
  ETAPA1_REALIZADA,
  ETAPA2_REALIZADA,
  EXPEDIENTE_VERIFICADO,
  EN_DECISION,
  /**decisión favorable registrada: certificados en borrador, revisión y firma*/
  EN_EMISION,
  /**todos los certificados del expediente están emitidos*/
  EMITIDO,
  /**decisión de rechazo (estado final)*/
  RECHAZADO,
  // Comunes
  ENTREGADO,
  CANCELADO
}
