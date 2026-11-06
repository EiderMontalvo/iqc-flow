package pe.com.iqc.flow.clientes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

class RucEcuadorTest {
  @ParameterizedTest
  @ValueSource(strings = {
      "1710034065001", // persona natural (cédula, módulo 10)
      "1790011674001", // sociedad privada (tercer dígito 9, módulo 11)
      "1760001550001"  // entidad pública (tercer dígito 6, módulo 11)
  })
  void aceptaRucConEstructuraYDigitoVerificadorCorrectos(String ruc) {
    assertThat(ValidadorDocumento.normalizar("EC", TipoDocumento.RUC, ruc)).isEqualTo(ruc);
    assertThat(ValidadorDocumento.esDigitoVerificadorEcuadorValido(ruc)).isTrue();
    assertThat(ValidadorDocumento.advertencias("EC", TipoDocumento.RUC, ruc)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
      "179001167400, 13 dígitos",
      "2510034065001, provincia",
      "1770011674001, tercer dígito",
      "1790011674000, establecimiento"
  })
  void rechazaUnaEstructuraInvalida(String ruc, String mensaje) {
    assertThatThrownBy(() -> ValidadorDocumento.normalizar("EC", TipoDocumento.RUC, ruc))
        .isInstanceOf(ReglaNegocioException.class)
        .hasFieldOrPropertyWithValue("codigo", "DOCUMENTO_INVALIDO")
        .hasMessageContaining(mensaje);
  }

  @Test
  void unDigitoVerificadorDudosoSoloGeneraUnaAdvertencia() {
    String ruc = "1790011675001";

    assertThat(ValidadorDocumento.normalizar("EC", TipoDocumento.RUC, ruc)).isEqualTo(ruc);
    assertThat(ValidadorDocumento.advertencias("EC", TipoDocumento.RUC, ruc)).singleElement()
        .asString().contains("SRI");
  }

  @Test
  void normalizaGuionesYEspacios() {
    assertThat(ValidadorDocumento.normalizar("ec", TipoDocumento.RUC, "17 9001 1674-001")).isEqualTo("1790011674001");
  }

  @Test
  void lasEmpresasEcuatorianasSeIdentificanConRuc() {
    assertThatThrownBy(() -> ValidadorDocumento.normalizar("EC", TipoDocumento.OTRO, "1790011674001"))
        .hasMessageContaining("RUC");
  }
}
