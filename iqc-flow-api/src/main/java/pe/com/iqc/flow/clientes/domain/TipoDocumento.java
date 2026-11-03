package pe.com.iqc.flow.clientes.domain;

/**identificador fiscal de la empresa según su país. */
public enum TipoDocumento {
  /**Perú (SUNAT). */
  RUC,
  /**Colombia, Bolivia y otros. */
  NIT,
  /**México. */
  RFC,
  /**Chile. */
  RUT,
  /**Argentina. */
  CUIT,
  /**India (Goods and Services Tax Identification Number). */
  GSTIN,
  OTRO
}
