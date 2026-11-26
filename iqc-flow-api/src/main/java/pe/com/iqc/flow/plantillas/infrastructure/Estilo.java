package pe.com.iqc.flow.plantillas.infrastructure;

import java.awt.Color;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 *tipografía de un texto: fuente, tamaño, color y escala horizontal en % (los originales de IQC condensan o ensanchan
 *algunas líneas con ella, y sin ese ajuste el texto no mide lo mismo)
 */
record Estilo(PDFont fuente, float tamano, Color color, float escala) {
  Estilo(PDFont fuente, float tamano, Color color) { this(fuente, tamano, color, 100f); }

  Estilo conEscala(float nuevaEscala) { return new Estilo(fuente, tamano, color, nuevaEscala); }

  Estilo conTamano(float nuevoTamano) { return new Estilo(fuente, nuevoTamano, color, escala); }

  Estilo conColor(Color nuevoColor) { return new Estilo(fuente, tamano, nuevoColor, escala); }
}
