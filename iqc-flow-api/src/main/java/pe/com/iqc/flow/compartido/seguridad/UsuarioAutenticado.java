package pe.com.iqc.flow.compartido.seguridad;

import java.util.UUID;

/**vista mínima del usuario autenticado que pueden usar todos los módulos)*/
public interface UsuarioAutenticado {
  UUID id();
  UUID organizacionId();
  String correo();
}
