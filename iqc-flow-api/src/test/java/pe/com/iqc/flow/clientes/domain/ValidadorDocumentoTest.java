package pe.com.iqc.flow.clientes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

class ValidadorDocumentoTest {
  @ParameterizedTest
  @ValueSource(strings = {"20131312955", "20100047218", "20601234565", "10123456781"})
  void aceptaRucConDigitoVerificadorCorrecto(String ruc) {
    assertThat(ValidadorDocumento.esRucValido(ruc)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"20131312954", "30131312955", "2013131295", "201313129550", "2013131295A", ""})
  void rechazaRucInvalido(String ruc) {
    assertThat(ValidadorDocumento.esRucValido(ruc)).isFalse();
  }

  @Test
  void normalizaEspaciosYPuntos() {
    assertThat(ValidadorDocumento.normalizar("pe", TipoDocumento.RUC, " 2013.1312 955 ")).isEqualTo("20131312955");
  }

  @Test
  void explicaPorQueElRucEsInvalido() {
    assertThatThrownBy(() -> ValidadorDocumento.normalizar("PE", TipoDocumento.RUC, "20131312954"))
        .isInstanceOf(ReglaNegocioException.class)
        .hasFieldOrPropertyWithValue("codigo", "DOCUMENTO_INVALIDO")
        .hasMessageContaining("dígito verificador");
    assertThatThrownBy(() -> ValidadorDocumento.normalizar("PE", TipoDocumento.RUC, "30131312955"))
        .hasMessageContaining("10, 15, 16, 17 o 20");
  }

  @Test
  void elRucSoloAplicaAPeruYPeruSoloAdmiteRuc() {
    assertThatThrownBy(() -> ValidadorDocumento.normalizar("CO", TipoDocumento.RUC, "20131312955"))
        .hasMessageContaining("solo aplica");
    assertThatThrownBy(() -> ValidadorDocumento.normalizar("PE", TipoDocumento.NIT, "900123456"))
        .hasMessageContaining("RUC");
  }

  @Test
  void validaElFormatoDelGstinDeIndia() {
    assertThat(ValidadorDocumento.normalizar("IN", TipoDocumento.GSTIN, "27aapfu0939f1zv")).isEqualTo("27AAPFU0939F1ZV");
    assertThatThrownBy(() -> ValidadorDocumento.normalizar("IN", TipoDocumento.GSTIN, "27AAPFU0939F1"))
        .hasMessageContaining("GSTIN");
  }

  @Test
  void rechazaCodigosDePaisInexistentes() {
    assertThatThrownBy(() -> ValidadorDocumento.normalizarPais("XX"))
        .hasFieldOrPropertyWithValue("codigo", "PAIS_INVALIDO");
  }
}
