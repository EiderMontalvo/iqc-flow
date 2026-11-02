package pe.com.iqc.flow.compartido.domain;

/**error de negocio con un código estable que el frontend puede interpretar. */
public abstract class ExcepcionNegocio extends RuntimeException {
  private final String codigo;

  protected ExcepcionNegocio(String codigo, String mensaje) {
    super(mensaje);
    this.codigo = codigo;
  }

  public String codigo() { return codigo; }
}
