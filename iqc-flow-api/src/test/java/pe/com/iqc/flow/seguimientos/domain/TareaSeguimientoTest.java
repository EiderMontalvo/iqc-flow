package pe.com.iqc.flow.seguimientos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

class TareaSeguimientoTest {
  private static final LocalDate HOY = LocalDate.of(2026, 11, 3);

  @Test
  void venceAlPasarLaFechaOLaHoraDelDia() {
    assertThat(tarea(HOY.minusDays(1), null).estaVencida(HOY, LocalTime.NOON)).isTrue();
    assertThat(tarea(HOY, null).estaVencida(HOY, LocalTime.of(23, 59))).isFalse();
    assertThat(tarea(HOY, LocalTime.of(10, 0)).estaVencida(HOY, LocalTime.of(10, 30))).isTrue();
    assertThat(tarea(HOY, LocalTime.of(10, 0)).estaVencida(HOY, LocalTime.of(9, 30))).isFalse();
  }

  @Test
  void unaTareaCerradaNoVenceNiSePuedeModificar() {
    TareaSeguimiento tarea = tarea(HOY.minusDays(3), null);
    tarea.completar(ResultadoTarea.CONTACTADO, null, Instant.now(), UUID.randomUUID());

    assertThat(tarea.estaVencida(HOY, LocalTime.NOON)).isFalse();
    assertThatThrownBy(() -> tarea.reprogramar(HOY, null))
        .isInstanceOf(ReglaNegocioException.class)
        .hasFieldOrPropertyWithValue("codigo", "TAREA_CERRADA");
  }

  @Test
  void reprogramarCuentaYPermiteVolverAAvisar() {
    TareaSeguimiento tarea = tarea(HOY.minusDays(1), null);
    tarea.marcarAvisoVencimiento(Instant.now());

    tarea.reprogramar(HOY.plusDays(2), LocalTime.of(9, 0));

    assertThat(tarea.getReprogramaciones()).isEqualTo(1);
    assertThat(tarea.getAvisoVencimientoEn()).isNull();
  }

  @Test
  void noSePuedeCerrarCambiandoElEstado() {
    assertThatThrownBy(() -> tarea(HOY, null).cambiarEstado(EstadoTarea.COMPLETADA))
        .hasFieldOrPropertyWithValue("codigo", "ESTADO_INVALIDO");
  }

  private static TareaSeguimiento tarea(LocalDate fecha, LocalTime hora) {
    return TareaSeguimiento.nueva(UUID.randomUUID(), "Llamar", null, "LLAMADA", null, null, null, UUID.randomUUID(),
        PrioridadTarea.NORMAL, fecha, hora, OrigenTarea.MANUAL, null, UUID.randomUUID());
  }
}
