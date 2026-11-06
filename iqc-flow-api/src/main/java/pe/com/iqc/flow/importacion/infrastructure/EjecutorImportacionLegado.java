package pe.com.iqc.flow.importacion.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.importacion.application.ServicioImportacionLegado;
import pe.com.iqc.flow.importacion.application.ServicioImportacionLegado.Resultado;

/**
 *ejecuta la importación al arrancar si se indica iqc.importacion-legado.archivo (ver README). Escribe el informe
 *y, por defecto, termina la aplicación. Nunca se conecta a la base de producción: solo lee el volcado
 */
@Component
@ConditionalOnProperty("iqc.importacion-legado.archivo")
class EjecutorImportacionLegado implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(EjecutorImportacionLegado.class);

  private final ServicioImportacionLegado servicio;
  private final PropiedadesImportacion propiedades;
  private final ConfigurableApplicationContext contexto;

  EjecutorImportacionLegado(ServicioImportacionLegado servicio, PropiedadesImportacion propiedades,
      ConfigurableApplicationContext contexto) {
    this.servicio = servicio;
    this.propiedades = propiedades;
    this.contexto = contexto;
  }

  @Override
  public void run(ApplicationArguments argumentos) throws IOException {
    Path volcado = Path.of(propiedades.archivo());
    Path informe = propiedades.informe() != null ? Path.of(propiedades.informe())
        : volcado.resolveSibling(volcado.getFileName() + "." + propiedades.modo().name().toLowerCase() + ".md");
    int codigo = 0;
    try {
      Resultado r = servicio.ejecutar(volcado, propiedades.modo() == PropiedadesImportacion.Modo.APLICAR,
          propiedades.organizacion(), propiedades.responsable());
      Files.writeString(informe, r.informe(), StandardCharsets.UTF_8);
      log.info("Importación ({}) terminada. Informe: {}", propiedades.modo(), informe.toAbsolutePath());
    } catch (RuntimeException e) {
      log.error("La importación falló y no se escribió nada en la base de datos", e);
      codigo = 1;
    }
    if (propiedades.salir()) {
      int salida = codigo;
      System.exit(SpringApplication.exit(contexto, () -> salida));
    }
  }
}
