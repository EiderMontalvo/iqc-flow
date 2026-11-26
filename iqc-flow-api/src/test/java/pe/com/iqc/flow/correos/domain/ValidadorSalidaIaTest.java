package pe.com.iqc.flow.correos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.AsistenteCorreos.Borrador;
import pe.com.iqc.flow.correos.AsistenteCorreos.Consulta;
import pe.com.iqc.flow.correos.AsistenteCorreos.Consumo;
import pe.com.iqc.flow.correos.AsistenteCorreos.Resumen;
import pe.com.iqc.flow.correos.TipoCorreo;

class ValidadorSalidaIaTest {
  private static final Consumo CONSUMO = new Consumo("modelo", 10, 5);

  private static void rechaza(Runnable r) {
    assertThatThrownBy(r::run).isInstanceOf(ReglaNegocioException.class).hasFieldOrPropertyWithValue("codigo", "IA_RESPUESTA_INVALIDA");
  }

  @Test
  void elResumenSeLimpiaYSeAcota() {
    List<String> puntos = List.of("Uno", "  ", "Dos\nlíneas", "Tres", "Cuatro", "Cinco", "Seis", "Siete");
    Resumen r = ValidadorSalidaIa.resumen(new Resumen("Pide\tcotización\r\nde ISO 9001.", puntos, "  ", TipoCorreo.COTIZACION, CONSUMO));
    assertThat(r.resumen()).isEqualTo("Pide cotización de ISO 9001.");
    assertThat(r.puntosClave()).containsExactly("Uno", "Dos líneas", "Tres", "Cuatro", "Cinco");
    assertThat(r.accionSugerida()).isNull();
    assertThat(r.tipoSugerido()).isEqualTo(TipoCorreo.COTIZACION);
  }

  @Test
  void unTextoDeLecturaMuyLargoSeRecortaConPuntosSuspensivosEnVezDeRechazarse() {
    Resumen r = ValidadorSalidaIa.resumen(new Resumen("x".repeat(ValidadorSalidaIa.RESUMEN_MAXIMO + 50), List.of(), null, null, CONSUMO));
    assertThat(r.resumen()).hasSize(ValidadorSalidaIa.RESUMEN_MAXIMO).endsWith("…");
  }

  @Test
  void unResumenVacioSeRechaza() {
    rechaza(() -> ValidadorSalidaIa.resumen(new Resumen("  \n ", List.of("a"), null, null, CONSUMO)));
    rechaza(() -> ValidadorSalidaIa.resumen(new Resumen(null, null, null, null, CONSUMO)));
  }

  @Test
  void elAsuntoDelBorradorVaEnUnaSolaLineaYNoPuedeInyectarCabeceras() {
    Borrador b = ValidadorSalidaIa.borrador(new Borrador("Re: Hola\r\nBcc: robo@x.pe", "Estimado cliente:\n\nGracias.\r\nSaludos", CONSUMO));
    assertThat(b.asunto()).isEqualTo("Re: Hola Bcc: robo@x.pe").doesNotContain("\n").doesNotContain("\r");
    assertThat(b.cuerpo()).isEqualTo("Estimado cliente:\n\nGracias.\nSaludos"); // el cuerpo conserva sus saltos de línea
  }

  @Test
  void sinAsuntoSeDejaVacioParaQueElServicioPongaElRe() {
    assertThat(ValidadorSalidaIa.borrador(new Borrador(null, "Texto", CONSUMO)).asunto()).isEmpty();
  }

  @Test
  void elBorradorSeRechazaSiNoCabeOEstaVacio() {
    rechaza(() -> ValidadorSalidaIa.borrador(new Borrador("a".repeat(ValidadorSalidaIa.ASUNTO_MAXIMO + 1), "Texto", CONSUMO)));
    rechaza(() -> ValidadorSalidaIa.borrador(new Borrador("Re: x", "b".repeat(ValidadorSalidaIa.CUERPO_MAXIMO + 1), CONSUMO)));
    rechaza(() -> ValidadorSalidaIa.borrador(new Borrador("Re: x", "   ", CONSUMO)));
  }

  @Test
  void laConsultaSoloPuedeCitarCorreosQueSeLeDieron() {
    Consulta c = ValidadorSalidaIa.consulta(new Consulta("Llegó una factura.", java.util.Arrays.asList("a", "inventado", " b ", "a", null), CONSUMO), java.util.Set.of("a", "b", "c"));
    assertThat(c.idsUsados()).containsExactly("a", "b");
    rechaza(() -> ValidadorSalidaIa.consulta(new Consulta(" ", List.of(), CONSUMO), java.util.Set.of()));
  }
}
