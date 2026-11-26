package pe.com.iqc.flow.plantillas.infrastructure;

import java.awt.Color;

/**colores de los certificados, medidos sobre los originales de IQC; es el único lugar donde se definen*/
final class Paleta {
  // Empresarial (DDC-FRM-015)
  static final Color AZUL_EMPRESARIAL = new Color(0x2E4AA6);
  static final Color NEGRO = new Color(0x050606);
  static final Color TEXTO_ALCANCE = new Color(0x231F20);
  static final Color FECHAS = new Color(0x333434);
  static final Color PIE_EMPRESARIAL = new Color(0x24265D);
  static final Color VERSION_EMPRESARIAL = new Color(0x2C3792);
  static final Color VALIDEZ = new Color(0x4A4D9C);
  static final Color ACREDITACION_NUMERO = new Color(0x2A5CB3);
  static final Color FORMATO_GRIS = new Color(0xB0B0B0);
  static final Color BLANCO = Color.WHITE;

  // Personal
  static final Color PERSONAL_TITULO = new Color(0x08283F);
  static final Color PERSONAL_TEXTO = new Color(0x183346);
  static final Color PERSONAL_ACENTO = new Color(0x126F9A);
  static final Color PERSONAL_DOCUMENTO = new Color(0x617381);

  // IQC Global (QF-12)
  static final Color GLOBAL_TEXTO = new Color(0x1F1A16);
  static final Color GLOBAL_NEGRO = Color.BLACK;

  private Paleta() {
  }
}
