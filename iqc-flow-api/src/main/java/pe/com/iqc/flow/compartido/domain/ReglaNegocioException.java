package pe.com.iqc.flow.compartido.domain;

/**la solicitud es válida en forma pero infringe una regla de negocio*/
public class ReglaNegocioException extends ExcepcionNegocio {
  public ReglaNegocioException(String codigo, String mensaje) { super(codigo, mensaje); }
}
