package pe.com.iqc.flow.seguimientos.domain;

/**distingue las tareas creadas a mano de las generadas por automatizaciones o propuestas por la IA*/
public enum OrigenTarea {
  MANUAL,
  AUTOMATIZACION,
  IA
}
