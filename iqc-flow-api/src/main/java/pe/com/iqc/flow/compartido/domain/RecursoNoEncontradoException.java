package pe.com.iqc.flow.compartido.domain;

public class RecursoNoEncontradoException extends ExcepcionNegocio {
  public RecursoNoEncontradoException(String recurso, Object id) {
    super("RECURSO_NO_ENCONTRADO", recurso + " no encontrado: " + id);
  }
}
