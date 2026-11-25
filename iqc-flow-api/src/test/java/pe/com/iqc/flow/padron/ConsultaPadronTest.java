package pe.com.iqc.flow.padron;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**lectura de las respuestas de SUNAT (textos de ejemplo ficticios, sin red)*/
class ConsultaPadronTest {
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void leeRazonSocialYDireccionDelRuc() {
    String cuerpo = """
        {"message":"success","lista":[{"apenomdenunciado":"EMPRESA DE PRUEBA S.A.C.          ","direstablecimiento":"AV. FICTICIA - Nro: 123  - LIMA",
        "desdistrito":"MIRAFLORES     ","desprovincia":"LIMA     ","desdepartamento":"LIMA    "}]}""";
    var r = ConsultaPadron.leerRuc("20123456789", cuerpo, json).orElseThrow();
    assertThat(r.razonSocial()).isEqualTo("EMPRESA DE PRUEBA S.A.C.");
    assertThat(r.direccion()).isEqualTo("AV. FICTICIA - Nro: 123 - LIMA");
    assertThat(r.distrito()).isEqualTo("MIRAFLORES");
  }

  @Test
  void ordenaNombresYApellidosDelDni() {
    var d = ConsultaPadron.leerDni("12345678", "{\"message\":\"success\",\"lista\":[{\"nombresapellidos\":\"DE LA CRUZ PEREZ,ANA MARIA\"}]}", json).orElseThrow();
    assertThat(d.nombres()).isEqualTo("Ana Maria");
    assertThat(d.apellidos()).isEqualTo("De la Cruz Perez");
    assertThat(d.nombreCompleto()).isEqualTo("Ana Maria de la Cruz Perez");
  }

  @Test
  void sinDatosNoDevuelveNada() {
    assertThat(ConsultaPadron.leerRuc("20000000001", "{\"error\":\"No existen datos para los filtros seleccionados\"}", json)).isEmpty();
    assertThat(ConsultaPadron.leerDni("00000000", "no es json", json)).isEmpty();
  }
}
