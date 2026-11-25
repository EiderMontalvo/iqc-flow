package pe.com.iqc.flow.cotizaciones.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.cotizaciones.application.ServicioCotizaciones;
import pe.com.iqc.flow.cotizaciones.domain.Cotizacion;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas.NuevaTareaAutomatica;

/**
 *alerta de cotizaciones sin respuesta: cuando una enviada lleva N días (Configuración, COTIZACION_DIAS_SIN_RESPUESTA)
 *sin respuesta, crea una tarea «Recordatorio de cotización» a su responsable, una sola vez por envío. Si el cliente
 *responde o se anula, la tarea abierta se cancela (ServicioCotizaciones)
 */
@Component
public class DetectorCotizacionesSinRespuesta {
  private static final Logger log = LoggerFactory.getLogger(DetectorCotizacionesSinRespuesta.class);
  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final CotizacionRepository cotizaciones;
  private final ParametrosOrganizacion parametros;
  private final ProgramadorTareas tareas;
  private final Clock reloj;

  public DetectorCotizacionesSinRespuesta(CotizacionRepository cotizaciones, ParametrosOrganizacion parametros,
      ProgramadorTareas tareas, Clock reloj) {
    this.cotizaciones = cotizaciones;
    this.parametros = parametros;
    this.tareas = tareas;
    this.reloj = reloj;
  }

  @Scheduled(fixedDelayString = "${iqc.cotizaciones.revision-sin-respuesta:PT1H}", initialDelayString = "PT2M")
  @Transactional
  public void ejecutarProgramado() {
    int n = revisar();
    if (n > 0) log.info("Recordatorios de cotizaciones sin respuesta creados: {}", n);
  }

  @Transactional
  public int revisar() {
    LocalDate hoy = LocalDate.now(reloj);
    Instant ahora = Instant.now(reloj);
    int creados = 0;
    // El menor plazo configurable es 3 días: las enviadas hace menos ni se revisan
    List<Cotizacion> candidatas = cotizaciones.bloquearSinRespuesta(hoy.minusDays(3), PageRequest.of(0, 200));
    for (Cotizacion c : candidatas) {
      int dias;
      try { dias = Integer.parseInt(parametros.valor(c.getOrganizacionId(), ParametroOrganizacion.COTIZACION_DIAS_SIN_RESPUESTA)); }
      catch (NumberFormatException e) { dias = 7; }
      if (c.getEnviadaEn().isAfter(hoy.minusDays(dias))) continue;
      tareas.crearTareaAutomatica(new NuevaTareaAutomatica(c.getOrganizacionId(),
          "Seguimiento de la cotización " + c.getNumero(),
          "Se envió el " + FECHA.format(c.getEnviadaEn()) + " y el cliente aún no responde. Contáctalo y registra su respuesta en la cotización.",
          "RECORDATORIO_COTIZACION", "COTIZACION", c.getId(), c.getClienteId(), c.getResponsableId(), hoy,
          ServicioCotizaciones.claveRecordatorio(c.getId())));
      c.marcarRecordatorio(ahora);
      creados++;
    }
    return creados;
  }
}
