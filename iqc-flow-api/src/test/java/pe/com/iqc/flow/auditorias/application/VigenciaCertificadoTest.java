package pe.com.iqc.flow.auditorias.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.Vigencia;

/**la vigencia depende solo de la fecha de expiración, no del estado administrativo (ADR-037)*/
class VigenciaCertificadoTest {
  private static final LocalDate HOY = LocalDate.of(2026, 11, 20);

  @Test
  void clasificaPorLaFechaDeExpiracion() {
    assertThat(ServicioPanelCertificados.vigencia(null, HOY, 90)).isEqualTo(Vigencia.SIN_FECHA);
    assertThat(ServicioPanelCertificados.vigencia(HOY.minusDays(1), HOY, 90)).isEqualTo(Vigencia.VENCIDO);
    assertThat(ServicioPanelCertificados.vigencia(HOY, HOY, 90)).isEqualTo(Vigencia.POR_VENCER);
    assertThat(ServicioPanelCertificados.vigencia(HOY.plusDays(90), HOY, 90)).isEqualTo(Vigencia.POR_VENCER);
    assertThat(ServicioPanelCertificados.vigencia(HOY.plusDays(91), HOY, 90)).isEqualTo(Vigencia.VIGENTE);
  }
}
