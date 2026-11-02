package pe.com.iqc.flow.identidad.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

class PoliticaContrasenaTest {
  @Test
  void aceptaUnaFraseLarga() {
    assertThatCode(() -> PoliticaContrasena.validar("caballo correcto batería grapa", "ana@iqcperu.com"))
        .doesNotThrowAnyException();
  }

  @Test
  void rechazaMenosDeDoceCaracteres() {
    assertThatThrownBy(() -> PoliticaContrasena.validar("Corta-123", "ana@iqcperu.com"))
        .isInstanceOf(ReglaNegocioException.class)
        .hasFieldOrPropertyWithValue("codigo", "CONTRASENA_DEBIL");
  }

  @Test
  void rechazaMasDeSetentaYDosBytes() {
    assertThatThrownBy(() -> PoliticaContrasena.validar("ñ".repeat(40), "ana@iqcperu.com"))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("72 bytes");
  }

  @Test
  void rechazaContrasenasRepetitivas() {
    assertThatThrownBy(() -> PoliticaContrasena.validar("aaaaaaaaaaab", "ana@iqcperu.com"))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("repetitiva");
  }

  @Test
  void rechazaQueContengaElUsuarioDelCorreo() {
    assertThatThrownBy(() -> PoliticaContrasena.validar("MiClaveAna.Perez2026", "ana.perez@iqcperu.com"))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("usuario de correo");
  }
}
