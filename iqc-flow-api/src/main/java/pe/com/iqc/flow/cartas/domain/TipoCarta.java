package pe.com.iqc.flow.cartas.domain;

/**
 *cartas que se emiten a un cliente; cada una usa una plantilla de plantillas/cartas. requiereCertificados: la carta
 *habla de certificados concretos del cliente (sus normas y ediciones salen de ellos)
 */
public enum TipoCarta {
  CERTIFICACION("carta-certificacion.docx", "Carta de certificación", "Aprobación o recertificación del sistema de gestión, con su alcance", true),
  CONTINUIDAD("carta-continuidad.docx", "Carta de continuidad", "Continuidad de las certificaciones tras una vigilancia", true),
  SUSPENSION_PAGO("carta-suspension-pago.docx", "Suspensión por falta de pago", "Notifica la suspensión de las certificaciones por pagos pendientes", true),
  LIBRE("carta-base.docx", "Carta libre", "Asunto y texto a tu medida, con el membrete y la firma de IQC", false);

  private final String plantilla;
  private final String nombre;
  private final String descripcion;
  private final boolean requiereCertificados;

  TipoCarta(String plantilla, String nombre, String descripcion, boolean requiereCertificados) {
    this.plantilla = plantilla;
    this.nombre = nombre;
    this.descripcion = descripcion;
    this.requiereCertificados = requiereCertificados;
  }

  public String plantilla() { return plantilla; }
  public String nombre() { return nombre; }
  public String descripcion() { return descripcion; }
  public boolean requiereCertificados() { return requiereCertificados; }
}
