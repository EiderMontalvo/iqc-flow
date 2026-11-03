package pe.com.iqc.flow.compartido.domain;

/**conflicto con el estado actual del recurso (duplicados, versión desactualizada)*/
public class ConflictoException extends ExcepcionNegocio {
  public ConflictoException(String codigo, String mensaje) { super(codigo, mensaje); }
}
