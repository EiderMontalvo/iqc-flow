package pe.com.iqc.flow.correos.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServicioBandejaTest {
  @Test
  void elAsuntoDeLaRespuestaNoRepiteRe() {
    assertThat(ServicioBandeja.asuntoDeRespuesta("Cotización ISO 9001")).isEqualTo("Re: Cotización ISO 9001");
    assertThat(ServicioBandeja.asuntoDeRespuesta("RE: Cotización")).isEqualTo("RE: Cotización");
    assertThat(ServicioBandeja.asuntoDeRespuesta("re: x")).isEqualTo("re: x");
    assertThat(ServicioBandeja.asuntoDeRespuesta("")).isEqualTo("Re: ");
    assertThat(ServicioBandeja.asuntoDeRespuesta("a".repeat(2000))).hasSize(900);
  }

  @Test
  void soloSeConservanLasUltimasReferenciasDelHilo() {
    assertThat(ServicioBandeja.ultimasReferencias(null)).isNull();
    assertThat(ServicioBandeja.ultimasReferencias("  ")).isNull();
    assertThat(ServicioBandeja.ultimasReferencias("<a@x> <b@x>")).isEqualTo("<a@x> <b@x>");
    String muchas = String.join(" ", java.util.stream.IntStream.rangeClosed(1, 15).mapToObj(i -> "<" + i + "@x>").toList());
    assertThat(ServicioBandeja.ultimasReferencias(muchas)).startsWith("<6@x>").endsWith("<15@x>").doesNotContain("<5@x>");
  }
}
