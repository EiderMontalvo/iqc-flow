package pe.com.iqc.flow.plantillas.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import org.apache.fontbox.ttf.GlyphSubstitutionTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado;
import pe.com.iqc.flow.plantillas.infrastructure.ContextoPdf.Fuentes;

/**
 *dibuja cada certificado sobre un fondo vectorial (la página original sin texto) y escribe encima los datos. Así el
 *marco, el logo y la marca de agua son los de IQC y el PDF resultante tiene texto real, que se puede buscar y leer
 */
@Component
class PdfBoxGeneradorCertificado implements GeneradorPdfCertificado {
  private final RecursosCertificado recursos;

  PdfBoxGeneradorCertificado(RecursosCertificado recursos) { this.recursos = recursos; }

  @Override
  public byte[] empresarial(CertificadoEmpresarial datos) {
    return generar("fondos/empresarial.pdf", "Certificado " + datos.codigo(),
        contexto -> new PlantillaEmpresarial(contexto).dibujar(datos));
  }

  @Override
  public byte[] personal(CertificadoPersonal datos) {
    return generar("fondos/personal.pdf", "Certificado " + datos.codigo(),
        contexto -> new PlantillaPersonal(contexto).dibujar(datos));
  }

  @Override
  public byte[] iqcGlobal(CertificadoIqcGlobal datos) {
    PlantillaIqcGlobal.Diseno diseno = PlantillaIqcGlobal.diseno(datos.normaCodigo());
    return generar(diseno.fondo(), "Certificado " + datos.codigo(), contexto -> new PlantillaIqcGlobal(contexto, diseno).dibujar(datos));
  }

  private byte[] generar(String fondo, String titulo, Consumer<ContextoPdf> dibujo) {
    try (PDDocument original = Loader.loadPDF(recursos.bytes(fondo)); PDDocument documento = new PDDocument()) {
      PDPage pagina = new PDPage(original.getPage(0).getMediaBox());
      documento.addPage(pagina);
      PDFormXObject formaFondo = new LayerUtility(documento).importPageAsForm(original, 0);
      Fuentes fuentes = new Fuentes(lato(documento, "Bold"), lato(documento, "Light"), lato(documento, "LightItalic"),
          new PDType1Font(FontName.HELVETICA), new PDType1Font(FontName.HELVETICA_BOLD), incrustar(documento, "fuentes/P052-Roman.ttf"),
          incrustar(documento, "fuentes/P052-Bold.ttf"));
      try (Lienzo lienzo = new Lienzo(documento, pagina)) {
        lienzo.fondo(formaFondo);
        dibujo.accept(new ContextoPdf(documento, lienzo, fuentes, recursos));
      }
      documento.getDocumentInformation().setTitle(titulo);
      documento.getDocumentInformation().setCreator("IQC Flow");
      ByteArrayOutputStream salida = new ByteArrayOutputStream();
      documento.save(salida);
      return salida.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo generar el PDF del certificado", e);
    }
  }

  private PDType0Font lato(PDDocument documento, String variante) throws IOException {
    return incrustar(documento, "fuentes/Lato-" + variante + ".ttf");
  }

  /**
   *fuente TrueType sin su tabla GSUB: con ella PDFBox sustituye «ti», «tt» y «fi» por ligaduras que el PDF no sabe volver a
   *texto, y el certificado dejaría de poder buscarse o leerse («cer ficado»)
   */
  private PDType0Font incrustar(PDDocument documento, String ruta) throws IOException {
    TrueTypeFont fuente = new TTFParser().parse(new RandomAccessReadBuffer(recursos.bytes(ruta)));
    fuente.getTableMap().remove(GlyphSubstitutionTable.TAG);
    return PDType0Font.load(documento, fuente, true);
  }
}
