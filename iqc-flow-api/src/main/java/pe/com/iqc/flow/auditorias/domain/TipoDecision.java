package pe.com.iqc.flow.auditorias.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 *tipos de decisión de DDC-PRO-003. Las "iniciales" cierran un expediente que está EN_DECISION (otorgan o
 *rechazan); las "posteriores" actúan sobre certificados ya emitidos del expediente (vigilancias, sanciones)
 */
public enum TipoDecision {
  OTORGAMIENTO(true, true, EnumSet.of(TipoServicio.CERTIFICACION_INICIAL)),
  RENOVACION(true, true, EnumSet.of(TipoServicio.RECERTIFICACION)),
  AMPLIACION(true, true, EnumSet.of(TipoServicio.AMPLIACION)),
  /**certificado vigente de otro organismo que pasa a IQC*/
  TRANSFERENCIA(true, true, EnumSet.of(TipoServicio.CERTIFICACION_INICIAL, TipoServicio.RECERTIFICACION)),
  RECHAZO(true, false, EnumSet.allOf(TipoServicio.class)),
  /**tras una vigilancia: el certificado continúa*/
  MANTENIMIENTO(false, true, EnumSet.allOf(TipoServicio.class)),
  /**reducción del alcance: genera una nueva revisión del certificado*/
  REDUCCION(false, true, EnumSet.allOf(TipoServicio.class)),
  SUSPENSION(false, false, EnumSet.allOf(TipoServicio.class)),
  RESTAURACION(false, true, EnumSet.allOf(TipoServicio.class)),
  RETIRO(false, false, EnumSet.allOf(TipoServicio.class)),
  CANCELACION_VOLUNTARIA(false, false, EnumSet.allOf(TipoServicio.class));

  private final boolean inicial;
  private final boolean favorable;
  private final Set<TipoServicio> servicios;

  TipoDecision(boolean inicial, boolean favorable, Set<TipoServicio> servicios) {
    this.inicial = inicial;
    this.favorable = favorable;
    this.servicios = servicios;
  }

  public boolean esInicial() { return inicial; }

  /**la decisión favorable exige una revisión sin observaciones pendientes*/
  public boolean esFavorable() { return favorable; }

  public boolean aplicaA(TipoServicio servicio) { return servicios.contains(servicio); }

  /**antes de suspender o retirar, el Anexo 1 (checklist de suspensión) es obligatorio*/
  public boolean exigeChecklist() { return this == SUSPENSION || this == RETIRO; }
}
