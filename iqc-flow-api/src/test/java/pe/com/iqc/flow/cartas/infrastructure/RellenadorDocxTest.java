package pe.com.iqc.flow.cartas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.com.iqc.flow.cartas.domain.TipoCarta;

/**llenado de las plantillas reales de plantillas/cartas (y, si el equipo tiene LibreOffice, su conversión a PDF)*/
class RellenadorDocxTest {
  private final RellenadorDocx rellenador = new RellenadorDocx();

  private static final Map<String, String> CAMPOS = Map.ofEntries(Map.entry("marcaNombre", "IQC Perú"),
      Map.entry("marcaDireccion", "Jr. Abancay 425, Huancayo"), Map.entry("marcaTelefono", "+51 980 923 270"),
      Map.entry("marcaCorreo", "administracion@iqcperu.com"), Map.entry("marcaRazonSocial", "IQC PERÚ S.A.C."),
      Map.entry("marcaRuc", "20604476691"), Map.entry("marcaDireccionFiscal", "Av. Arequipa 2450, Lince"), Map.entry("ciudad", "Lima"),
      Map.entry("fechaTexto", "26 de noviembre de 2026"), Map.entry("razonSocial", "EMPRESA DE PRUEBA S.A.C."),
      Map.entry("numeroDocumento", "20123456789"), Map.entry("tipoServicio", "recertificación"),
      Map.entry("normas", "ISO 9001:2015 e ISO 14001:2015"), Map.entry("normasSinEdicion", "ISO 9001 e ISO 14001"),
      Map.entry("sistema", "Sistema Integrado de Gestión"), Map.entry("etapa", "2da Vigilancia"), Map.entry("alcance", "Fabricación de estructuras"),
      Map.entry("asunto", "Asunto de prueba"), Map.entry("firmanteNombre", "Nombre Firmante"), Map.entry("firmanteCargo", "Gerente General"));
  private static final List<Map<String, String>> NORMAS = List.of(Map.of("codigo", "ISO 9001", "nombre", "Sistema de Gestión de la Calidad"),
      Map.of("codigo", "ISO 14001", "nombre", "Sistema de Gestión Ambiental"));

  @ParameterizedTest
  @EnumSource(TipoCarta.class)
  void cadaPlantillaQuedaSinCamposPendientes(TipoCarta tipo) throws Exception {
    byte[] docx = llenar(tipo, Map.of());
    String texto = texto(docx);
    assertThat(texto).doesNotContain("{{").contains("EMPRESA DE PRUEBA S.A.C.", "Lima, 26 de noviembre de 2026", "Nombre Firmante", "IQC PERÚ S.A.C.");
    if (tipo == TipoCarta.SUSPENSION_PAGO) {
      assertThat(texto).contains("ISO 9001: Sistema de Gestión de la Calidad.", "ISO 14001: Sistema de Gestión Ambiental.");
    }
    if (tipo == TipoCarta.LIBRE) assertThat(texto).contains("Uno.", "Dos,\ncon salto.");
  }

  @Test
  void cambiaLaFirmaPorLaDeLaPersonaSinDeformarla() throws Exception {
    byte[] firma = png(800, 200);
    byte[] docx = llenar(TipoCarta.CONTINUIDAD, Map.of("firma", firma));
    try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx))) {
      assertThat(d.getAllPictures()).extracting(XWPFPictureData::getData).anySatisfy(datos -> assertThat(datos).isEqualTo(firma));
      var forma = d.getParagraphs().stream().flatMap(p -> p.getRuns().stream()).flatMap(r -> r.getEmbeddedPictures().stream())
          .filter(p -> java.util.Arrays.equals(p.getPictureData().getData(), firma)).findFirst().orElseThrow();
      double proporcion = forma.getWidth() / forma.getDepth();
      assertThat(proporcion).isCloseTo(4.0, org.assertj.core.data.Offset.offset(0.05));
    }
  }

  @Test
  void conLibreOfficeElPdfLlevaElCierreEnLaUltimaPagina() throws Exception {
    ConvertidorPdf convertidor = new ConvertidorPdf(new PropiedadesCartas("soffice", Duration.ofSeconds(90), "Lima",
        new PropiedadesCartas.Empresa("IQC PERÚ S.A.C.", "20604476691", "Lima"), new PropiedadesCartas.Membrete("Huancayo", "980", "a@b.pe")));
    assumeTrue(convertidor.disponible(), "LibreOffice no está instalado en este equipo");
    byte[] pdf = convertidor.aPdf(llenar(TipoCarta.SUSPENSION_PAGO, Map.of()));
    try (PDDocument d = Loader.loadPDF(pdf)) {
      PDFTextStripper lector = new PDFTextStripper();
      lector.setStartPage(d.getNumberOfPages());
      lector.setEndPage(d.getNumberOfPages());
      assertThat(lector.getText(d)).contains("Atentamente", "Nombre Firmante", "Gerente General");
    }
  }

  private byte[] llenar(TipoCarta tipo, Map<String, byte[]> imagenes) throws Exception {
    try (InputStream plantilla = getClass().getResourceAsStream("/plantillas/cartas/" + tipo.plantilla())) {
      assertThat(plantilla).as("plantilla %s en el classpath", tipo.plantilla()).isNotNull();
      return rellenador.llenar(plantilla, new RellenadorDocx.Datos(CAMPOS, NORMAS, "Uno.\n\nDos,\ncon salto.", imagenes));
    }
  }

  private static String texto(byte[] docx) throws Exception {
    try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx)); XWPFWordExtractor e = new XWPFWordExtractor(d)) {
      return e.getText();
    }
  }

  private static byte[] png(int ancho, int alto) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }
}
