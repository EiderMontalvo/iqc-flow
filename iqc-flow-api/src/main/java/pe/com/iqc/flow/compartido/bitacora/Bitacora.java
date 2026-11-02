package pe.com.iqc.flow.compartido.bitacora;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.com.iqc.flow.compartido.infrastructure.FiltroCorrelacion;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import tools.jackson.databind.json.JsonMapper;

/**punto único para registrar y consultar la bitácora de actividad. */
@Service
public class Bitacora {
  private final RegistroBitacoraRepository repositorio;
  private final ActorActual actorActual;
  private final JsonMapper json;
  private final Clock reloj;

  public Bitacora(RegistroBitacoraRepository repositorio, ActorActual actorActual, JsonMapper json, Clock reloj) {
    this.repositorio = repositorio;
    this.actorActual = actorActual;
    this.json = json;
    this.reloj = reloj;
  }

  /** Registra dentro de la transacción en curso: si la operación se revierte, el registro también. */
  @Transactional
  public void registrar(EntradaBitacora entrada) { repositorio.save(construir(entrada)); }

  /** Registra en una transacción independiente; se usa para denegaciones y fallos que revierten la operación. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrarAparte(EntradaBitacora entrada) { repositorio.save(construir(entrada)); }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('bitacora:leer')")
  public Page<RegistroBitacora> consultar(String entidadTipo, String entidadId, String accion, int pagina, int tamano) {
    UUID organizacionId = actorActual.requerido().organizacionId();
    Specification<RegistroBitacora> filtro = (raiz, consulta, cb) -> {
      List<Predicate> condiciones = new ArrayList<>();
      // Los registros sin organización son eventos de seguridad previos a identificar al usuario
      condiciones.add(cb.or(cb.equal(raiz.get("organizacionId"), organizacionId), cb.isNull(raiz.get("organizacionId"))));
      if (entidadTipo != null && !entidadTipo.isBlank()) condiciones.add(cb.equal(raiz.get("entidadTipo"), entidadTipo));
      if (entidadId != null && !entidadId.isBlank()) condiciones.add(cb.equal(raiz.get("entidadId"), entidadId));
      if (accion != null && !accion.isBlank()) condiciones.add(cb.equal(raiz.get("accion"), accion));
      return cb.and(condiciones.toArray(Predicate[]::new));
    };
    return repositorio.findAll(filtro, PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "ocurridoEn")));
  }

  private RegistroBitacora construir(EntradaBitacora entrada) {
    Optional<UsuarioAutenticado> actor = actorActual.obtener();
    HttpServletRequest solicitud = solicitudActual();
    UUID actorId = entrada.getActorUsuarioId() != null
        ? entrada.getActorUsuarioId() : actor.map(UsuarioAutenticado::id).orElse(null);
    UUID organizacionId = entrada.getOrganizacionId() != null
        ? entrada.getOrganizacionId() : actor.map(UsuarioAutenticado::organizacionId).orElse(null);
    TipoActor tipoActor = entrada.getTipoActor() != null
        ? entrada.getTipoActor() : (actorId != null ? TipoActor.USUARIO : TipoActor.SISTEMA);
    OrigenOperacion origen = entrada.getOrigen() != null
        ? entrada.getOrigen() : (solicitud != null ? OrigenOperacion.WEB : OrigenOperacion.PROGRAMADOR);
    return new RegistroBitacora(
        organizacionId,
        Instant.now(reloj),
        actorId,
        tipoActor,
        origen,
        entrada.getAccion(),
        entrada.getEntidadTipo(),
        recortar(entrada.getEntidadId(), 200),
        aJson(entrada.getEstadoAnterior()),
        aJson(entrada.getEstadoNuevo()),
        entrada.getResultado(),
        recortar(entrada.getMotivo(), 500),
        MDC.get(FiltroCorrelacion.CLAVE_MDC),
        solicitud != null ? recortar(solicitud.getRemoteAddr(), 45) : null,
        solicitud != null ? recortar(solicitud.getHeader("User-Agent"), 500) : null);
  }

  private String aJson(Map<String, ?> estado) { return estado == null ? null : json.writeValueAsString(estado); }

  private static HttpServletRequest solicitudActual() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos
        ? atributos.getRequest() : null;
  }

  private static String recortar(String valor, int maximo) {
    return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
