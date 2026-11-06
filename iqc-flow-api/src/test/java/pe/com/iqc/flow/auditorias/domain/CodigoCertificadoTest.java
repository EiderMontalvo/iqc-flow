package pe.com.iqc.flow.auditorias.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CodigoCertificadoTest {
  @Test
  void reproduceElEjemploDelProcedimiento() {
    // DDC-PRO-003: PE-INA-00120210111 = PE · INA · 001 · 2021 · 01 · 1 · 1
    assertThat(CodigoCertificado.generar("PE", "INACAL", "001-2021", 2021, "01", 1, 1, null)).isEqualTo("PE-INA-00120210111");
  }

  @Test
  void ecuadorUsaElPaisDelOrganismoYLaAcreditacionSae() {
    assertThat(CodigoCertificado.generar("PE", "SAE", "CT 045/2026", 2026, "02", 2, 1, null)).isEqualTo("PE-SAE-04520260221");
  }

  @Test
  void laEdicion2025DeIso37001AgregaLaLetraA() {
    // Certificado real de la plataforma anterior: ISO 37001:2025 bajo SAE
    assertThat(CodigoCertificado.generar("PE", "SAE", "021", 2026, "01", 1, 1, "A")).isEqualTo("PE-SAE-02120260111A");
  }

  @Test
  void unaRenovacionConservaElAnioYSubeElCiclo() {
    // Certificado real renovado en 2025: año del otorgamiento original (2022), revisión 3, ciclo 2
    assertThat(CodigoCertificado.generar("PE", "INACAL", "015", 2022, "01", 3, 2, null)).isEqualTo("PE-INA-01520220132");
  }

  @Test
  void exigeContratoYNoGeneraCodigosParaIqcGlobal() {
    assertThatThrownBy(() -> CodigoCertificado.generar("PE", "INACAL", "A-1", 2026, "02", 1, 1, null))
        .hasFieldOrPropertyWithValue("codigo", "CONTRATO_REQUERIDO");
    assertThatThrownBy(() -> CodigoCertificado.generar("PE", "IQC_GLOBAL", "001", 2026, "02", 1, 1, null))
        .hasFieldOrPropertyWithValue("codigo", "ESQUEMA_SIN_CODIGO");
  }
}
