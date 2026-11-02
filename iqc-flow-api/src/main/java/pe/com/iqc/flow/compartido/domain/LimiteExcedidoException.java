package pe.com.iqc.flow.compartido.domain;

public class LimiteExcedidoException extends ExcepcionNegocio {
  public LimiteExcedidoException(String codigo, String mensaje) { super(codigo, mensaje); }
}
