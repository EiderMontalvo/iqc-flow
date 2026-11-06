package pe.com.iqc.flow.documentos.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

/**almacén en disco local. Escribe en un archivo temporal y lo mueve, para no dejar archivos a medias*/
@Component
class AlmacenArchivosLocal implements AlmacenArchivos {
  private final Path raiz;

  AlmacenArchivosLocal(PropiedadesDocumentos propiedades) {
    this.raiz = Path.of(propiedades.directorio()).toAbsolutePath().normalize();
  }

  @Override
  public void guardar(String ruta, byte[] contenido) throws IOException {
    Path destino = resolver(ruta);
    Files.createDirectories(destino.getParent());
    Path temporal = Files.createTempFile(destino.getParent(), ".subida-", ".tmp");
    try {
      Files.write(temporal, contenido);
      Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temporal);
    }
  }

  @Override
  public InputStream abrir(String ruta) throws IOException { return Files.newInputStream(resolver(ruta)); }

  @Override
  public void eliminar(String ruta) throws IOException { Files.deleteIfExists(resolver(ruta)); }

  /**impide salir de la carpeta raíz aunque la ruta guardada estuviera manipulada*/
  private Path resolver(String ruta) {
    Path destino = raiz.resolve(ruta).normalize();
    if (!destino.startsWith(raiz)) throw new IllegalArgumentException("Ruta fuera del almacén de documentos");
    return destino;
  }
}
