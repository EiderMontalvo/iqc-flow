package pe.com.iqc.flow.identidad.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**datos para crear el primer usuario MASTER (iqc.master-inicial.*), normalmente desde variables de entorno*/
@ConfigurationProperties("iqc.master-inicial")
public record PropiedadesMasterInicial(
    boolean habilitado,
    String organizacionCodigo,
    String organizacionNombre,
    String correo,
    String nombre,
    String contrasena) {

  @Override
  public String toString() {
    return "PropiedadesMasterInicial[habilitado=" + habilitado + ", organizacionCodigo=" + organizacionCodigo
        + ", correo=" + correo + ", contrasena=****]";
  }
}
