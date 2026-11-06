package pe.com.iqc.flow.auditorias.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.com.iqc.flow.auditorias.domain.EstadoExpediente.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FlujoExpedienteTest {
  @Test
  void elEstadoInicialDependeDeQuienDecide() {
    assertThat(FlujoExpediente.estadoInicial(true)).isEqualTo(SOLICITUD_ENVIADA);
    assertThat(FlujoExpediente.estadoInicial(false)).isEqualTo(EN_PLANIFICACION);
  }

  @Test
  void elFlujoConIndiaPermiteObservacionesYReenvio() {
    assertThat(FlujoExpediente.siguientes(PAQUETE_FASE2_ENVIADO, true, TipoServicio.CERTIFICACION_INICIAL))
        .containsExactlyInAnyOrder(OBSERVADO, CERTIFICADO_RECIBIDO, CANCELADO);
    assertThat(FlujoExpediente.siguientes(OBSERVADO, true, TipoServicio.CERTIFICACION_INICIAL))
        .contains(PAQUETE_FASE1_ENVIADO, PAQUETE_FASE2_ENVIADO);
  }

  @Test
  void unaRecertificacionPuedeOmitirLaEtapa1PeroUnaCertificacionInicialNo() {
    assertThat(FlujoExpediente.siguientes(EN_PLANIFICACION, false, TipoServicio.RECERTIFICACION)).contains(ETAPA2_REALIZADA);
    assertThatThrownBy(() -> FlujoExpediente.validar(EN_PLANIFICACION, ETAPA2_REALIZADA, false,
        TipoServicio.CERTIFICACION_INICIAL))
        .hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");
  }

  @Test
  void noSeMezclanLosRecorridosNiSeSaleDeUnEstadoFinal() {
    assertThatThrownBy(() -> FlujoExpediente.validar(EN_PLANIFICACION, PROPOSAL_RECIBIDO, false,
        TipoServicio.CERTIFICACION_INICIAL)).hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");
    assertThat(FlujoExpediente.siguientes(ENTREGADO, true, TipoServicio.CERTIFICACION_INICIAL)).isEmpty();
    assertThat(FlujoExpediente.siguientes(CANCELADO, false, TipoServicio.RECERTIFICACION)).isEmpty();
  }

  @Test
  void laDecisionLaFirmaYLaEntregaNoSonTransicionesManuales() {
    assertThat(FlujoExpediente.siguientes(EN_DECISION, false, TipoServicio.CERTIFICACION_INICIAL))
        .containsExactlyInAnyOrder(EN_EMISION, RECHAZADO, CANCELADO);
    assertThat(FlujoExpediente.siguientesManuales(EN_DECISION, false, TipoServicio.CERTIFICACION_INICIAL))
        .containsExactly(CANCELADO);
    assertThatThrownBy(() -> FlujoExpediente.validarManual(EN_DECISION, EN_EMISION, false,
        TipoServicio.CERTIFICACION_INICIAL)).hasFieldOrPropertyWithValue("codigo", "TRANSICION_AUTOMATICA");
    assertThatThrownBy(() -> FlujoExpediente.validarManual(TRADUCIDO, ENTREGADO, true,
        TipoServicio.CERTIFICACION_INICIAL)).hasFieldOrPropertyWithValue("codigo", "TRANSICION_AUTOMATICA");
    assertThat(FlujoExpediente.siguientes(RECHAZADO, false, TipoServicio.CERTIFICACION_INICIAL)).isEmpty();
  }

  @Test
  void elCicloDeTresAniosColocaLasVigilanciasEnLosAniversarios() {
    CicloCertificacion ciclo = CicloCertificacion.calcular(LocalDate.of(2024, 8, 26), 3, 2);

    assertThat(ciclo.limitesVigilancia()).containsExactly(LocalDate.of(2025, 8, 26), LocalDate.of(2026, 8, 26));
    assertThat(ciclo.expiracion()).isEqualTo(LocalDate.of(2027, 8, 26));
  }
}
