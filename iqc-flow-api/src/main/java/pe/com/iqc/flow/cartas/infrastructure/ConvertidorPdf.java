package pe.com.iqc.flow.cartas.infrastructure;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *convierte un .docx a PDF con LibreOffice sin interfaz (soffice --headless). Una conversión a la vez (cada una
 *levanta un proceso de ~200 MB) y con tiempo máximo; el perfil de LibreOffice se reutiliza para que arranque rápido
 */
@Component
public class ConvertidorPdf {
  private static final Logger log = LoggerFactory.getLogger(ConvertidorPdf.class);
  private final PropiedadesCartas propiedades;
  private final Semaphore turno = new Semaphore(1, true);

  public ConvertidorPdf(PropiedadesCartas propiedades) { this.propiedades = propiedades; }

  public boolean disponible() {
    String cmd = propiedades.soffice();
    if (cmd.contains(File.separator)) return Files.isExecutable(Path.of(cmd));
    String path = System.getenv("PATH");
    return path != null && Stream.of(path.split(File.pathSeparator)).anyMatch(d -> Files.isExecutable(Path.of(d, cmd)));
  }

  public byte[] aPdf(byte[] docx) {
    if (!disponible()) {
      throw new ReglaNegocioException("CONVERSOR_NO_DISPONIBLE", "El servidor no tiene LibreOffice para generar el PDF de la carta.");
    }
    boolean conTurno = false;
    Path carpeta = null;
    try {
      conTurno = turno.tryAcquire(propiedades.tiempoMaximo().toSeconds(), TimeUnit.SECONDS);
      if (!conTurno) throw new ReglaNegocioException("CONVERSOR_OCUPADO", "El generador de cartas está ocupado. Intenta de nuevo en unos segundos.");
      carpeta = Files.createTempDirectory("iqc-carta-");
      Path entrada = carpeta.resolve("carta.docx");
      Files.write(entrada, docx);
      // un perfil por usuario del sistema: si otro usuario (p. ej. root en pruebas) creó el suyo, no choca con el del servicio
      Path perfil = Path.of(System.getProperty("java.io.tmpdir"), "iqc-flow-libreoffice-" + System.getProperty("user.name"));
      Process proceso = new ProcessBuilder(List.of(propiedades.soffice(), "--headless", "--norestore", "--nologo", "--nolockcheck",
          "-env:UserInstallation=" + perfil.toUri(), "--convert-to", "pdf", "--outdir", carpeta.toString(), entrada.toString()))
          .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
      if (!proceso.waitFor(propiedades.tiempoMaximo().toSeconds(), TimeUnit.SECONDS)) {
        proceso.destroyForcibly();
        throw new ReglaNegocioException("CONVERSION_LENTA", "La carta tardó demasiado en generarse. Intenta de nuevo.");
      }
      Path pdf = carpeta.resolve("carta.pdf");
      if (proceso.exitValue() != 0 || !Files.exists(pdf)) {
        log.warn("LibreOffice terminó con código {} sin generar el PDF", proceso.exitValue());
        throw new ReglaNegocioException("CONVERSION_FALLIDA", "No se pudo generar el PDF de la carta.");
      }
      return Files.readAllBytes(pdf);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo convertir la carta a PDF", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReglaNegocioException("CONVERSION_INTERRUMPIDA", "Se interrumpió la generación de la carta.");
    } finally {
      if (conTurno) turno.release();
      borrar(carpeta);
    }
  }

  private static void borrar(Path carpeta) {
    if (carpeta == null) return;
    try (Stream<Path> s = Files.walk(carpeta)) {
      s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException e) {
      log.debug("No se pudo borrar {}", carpeta, e);
    }
  }
}
