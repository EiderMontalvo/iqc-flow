package pe.com.iqc.flow.seguimientos.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.ClienteSinMovimiento;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.Referencia;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.ResumenCentro;
import pe.com.iqc.flow.seguimientos.domain.EstadoTarea;
import pe.com.iqc.flow.seguimientos.domain.TareaSeguimiento;
import pe.com.iqc.flow.seguimientos.infrastructure.ActividadRepository;
import pe.com.iqc.flow.seguimientos.infrastructure.TareaRepository;

/**contadores del panel y detección de clientes sin movimiento*/
@Service
public class CentroSeguimientos {
  private final TareaRepository tareas;
  private final ActividadRepository actividades;
  private final ConsultaClientes clientes;
  private final ServicioTareas servicioTareas;
  private final AccesoTareas acceso;
  private final ActorActual actorActual;
  private final Clock reloj;

  public CentroSeguimientos(TareaRepository tareas, ActividadRepository actividades, ConsultaClientes clientes,
      ServicioTareas servicioTareas, AccesoTareas acceso, ActorActual actorActual, Clock reloj) {
    this.tareas = tareas;
    this.actividades = actividades;
    this.clientes = clientes;
    this.servicioTareas = servicioTareas;
    this.acceso = acceso;
    this.actorActual = actorActual;
    this.reloj = reloj;
  }

  /**contadores de las tareas propias del usuario actual*/
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('seguimientos:leer')")
  public ResumenCentro resumen(int diasProximas) {
    UsuarioAutenticado actor = actorActual.requerido();
    LocalDate hoy = LocalDate.now(reloj);
    return new ResumenCentro(
        tareas.contarPorFechas(actor.organizacionId(), actor.id(), TareaSeguimiento.ABIERTAS, hoy, hoy),
        tareas.contarVencidas(actor.organizacionId(), actor.id(), TareaSeguimiento.ABIERTAS, hoy, LocalTime.now(reloj)),
        tareas.contarPorFechas(actor.organizacionId(), actor.id(), TareaSeguimiento.ABIERTAS, hoy.plusDays(1),
            hoy.plusDays(diasProximas)),
        tareas.countByOrganizacionIdAndResponsableUsuarioIdAndEstado(actor.organizacionId(), actor.id(),
            EstadoTarea.EN_ESPERA));
  }

  /**
   *prospectos y clientes activos sin actividad registrada en los últimos N días y sin tareas abiertas.
   *Sin seguimientos:leer-todos solo se consultan los clientes propios
   */
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('seguimientos:leer')")
  public List<ClienteSinMovimiento> clientesSinMovimiento(int dias, UUID responsableId) {
    UsuarioAutenticado actor = actorActual.requerido();
    UUID responsable = responsableId;
    if (!acceso.veTodas()) {
      if (responsableId != null && !responsableId.equals(actor.id())) {
        throw new AccessDeniedException("Consultar la cartera de otra persona requiere seguimientos:leer-todos");
      }
      responsable = actor.id();
    }
    Instant ahora = Instant.now(reloj);
    Instant limite = ahora.minus(Duration.ofDays(dias));
    Map<UUID, Instant> ultima = new HashMap<>();
    for (Object[] fila : actividades.ultimaActividadPorCliente(actor.organizacionId())) {
      ultima.put((UUID) fila[0], (Instant) fila[1]);
    }
    Set<UUID> conTareas = new HashSet<>(tareas.clientesConTareasAbiertas(actor.organizacionId(),
        TareaSeguimiento.ABIERTAS));
    List<ClienteReferencia> candidatos = clientes.listarVigentes(actor.organizacionId(), responsable).stream()
        .filter(c -> !conTareas.contains(c.id()))
        .filter(c -> {
          Instant referencia = ultima.getOrDefault(c.id(), c.creadoEn());
          return referencia.isBefore(limite);
        })
        .toList();
    Map<UUID, Referencia> personas = servicioTareas.personas(candidatos.stream()
        .map(ClienteReferencia::responsableId).collect(Collectors.toSet()));
    return candidatos.stream()
        .map(c -> {
          Instant ultimaActividad = ultima.get(c.id());
          Instant referencia = ultimaActividad != null ? ultimaActividad : c.creadoEn();
          return new ClienteSinMovimiento(c.id(), c.codigo(), c.razonSocial(), personas.get(c.responsableId()),
              ultimaActividad, Duration.between(referencia, ahora).toDays());
        })
        .sorted(Comparator.comparingLong(ClienteSinMovimiento::diasSinMovimiento).reversed())
        .toList();
  }
}
