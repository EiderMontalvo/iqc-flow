package pe.com.iqc.flow.clientes.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnmascaramientoTest {
  @Test
  void enmascaraElCorreoConservandoElDominio() {
    assertThat(ServicioDatosContacto.enmascararCorreo("ana.perez@empresa.com")).isEqualTo("a***@empresa.com");
    assertThat(ServicioDatosContacto.enmascararCorreo(null)).isNull();
  }

  @Test
  void enmascaraElTelefonoConservandoLosUltimosTresDigitos() {
    assertThat(ServicioDatosContacto.enmascararTelefono("+51 987 654 321")).isEqualTo("***321");
    assertThat(ServicioDatosContacto.enmascararTelefono("12")).isEqualTo("***");
  }
}
