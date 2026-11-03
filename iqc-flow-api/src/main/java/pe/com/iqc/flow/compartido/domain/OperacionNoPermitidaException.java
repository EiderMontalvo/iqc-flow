package pe.com.iqc.flow.compartido.domain;

/**el usuario está autenticado pero su situación actual no le permite la operación*/
public class OperacionNoPermitidaException extends ExcepcionNegocio {
  public OperacionNoPermitidaException(String codigo, String mensaje) { super(codigo, mensaje); }
}
