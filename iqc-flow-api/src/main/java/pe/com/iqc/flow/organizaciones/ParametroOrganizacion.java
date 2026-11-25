package pe.com.iqc.flow.organizaciones;

import java.util.List;

/**
 *parámetros configurables por organización, con sus valores admitidos y el valor por defecto. Para habilitar
 *algo nuevo (p. ej. la visibilidad de una categoría en el portal de clientes, ADR-032) se agrega aquí
 */
public enum ParametroOrganizacion {
  /**quién debe firmar un certificado antes de emitirlo (DDC-PRO-003: Gerente General o Jefe Técnico)*/
  POLITICA_FIRMA_CERTIFICADOS("FIRMA_GERENCIA", List.of("FIRMA_GERENCIA", "FIRMA_GERENCIA_Y_JEFE_TECNICO"), false),

  // Interruptores generales (solo MASTER): valen para todos los usuarios de la organización

  /**IA para todos: lectura de PDF de respaldo y, más adelante, el asistente. Apagada hasta elegir proveedor*/
  IA_HABILITADA("NO", List.of("SI", "NO"), true),
  /**reglas automáticas (tareas del ciclo, recordatorios, escalamientos)*/
  AUTOMATIZACIONES_HABILITADAS("SI", List.of("SI", "NO"), true),

  // Modos de registro de certificados: cada uno se habilita por separado y el formulario muestra los habilitados

  /**se sube el PDF y los datos se escriben a mano*/
  CERT_EMPRESARIAL_MODO_MANUAL("SI", List.of("SI", "NO"), true),
  /**se sube el PDF, el sistema lee sus campos y completa el formulario para revisarlo*/
  CERT_EMPRESARIAL_MODO_AUTOMATIZADO("SI", List.of("SI", "NO"), true),
  /**se completa el formulario y el PDF se genera desde una plantilla Word*/
  CERT_EMPRESARIAL_MODO_PLANTILLA("SI", List.of("SI", "NO"), true),
  CERT_PERSONAL_MODO_MANUAL("SI", List.of("SI", "NO"), true),
  CERT_PERSONAL_MODO_AUTOMATIZADO("SI", List.of("SI", "NO"), true),
  CERT_PERSONAL_MODO_PLANTILLA("SI", List.of("SI", "NO"), true),

  /**
   *temática visual del panel para todos (fondo con íconos de la fecha). AUTOMATICA la elige el panel según el
   *calendario de Perú; NINGUNA la apaga
   */
  TEMATICA("NINGUNA", List.of("NINGUNA", "AUTOMATICA", "ANO_NUEVO", "SAN_VALENTIN", "DIA_DE_LA_MUJER", "DIA_DE_LA_MADRE",
      "DIA_DEL_PADRE", "FIESTAS_PATRIAS", "PRIMAVERA", "HALLOWEEN", "NAVIDAD"), false),

  /**días sin respuesta tras enviar una cotización para crear la tarea de seguimiento a su responsable*/
  COTIZACION_DIAS_SIN_RESPUESTA("7", List.of("3", "5", "7", "10", "15", "30"), false),

  // Herramientas del rol Administración y logística (MASTER puede desactivarlas)

  /**Administración puede usar el autocompletado desde PDF*/
  ADMINISTRACION_AUTOCOMPLETADO("SI", List.of("SI", "NO"), false),
  /**Administración puede generar PDF desde plantillas*/
  ADMINISTRACION_GENERACION_PDF("SI", List.of("SI", "NO"), false);

  private final String porDefecto;
  private final List<String> admitidos;
  private final boolean porPersona;

  ParametroOrganizacion(String porDefecto, List<String> admitidos, boolean porPersona) {
    this.porDefecto = porDefecto;
    this.admitidos = admitidos;
    this.porPersona = porPersona;
  }

  public String porDefecto() { return porDefecto; }
  public List<String> admitidos() { return admitidos; }

  /**
   *true si MASTER puede apagarlo a una persona concreta aunque siga encendido para la organización. Los de
   *Administración no: actúan sobre un rol, y a una persona se le apaga el modo (Automatizado o Desde plantilla)
   */
  public boolean porPersona() { return porPersona; }
}
