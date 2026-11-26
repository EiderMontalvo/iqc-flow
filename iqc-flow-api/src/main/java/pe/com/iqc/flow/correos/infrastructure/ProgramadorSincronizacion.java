package pe.com.iqc.flow.correos.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoCuenta;
import pe.com.iqc.flow.correos.application.SincronizadorCorreos;

/**revisa los buzones cada `iqc.correo.sincronizacion.intervalo` (3 minutos); se apaga con `iqc.correo.sincronizacion.habilitada: false`*/
@Component
class ProgramadorSincronizacion {
  private static final Logger LOG = LoggerFactory.getLogger(ProgramadorSincronizacion.class);

  private final SincronizadorCorreos sincronizador;
  private final PropiedadesCorreo propiedades;

  ProgramadorSincronizacion(SincronizadorCorreos sincronizador, PropiedadesCorreo propiedades) {
    this.sincronizador = sincronizador;
    this.propiedades = propiedades;
  }

  @Scheduled(fixedDelayString = "${iqc.correo.sincronizacion.intervalo:PT3M}", initialDelayString = "PT1M")
  void ejecutar() {
    if (!propiedades.sincronizacion().habilitada()) return;
    try {
      sincronizador.pasada().ifPresent(resultados -> {
        int nuevos = resultados.stream().mapToInt(ResultadoCuenta::nuevos).sum();
        if (nuevos > 0) LOG.info("Correos nuevos en la bandeja: {}", nuevos);
      });
    } catch (RuntimeException e) {
      LOG.warn("La revisión programada de los buzones falló ({})", e.getClass().getSimpleName());
    }
  }
}
