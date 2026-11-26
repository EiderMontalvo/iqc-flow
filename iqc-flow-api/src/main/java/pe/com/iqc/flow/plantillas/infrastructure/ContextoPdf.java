package pe.com.iqc.flow.plantillas.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**lo que necesita una plantilla para dibujar: el lienzo, las fuentes ya incrustadas en el documento y sus imágenes*/
record ContextoPdf(PDDocument documento, Lienzo lienzo, Fuentes fuentes, RecursosCertificado recursos) {
  /**
   *Lato (incrustada, tal como la usa el diseño empresarial), Helvetica estándar (la del diseño personal) y P052, el clon
   *libre de Palatino que reemplaza a Book Antiqua en el diseño de IQC Global
   */
  record Fuentes(PDFont latoNegrita, PDFont latoLigera, PDFont latoLigeraCursiva, PDFont helvetica,
      PDFont helveticaNegrita, PDFont palatino, PDFont palatinoNegrita) {
  }

  PDImageXObject imagen(String ruta) { return imagen(recursos.bytes(ruta), ruta); }

  PDImageXObject imagen(byte[] contenido, String nombre) {
    try {
      return PDImageXObject.createFromByteArray(documento, contenido, nombre);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
