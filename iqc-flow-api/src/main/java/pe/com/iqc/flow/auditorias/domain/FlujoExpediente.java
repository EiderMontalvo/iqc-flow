package pe.com.iqc.flow.auditorias.domain;

import static pe.com.iqc.flow.auditorias.domain.EstadoExpediente.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *transiciones permitidas del expediente. Una recertificación o ampliación puede omitir la Etapa 1 / Fase I.
 *Desde cualquier estado abierto se puede cancelar. Las decisiones de certificación (EN_DECISION → EMITIDO)
 *se controlarán con el registro de decisiones del siguiente incremento
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
      Map.entry(EN_DECISION, EnumSet.of(EMITIDO)),
      Map.entry(EMITIDO, EnumSet.of(ENTREGADO)));

  /**estados en los que ya existe un certificado: se puede registrar la fecha de certificación y el ciclo*/
  public static final Set<EstadoExpediente> CON_CERTIFICADO = EnumSet.of(CERTIFICADO_RECIBIDO, TRADUCIDO, EMITIDO, ENTREGADO);

  private FlujoExpediente() {
  }

  /**en IQC Global decide India; en INACAL y SAE (o si aún no se sabe) decide IQC Perú*/
  public static EstadoExpediente estadoInicial(boolean decideIndia) {
    return decideIndia ? SOLICITUD_ENVIADA : EN_PLANIFICACION;
  }

  public static Set<EstadoExpediente> siguientes(EstadoExpediente actual, boolean decideIndia, TipoServicio tipo) {
    if (actual == ENTREGADO || actual == CANCELADO) return EnumSet.noneOf(EstadoExpediente.class);
    EnumSet<EstadoExpediente> permitidos = EnumSet.copyOf(
        (decideIndia ? INDIA : PROPIO).getOrDefault(actual, EnumSet.noneOf(EstadoExpediente.class)));
    if (tipo != TipoServicio.CERTIFICACION_INICIAL) {
      if (actual == PROPOSAL_FIRMADO) permitidos.add(PAQUETE_FASE2_RECIBIDO);
      if (actual == EN_PLANIFICACION) permitidos.add(ETAPA2_REALIZADA);
    }
    permitidos.add(CANCELADO);
    return permitidos;
  }

  public static void validar(EstadoExpediente actual, EstadoExpediente nuevo, boolean decideIndia, TipoServicio tipo) {
    if (!siguientes(actual, decideIndia, tipo).contains(nuevo)) {
      throw new ReglaNegocioException("TRANSICION_INVALIDA",
          "No se puede pasar de " + actual + " a " + nuevo + " en este expediente.");
    }
  }
}
