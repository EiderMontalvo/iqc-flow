package pe.com.iqc.flow.correos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerminosDeBusquedaTest {
  @Test
  void quedanSoloLasPalabrasQueDistinguenUnCorreoDeOtro() {
    assertThat(TerminosDeBusqueda.de("¿Qué correos de PAGO llegaron esta semana? Dime sobre la factura de Minera Andina")).containsExactly("pago", "factura", "minera", "andina");
  }

  @Test
  void lasTildesYLasMayusculasNoImportan() {
    assertThat(TerminosDeBusqueda.de("Validación de CERTIFICACIÓN")).containsExactly("validacion", "certificacion");
  }

  @Test
  void unaPreguntaGeneralNoDejaTerminosYSeMiranLosMasRecientes() {
    assertThat(TerminosDeBusqueda.de("¿Qué llegó hoy?")).isEmpty();
  }

  @Test
  void sinRepetirYConUnTope() {
    assertThat(TerminosDeBusqueda.de("auditoria auditoria AUDITORIA")).containsExactly("auditoria");
    assertThat(TerminosDeBusqueda.de("alfa1 beta2 gama3 delta4 epsilon5 zeta66 etaaa7 theta8 iotaa9 kappa10")).hasSize(8);
  }

  @Test
  void quienPreguntaPorLaPublicidadLaQuiereVer() {
    assertThat(TerminosDeBusqueda.pidePublicidad("¿Cuánta publicidad llegó?")).isTrue();
    assertThat(TerminosDeBusqueda.pidePublicidad("¿Hay spam o correos no deseados?")).isTrue();
    assertThat(TerminosDeBusqueda.pidePublicidad("¿Llegó la factura?")).isFalse();
  }
}
