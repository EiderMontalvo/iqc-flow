package pe.com.iqc.flow.clientes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizadorTextoTest {
  @Test
  void quitaTildesMayusculasYEspaciosSobrantes() {
    assertThat(NormalizadorTexto.normalizar("  Compañía   Minera del PERÚ  ")).isEqualTo("compania minera del peru");
  }

  @Test
  void trataNuloComoTextoVacio() {
    assertThat(NormalizadorTexto.normalizar(null)).isEmpty();
  }
}
