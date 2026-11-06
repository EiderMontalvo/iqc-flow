package pe.com.iqc.flow.organizaciones;

import java.util.List;

/**
 *parámetros configurables por organización, con sus valores admitidos y el valor por defecto. Para habilitar
 *algo nuevo (p. ej. la visibilidad de una categoría en el portal de clientes, ADR-032) se agrega aquí
 */
public enum ParametroOrganizacion {
  /**quién debe firmar un certificado antes de emitirlo (DDC-PRO-003: Gerente General o Jefe Técnico)*/
  POLITICA_FIRMA_CERTIFICADOS("FIRMA_GERENCIA", List.of("FIRMA_GERENCIA", "FIRMA_GERENCIA_Y_JEFE_TECNICO"));

  private final String porDefecto;
  private final List<String> admitidos;

  ParametroOrganizacion(String porDefecto, List<String> admitidos) {
    this.porDefecto = porDefecto;
    this.admitidos = admitidos;
  }

  public String porDefecto() { return porDefecto; }
  public List<String> admitidos() { return admitidos; }
}
