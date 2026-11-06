package pe.com.iqc.flow.auditorias.domain;

import static pe.com.iqc.flow.auditorias.domain.EstadoExpediente.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *transiciones permitidas del expediente. Una recertificación o ampliación puede omitir la Etapa 1 / Fase I.
 *Desde cualquier estado abierto se puede cancelar. Algunas transiciones solo las hace el sistema:
 *EN_DECISION → EN_EMISION o RECHAZADO (al registrar la decisión), EN_EMISION → EMITIDO (al firmarse el último
 *certificado) y → ENTREGADO (al registrar la entrega, que exige pagos conformes y la encuesta aplicada)
 */
public final class FlujoExpediente {
  private static final Map<EstadoExpediente, Set<EstadoExpediente>> INDIA = Map.ofEntries(
      Map.entry(SOLICITUD_ENVIADA, EnumSet.of(PROPOSAL_RECIBIDO)),
      Map.entry(PROPOSAL_RECIBIDO, EnumSet.of(PROPOSAL_FIRMADO)),
      Map.entry(PROPOSAL_FIRMADO, EnumSet.of(PAQUETE_FASE1_RECIBIDO)),
      Map.entry(PAQUETE_FASE1_RECIBIDO, EnumSet.of(PAQUETE_FASE1_ENVIADO)),
      Map.entry(PAQUETE_FASE1_ENVIADO, EnumSet.of(PAQUETE_FASE2_RECIBIDO, OBSERVADO)),
      Map.entry(PAQUETE_FASE2_RECIBIDO, EnumSet.of(PAQUETE_FASE2_ENVIADO)),
      Map.entry(PAQUETE_FASE2_ENVIADO, EnumSet.of(OBSERVADO, CERTIFICADO_RECIBIDO)),
      // Tras corregir las observaciones se reenvía el paquete correspondiente
      Map.entry(OBSERVADO, EnumSet.of(PAQUETE_FASE1_ENVIADO, PAQUETE_FASE2_ENVIADO)),
      Map.entry(CERTIFICADO_RECIBIDO, EnumSet.of(TRADUCIDO)),
      Map.entry(TRADUCIDO, EnumSet.of(ENTREGADO)));

  private static final Map<EstadoExpediente, Set<EstadoExpediente>> PROPIO = Map.ofEntries(
      Map.entry(EN_PLANIFICACION, EnumSet.of(ETAPA1_REALIZADA)),
      Map.entry(ETAPA1_REALIZADA, EnumSet.of(ETAPA2_REALIZADA)),
      Map.entry(ETAPA2_REALIZADA, EnumSet.of(EXPEDIENTE_VERIFICADO)),
      Map.entry(EXPEDIENTE_VERIFICADO, EnumSet.of(EN_DECISION)),
      Map.entry(EN_DECISION, EnumSet.of(EN_EMISION, RECHAZADO)),
      Map.entry(EN_EMISION, EnumSet.of(EMITIDO)),
      Map.entry(EMITIDO, EnumSet.of(ENTREGADO)));

  /**estados a los que solo se llega por una acción del sistema (decisión, firma o entrega), no por transición manual*/
  public static final Set<EstadoExpediente> AUTOMATICOS = EnumSet.of(EN_EMISION, RECHAZADO, EMITIDO, ENTREGADO);

  /**estados finales: no admiten más transiciones*/
  public static final Set<EstadoExpediente> FINALES = EnumSet.of(ENTREGADO, RECHAZADO, CANCELADO);

  /**estados en los que ya existe (o se está emitiendo) un certificado: se puede registrar la fecha de certificación*/
  public static final Set<EstadoExpediente> CON_CERTIFICADO =
      EnumSet.of(CERTIFICADO_RECIBIDO, TRADUCIDO, EN_EMISION, EMITIDO, ENTREGADO);

  private FlujoExpediente() {
  }

  /**en IQC Global decide India; en INACAL y SAE (o si aún no se sabe) decide IQC Perú*/
  public static EstadoExpediente estadoInicial(boolean decideIndia) {
    return decideIndia ? SOLICITUD_ENVIADA : EN_PLANIFICACION;
  }

  /**todas las transiciones del flujo desde el estado actual (manuales y del sistema)*/
  public static Set<EstadoExpediente> siguientes(EstadoExpediente actual, boolean decideIndia, TipoServicio tipo) {
    if (FINALES.contains(actual)) return EnumSet.noneOf(EstadoExpediente.class);
    EnumSet<EstadoExpediente> permitidos = EnumSet.copyOf(
        (decideIndia ? INDIA : PROPIO).getOrDefault(actual, EnumSet.noneOf(EstadoExpediente.class)));
    if (tipo != TipoServicio.CERTIFICACION_INICIAL) {
      if (actual == PROPOSAL_FIRMADO) permitidos.add(PAQUETE_FASE2_RECIBIDO);
      if (actual == EN_PLANIFICACION) permitidos.add(ETAPA2_REALIZADA);
    }
    permitidos.add(CANCELADO);
    return permitidos;
  }

  /**transiciones que una persona puede hacer con POST /transicion*/
  public static Set<EstadoExpediente> siguientesManuales(EstadoExpediente actual, boolean decideIndia, TipoServicio tipo) {
    Set<EstadoExpediente> permitidos = siguientes(actual, decideIndia, tipo);
    permitidos.removeAll(AUTOMATICOS);
    return permitidos;
  }

  /**valida una transición manual; las del sistema se rechazan indicando qué acción las produce*/
  public static void validarManual(EstadoExpediente actual, EstadoExpediente nuevo, boolean decideIndia,
      TipoServicio tipo) {
    if (AUTOMATICOS.contains(nuevo) && siguientes(actual, decideIndia, tipo).contains(nuevo)) {
      throw new ReglaNegocioException("TRANSICION_AUTOMATICA", switch (nuevo) {
        case EN_EMISION, RECHAZADO -> "Registra la decisión de certificación: el expediente avanzará solo.";
        case EMITIDO -> "El expediente pasa a EMITIDO cuando se firman todos sus certificados.";
        default -> "Registra la entrega: exige pagos conformes y la encuesta de satisfacción aplicada.";
      });
    }
    validar(actual, nuevo, decideIndia, tipo);
  }

  public static void validar(EstadoExpediente actual, EstadoExpediente nuevo, boolean decideIndia, TipoServicio tipo) {
    if (!siguientes(actual, decideIndia, tipo).contains(nuevo)) {
      throw new ReglaNegocioException("TRANSICION_INVALIDA",
          "No se puede pasar de " + actual + " a " + nuevo + " en este expediente.");
    }
  }
}
