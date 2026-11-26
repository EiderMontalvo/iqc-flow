package pe.com.iqc.flow.correos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizadorTextoCorreoTest {
  @Test
  void quitaTildesYPasaAMinusculas() {
    assertThat(NormalizadorTextoCorreo.normalizar("Cotización URGENTE — Ñandú")).isEqualTo("cotizacion urgente — nandu");
    assertThat(NormalizadorTextoCorreo.normalizar(null)).isEmpty();
  }

  @Test
  void separaLasPalabras() {
    assertThat(NormalizadorTextoCorreo.palabras("factura f001-23 pagada!")).containsExactly("factura", "f001", "23", "pagada");
    assertThat(NormalizadorTextoCorreo.palabras("")).isEmpty();
  }

  @Test
  void aceptaIgualesYUnaSolaErrata() {
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certificado")).isTrue();
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certiticado")).as("una letra distinta").isTrue();
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certifcado")).as("una letra de menos").isTrue();
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certificaddo")).as("una letra de más").isTrue();
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certifiacdo")).as("dos letras contiguas cambiadas").isTrue();
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "ocertificado")).as("de más al principio").isTrue();
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certificad")).as("de menos al final").isTrue();
  }

  @Test
  void rechazaDosErratasOLongitudesMuyDistintas() {
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certiticaco")).isFalse();
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certifica")).isFalse();
    assertThat(NormalizadorTextoCorreo.casiIguales("certificado", "certificacion")).isFalse();
    assertThat(NormalizadorTextoCorreo.casiIguales("validacion", "validar")).isFalse();
    assertThat(NormalizadorTextoCorreo.casiIguales("abcd", "badc")).as("dos intercambios a la vez son dos errores").isFalse();
  }

  @Test
  void trataLosVaciosSinFallar() {
    assertThat(NormalizadorTextoCorreo.casiIguales("", "")).isTrue();
    assertThat(NormalizadorTextoCorreo.casiIguales("", "a")).isTrue();
    assertThat(NormalizadorTextoCorreo.casiIguales("", "ab")).isFalse();
  }
}
