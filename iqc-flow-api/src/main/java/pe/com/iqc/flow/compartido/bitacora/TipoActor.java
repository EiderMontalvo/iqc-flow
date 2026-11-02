package pe.com.iqc.flow.compartido.bitacora;

/**quién originó la operación. Distingue los cambios manuales de los automáticos o asistidos por IA. */
public enum TipoActor {
  USUARIO,
  SISTEMA,
  AUTOMATIZACION,
  ASISTENTE_IA
}
