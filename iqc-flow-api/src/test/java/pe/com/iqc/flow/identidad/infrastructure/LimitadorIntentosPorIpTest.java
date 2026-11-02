package pe.com.iqc.flow.identidad.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LimitadorIntentosPorIpTest {
  private final RelojMutable reloj = new RelojMutable(Instant.parse("2026-11-02T15:00:00Z"));
  private final LimitadorIntentosPorIp limitador = new LimitadorIntentosPorIp(
      new PropiedadesSeguridad(5, Duration.ofMinutes(15), 3, Duration.ofMinutes(15)), reloj);

  @Test
  void bloqueaUnaIpAlAlcanzarElLimiteSinAfectarAOtras() {
    for (int i = 0; i < 3; i++) limitador.registrarFallo("10.0.0.1");

    assertThat(limitador.excedido("10.0.0.1")).isTrue();
    assertThat(limitador.excedido("10.0.0.2")).isFalse();
  }

  @Test
  void liberaLaIpCuandoLosFallosSalenDeLaVentana() {
    for (int i = 0; i < 3; i++) limitador.registrarFallo("10.0.0.1");

    reloj.avanzar(Duration.ofMinutes(16));

    assertThat(limitador.excedido("10.0.0.1")).isFalse();
  }

  private static final class RelojMutable extends Clock {
    private Instant ahora;

    RelojMutable(Instant inicio) { this.ahora = inicio; }

    void avanzar(Duration duracion) { ahora = ahora.plus(duracion); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zona) { return this; }
    @Override public Instant instant() { return ahora; }
  }
}
