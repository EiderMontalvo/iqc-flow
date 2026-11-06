package pe.com.iqc.flow.seguimientos.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.ActividadRespuesta;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.Referencia;
import pe.com.iqc.flow.seguimientos.application.DtosSeguimientos.SolicitudActividad;
import pe.com.iqc.flow.seguimientos.domain.Actividad;
import pe.com.iqc.flow.seguimientos.domain.DireccionActividad;
import pe.com.iqc.flow.seguimientos.domain.OrigenTarea;
import pe.com.iqc.flow.seguimientos.infrastructure.ActividadRepository;

/**historial de interacciones con cada cliente: llamadas, correos, reuniones, visitas y notas*/
@Service
public class ServicioActividades {
  /**tolerancia para relojes de equipos ligeramente adelantados*/
  private static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(5);

  private final ActividadRepository actividades;
  private final AccesoTareas acceso;
  private final ConsultaClientes clientes;
  private final ServicioTareas servicioTareas;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioActividades(ActividadRepository actividades, AccesoTareas acceso, ConsultaClientes clientes,
      ServicioTareas servicioTareas, ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.actividades = actividades;
    this.acceso = acceso;
    this.clientes = clientes;
    this.servicioTareas = servicioTareas;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional
  @PreAuthorize("hasAuthority('seguimientos:escribir')")
  public ActividadRespuesta registrar(SolicitudActividad solicitud) {
    UsuarioAutenticado actor = actorActual.requerido();
    ClienteReferencia cliente = cargarCliente(actor.organizacionId(), solicitud.clienteId());
    Instant ahora = Instant.now(reloj);
    Instant ocurrida = solicitud.ocurridaEn() != null ? solicitud.ocurridaEn() : ahora;
    if (ocurrida.isAfter(ahora.plus(TOLERANCIA_FUTURO))) {
      throw new ReglaNegocioException("FECHA_FUTURA", "Una actividad no puede registrarse con fecha futura.");
    }
    if (solicitud.contactoId() != null && !clientes.contactoPerteneceACliente(cliente.id(), solicitud.contactoId())) {
      throw new ReglaNegocioException("CONTACTO_INVALIDO", "El contacto no pertenece a este cliente.");
    }
    if (solicitud.tareaId() != null) {
      var tarea = acceso.cargarVisible(solicitud.tareaId());
      if (!cliente.id().equals(tarea.getClienteId())) {
        throw new ReglaNegocioException("TAREA_INVALIDA", "La tarea no corresponde a este cliente.");
      }
    }
    Actividad actividad = actividades.save(new Actividad(actor.organizacionId(), cliente.id(), solicitud.tipo(),
        solicitud.direccion() != null ? solicitud.direccion() : DireccionActividad.SALIENTE, ocurrida,
        solicitud.resumen(), ServicioTareas.limpio(solicitud.resultado()), solicitud.contactoId(), solicitud.tareaId(),
        OrigenTarea.MANUAL, actor.id()));
    actividades.flush();
    bitacora.registrar(EntradaBitacora.accion("ACTIVIDAD_REGISTRADA")
        .entidad("CLIENTE", cliente.id())
        .estadoNuevo(Map.of("actividadId", actividad.getId(), "tipo", actividad.getTipo().name(),
            "direccion", actividad.getDireccion().name())));
    return respuesta(actividad, servicioTareas.personas(java.util.Set.of(actor.id())));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('seguimientos:leer')")
  public Page<ActividadRespuesta> listarDeCliente(UUID clienteId, int pagina, int tamano) {
    UsuarioAutenticado actor = actorActual.requerido();
    ClienteReferencia cliente = cargarCliente(actor.organizacionId(), clienteId);
    Page<Actividad> resultado = actividades.findByOrganizacionIdAndClienteIdOrderByOcurridaEnDesc(
        actor.organizacionId(), cliente.id(), PageRequest.of(pagina, tamano));
    Map<UUID, Referencia> personas = servicioTareas.personas(resultado.getContent().stream()
        .map(Actividad::getCreadoPor).filter(Objects::nonNull).collect(Collectors.toSet()));
    return resultado.map(actividad -> respuesta(actividad, personas));
  }

  private ClienteReferencia cargarCliente(UUID organizacionId, UUID clienteId) {
    return clientes.buscar(organizacionId, clienteId)
        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", clienteId));
  }

  private static ActividadRespuesta respuesta(Actividad a, Map<UUID, Referencia> personas) {
    return new ActividadRespuesta(a.getId(), a.getClienteId(), a.getTipo(), a.getDireccion(), a.getOcurridaEn(),
        a.getResumen(), a.getResultado(), a.getContactoId(), a.getTareaId(), a.getOrigen(),
        a.getCreadoPor() == null ? null : personas.get(a.getCreadoPor()), a.getCreadoEn());
  }
}
