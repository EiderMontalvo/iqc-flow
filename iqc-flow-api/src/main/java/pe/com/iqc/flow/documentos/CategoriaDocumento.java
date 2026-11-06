package pe.com.iqc.flow.documentos;

/**para qué se adjunta el documento; cada módulo exige la categoría que corresponde a su uso*/
public enum CategoriaDocumento {
  /**acta del Comité de Imparcialidad cuando el decisor tiene conflicto (DDC-PRO-003)*/
  ACTA_COMITE_IMPARCIALIDAD,
  /**declaración de conflicto de interés firmada por un auditor externo*/
  DECLARACION_CONFLICTO,
  /**Anexo 1 de DDC-PRO-003, obligatorio antes de suspender o retirar*/
  CHECKLIST_SUSPENSION,
  /**traducción al español del certificado emitido por India (IQC Global)*/
  TRADUCCION_CERTIFICADO,
  OTRO
}
