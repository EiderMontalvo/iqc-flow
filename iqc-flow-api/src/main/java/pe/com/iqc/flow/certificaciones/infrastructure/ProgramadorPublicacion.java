package pe.com.iqc.flow.certificaciones.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.certificaciones.application.PublicadorCertificados;

/**busca cambios para publicar cada iqc.consultas.intervalo (1 minuto por defecto). Las pruebas lo desactivan*/
@Component
class ProgramadorPublicacion {
  private final PublicadorCertificados publicador;

  ProgramadorPublicacion(PublicadorCertificados publicador) { this.publicador = publicador; }

  @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${iqc.consultas.intervalo:PT1M}")
  void publicar() { publicador.publicar(false); }
}
