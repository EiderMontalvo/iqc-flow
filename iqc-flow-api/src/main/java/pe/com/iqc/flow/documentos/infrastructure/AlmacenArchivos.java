package pe.com.iqc.flow.documentos.infrastructure;

import java.io.IOException;
import java.io.InputStream;

/**
 *dónde se guarda el contenido de los documentos. Hoy en disco local; más adelante puede implementarse sobre
 *un almacén en la nube sin cambiar el resto del módulo (ADR-033). La ruta la genera el sistema, nunca el usuario
 */
public interface AlmacenArchivos {
  void guardar(String ruta, byte[] contenido) throws IOException;

  InputStream abrir(String ruta) throws IOException;

  void eliminar(String ruta) throws IOException;
}
