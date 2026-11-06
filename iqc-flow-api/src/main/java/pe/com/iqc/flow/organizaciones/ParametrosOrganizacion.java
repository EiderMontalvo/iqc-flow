package pe.com.iqc.flow.organizaciones;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.organizaciones.domain.ValorParametro;
import pe.com.iqc.flow.organizaciones.infrastructure.ValorParametroRepository;

/**API pública de parámetros de la organización: lectura para todos los módulos y cambio solo con organizacion:configurar*/
@Service
public class ParametrosOrganizacion {
  private final ValorParametroRepository valores;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ParametrosOrganizacion(ValorParametroRepository valores, ActorActual actorActual, Bitacora bitacora,
      Clock reloj) {
    this.valores = valores;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  public record ParametroRespuesta(ParametroOrganizacion clave, String valor, boolean porDefecto,
      List<String> admitidos, UUID actualizadoPor, Instant actualizadoEn) {
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
    return respuesta(actor.organizacionId(), parametro);
  }

  private ParametroRespuesta respuesta(UUID organizacionId, ParametroOrganizacion p) {
    return valores.findById(new ValorParametro.Clave(organizacionId, p.name()))
        .map(v -> new ParametroRespuesta(p, v.getValor(), false, p.admitidos(), v.getActualizadoPor(), v.getActualizadoEn()))
        .orElse(new ParametroRespuesta(p, p.porDefecto(), true, p.admitidos(), null, null));
  }
}
