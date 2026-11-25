package pe.com.iqc.flow.organizaciones;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.organizaciones.domain.ValorParametro;
import pe.com.iqc.flow.organizaciones.infrastructure.DesactivacionesPorPersona;
import pe.com.iqc.flow.organizaciones.infrastructure.ValorParametroRepository;

/**API pública de parámetros de la organización: lectura para todos los módulos y cambio solo con organizacion:configurar*/
@Service
public class ParametrosOrganizacion {
  private final ValorParametroRepository valores;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;
  private final ApplicationEventPublisher eventos;
  private final DesactivacionesPorPersona porPersona;

  public ParametrosOrganizacion(ValorParametroRepository valores, ActorActual actorActual, Bitacora bitacora,
      Clock reloj, ApplicationEventPublisher eventos, DesactivacionesPorPersona porPersona) {
    this.valores = valores;
    this.porPersona = porPersona;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
    this.eventos = eventos;
  }

  /**true si el parámetro SI/NO está en SI*/
  @Transactional(readOnly = true)
  public boolean activo(UUID organizacionId, ParametroOrganizacion parametro) {
    return "SI".equals(valor(organizacionId, parametro));
  }

  /**
   *true si la opción vale para esa persona: encendida para la organización y no apagada para ella. Es lo que deben
   *consultar los módulos cuando la opción afecta a quien hace la solicitud
   */
  @Transactional(readOnly = true)
  public boolean activoPara(UUID organizacionId, UUID usuarioId, ParametroOrganizacion parametro) {
    return activo(organizacionId, parametro)
        && !(parametro.porPersona() && usuarioId != null && porPersona.desactivado(usuarioId, parametro.name()));
  }

  /**porPersona: si se puede apagar a una persona; desactivadoPara: a quiénes se les apagó*/
  public record ParametroRespuesta(ParametroOrganizacion clave, String valor, boolean porDefecto,
      List<String> admitidos, UUID actualizadoPor, Instant actualizadoEn, boolean porPersona, List<UUID> desactivadoPara) {
  }

  /**valor vigente del parámetro, o su valor por defecto si nunca se configuró*/
  @Transactional(readOnly = true)
  public String valor(UUID organizacionId, ParametroOrganizacion parametro) {
    return valores.findById(new ValorParametro.Clave(organizacionId, parametro.name()))
        .map(ValorParametro::getValor).orElse(parametro.porDefecto());
  }

  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public List<ParametroRespuesta> listar() {
    UUID organizacionId = actorActual.requerido().organizacionId();
    return Arrays.stream(ParametroOrganizacion.values()).map(p -> respuesta(organizacionId, p)).toList();
  }

  @Transactional
  @PreAuthorize("hasAuthority('organizacion:configurar')")
  public ParametroRespuesta cambiar(ParametroOrganizacion parametro, String valor) {
    if (valor == null || !parametro.admitidos().contains(valor)) {
      throw new ReglaNegocioException("VALOR_NO_ADMITIDO",
          "Valores admitidos para " + parametro + ": " + String.join(", ", parametro.admitidos()) + ".");
    }
    UsuarioAutenticado actor = actorActual.requerido();
    String anterior = valor(actor.organizacionId(), parametro);
    Instant ahora = Instant.now(reloj);
    ValorParametro.Clave clave = new ValorParametro.Clave(actor.organizacionId(), parametro.name());
    valores.findById(clave).ifPresentOrElse(v -> v.cambiar(valor, actor.id(), ahora),
        () -> valores.save(new ValorParametro(actor.organizacionId(), parametro.name(), valor, actor.id(), ahora)));
    valores.flush();
    bitacora.registrar(EntradaBitacora.accion("PARAMETRO_CAMBIADO").entidad("PARAMETRO", parametro.name())
        .estadoAnterior(Map.of("valor", anterior)).estadoNuevo(Map.of("valor", valor)));
    if (!anterior.equals(valor)) eventos.publishEvent(new ParametroCambiado(actor.organizacionId(), parametro, anterior, valor));
    return respuesta(actor.organizacionId(), parametro);
  }

  /**apaga (habilitado=false) o devuelve una opción a una persona concreta, sin tocar al resto de la organización*/
  @Transactional
  @PreAuthorize("hasAuthority('organizacion:configurar')")
  public ParametroRespuesta cambiarParaPersona(ParametroOrganizacion parametro, UUID usuarioId, boolean habilitado) {
    if (!parametro.porPersona()) {
      throw new ReglaNegocioException("PARAMETRO_NO_POR_PERSONA", "Esta opción se configura solo para toda la organización.");
    }
    UsuarioAutenticado actor = actorActual.requerido();
    if (!porPersona.perteneceA(actor.organizacionId(), usuarioId)) {
      throw new ReglaNegocioException("USUARIO_NO_ENCONTRADO", "La persona no pertenece a la organización.");
    }
    boolean cambio = habilitado ? porPersona.reactivar(usuarioId, parametro.name())
        : porPersona.desactivar(actor.organizacionId(), usuarioId, parametro.name(), actor.id(), Instant.now(reloj));
    if (cambio) {
      bitacora.registrar(EntradaBitacora.accion("PARAMETRO_PERSONA_CAMBIADO").entidad("USUARIO", usuarioId)
          .estadoAnterior(Map.of("parametro", parametro.name(), "habilitado", !habilitado))
          .estadoNuevo(Map.of("parametro", parametro.name(), "habilitado", habilitado)));
    }
    return respuesta(actor.organizacionId(), parametro);
  }

  private ParametroRespuesta respuesta(UUID organizacionId, ParametroOrganizacion p) {
    List<UUID> apagados = p.porPersona() ? porPersona.personas(organizacionId, p.name()) : List.of();
    return valores.findById(new ValorParametro.Clave(organizacionId, p.name()))
        .map(v -> new ParametroRespuesta(p, v.getValor(), false, p.admitidos(), v.getActualizadoPor(), v.getActualizadoEn(),
            p.porPersona(), apagados))
        .orElse(new ParametroRespuesta(p, p.porDefecto(), true, p.admitidos(), null, null, p.porPersona(), apagados));
  }
}
