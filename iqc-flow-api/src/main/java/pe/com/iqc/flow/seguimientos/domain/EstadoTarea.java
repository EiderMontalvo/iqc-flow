package pe.com.iqc.flow.seguimientos.domain;

/**PENDIENTE, EN_CURSO y EN_ESPERA son estados abiertos; COMPLETADA y CANCELADA cierran la tarea*/
public enum EstadoTarea {
  PENDIENTE,
  EN_CURSO,
  EN_ESPERA,
  COMPLETADA,
  CANCELADA
}
