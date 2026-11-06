package pe.com.iqc.flow.auditores;

/**
 *roles dentro de un equipo auditor (ISO/IEC 17021-1). El experto técnico (T.E en el cuadro de control)
 *aporta conocimiento especializado y suele participar en la Etapa 2. Todos cuentan para la regla de
 *imparcialidad: quien participó en una auditoría del cliente no puede decidir su certificación
 */
public enum RolAuditor {
  LIDER,
  AUDITOR,
  EXPERTO_TECNICO,
  OBSERVADOR,
  EN_ENTRENAMIENTO
}
