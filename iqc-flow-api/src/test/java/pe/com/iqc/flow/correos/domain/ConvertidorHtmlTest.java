package pe.com.iqc.flow.correos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConvertidorHtmlTest {
  @Test
  void quitaEtiquetasConservaParrafosYDecodificaEntidades() {
    String texto = ConvertidorHtml.aTexto("<html><body><p>Estimados:&nbsp;gracias</p><p>Cotizaci&oacute;n &amp; precio &lt;S/ 500&gt;</p><br>Saludos</body></html>");
    assertThat(texto).isEqualTo("Estimados: gracias\nCotización & precio <S/ 500>\n\nSaludos");
  }

  @Test
  void descartaScriptsEstilosYComentariosConTodoSuContenido() {
    String texto = ConvertidorHtml.aTexto("<style>p{color:red}</style><script>alert('x')</script><!-- oculto --><p>Hola</p><img src='http://rastreador/x.gif'>");
    assertThat(texto).isEqualTo("Hola");
  }

  @Test
  void convierteListasYTablasEnTextoLegible() {
    String texto = ConvertidorHtml.aTexto("<ul><li>Uno</li><li>Dos</li></ul><table><tr><td>A</td><td>B</td></tr></table>");
    assertThat(texto).contains("- Uno").contains("- Dos").contains("A\tB");
  }

  @Test
  void entidadesNumericasValidasSeDecodificanYLasRarasSeDejan() {
    assertThat(ConvertidorHtml.aTexto("a&#241;o &#x41; &#0; &raro;")).isEqualTo("año A &#0; &raro;");
  }

  @Test
  void vacioONuloDanVacio() {
    assertThat(ConvertidorHtml.aTexto(null)).isEmpty();
    assertThat(ConvertidorHtml.aTexto("   ")).isEmpty();
  }
}
