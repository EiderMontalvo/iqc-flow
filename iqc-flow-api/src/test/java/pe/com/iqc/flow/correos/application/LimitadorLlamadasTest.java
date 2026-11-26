package pe.com.iqc.flow.correos.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LimitadorLlamadasTest {
  private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-25T10:00:00Z"));
  private final Clock reloj = new Clock() {
    @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return ahora.get(); }
  };

  @Test
  void dejaPasarHastaElTopeYRechazaLoSiguiente() {
    LimitadorLlamadas l = new LimitadorLlamadas(3, reloj);
    UUID ana = UUID.randomUUID();
    assertThat(l.permitir(ana)).isTrue();
    assertThat(l.permitir(ana)).isTrue();
    assertThat(l.permitir(ana)).isTrue();
    assertThat(l.permitir(ana)).isFalse();
  }

  @Test
  void cadaPersonaTieneSuPropiaCuenta() {
    LimitadorLlamadas l = new LimitadorLlamadas(1, reloj);
    assertThat(l.permitir(UUID.randomUUID())).isTrue();
    assertThat(l.permitir(UUID.randomUUID())).isTrue();
  }

  @Test
  void pasadoUnMinutoVuelveAPermitir() {
    LimitadorLlamadas l = new LimitadorLlamadas(1, reloj);
    UUID ana = UUID.randomUUID();
    assertThat(l.permitir(ana)).isTrue();
    ahora.set(ahora.get().plusSeconds(59));
    assertThat(l.permitir(ana)).isFalse();
    ahora.set(ahora.get().plusSeconds(2));
    assertThat(l.permitir(ana)).isTrue();
  }
}
