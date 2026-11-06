package pe.com.iqc.flow.auditorias.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CodigoCertificadoTest {
  @Test
  void reproduceElEjemploDelProcedimiento() {
    // DDC-PRO-003: PE-INA-00120210111 = PE · INA · 001 · 2021 · 01 · 1 · 1
    assertThat(CodigoCertificado.generar("PE", "INACAL", "001-2021", 2021, "01", 1, 1)).isEqualTo("PE-INA-00120210111");
  }

  @Test
  void ecuadorUsaElPaisDelOrganismoYLaAcreditacionSae() {
    assertThat(CodigoCertificado.generar("PE", "SAE", "CT 045/2026", 2026, "02", 2, 1)).isEqualTo("PE-SAE-04520260221");
  }

  @Test
  void exigeContratoYNoGeneraCodigosParaIqcGlobal() {
    assertThatThrownBy(() -> CodigoCertificado.generar("PE", "INACAL", "A-1", 2026, "02", 1, 1))
        .hasFieldOrPropertyWithValue("codigo", "CONTRATO_REQUERIDO");
    assertThatThrownBy(() -> CodigoCertificado.generar("PE", "IQC_GLOBAL", "001", 2026, "02", 1, 1))
        .hasFieldOrPropertyWithValue("codigo", "ESQUEMA_SIN_CODIGO");
  }
}
