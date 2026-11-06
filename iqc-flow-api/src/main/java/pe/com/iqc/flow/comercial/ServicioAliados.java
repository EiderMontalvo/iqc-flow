package pe.com.iqc.flow.comercial;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.comercial.domain.AliadoComercial;
import pe.com.iqc.flow.comercial.infrastructure.AliadoComercialRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;

/**aliados comerciales (SPONSOR). API pública: otros módulos consultan aliados con buscar()*/
@Service
public class ServicioAliados {
  private static final String ENTIDAD = "ALIADO_COMERCIAL";

  private final AliadoComercialRepository aliados;
  private final ActorActual actorActual;
  private final Bitacora bitacora;

  public ServicioAliados(AliadoComercialRepository aliados, ActorActual actorActual, Bitacora bitacora) {
    this.aliados = aliados;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('auditores:leer')")
  public List<AliadoRespuesta> listar() {
    return aliados.findByOrganizacionIdOrderByNombreAsc(actorActual.requerido().organizacionId()).stream()
        .map(AliadoRespuesta::de).toList();
  }

  @Transactional
  @PreAuthorize("hasAuthority('auditores:gestionar')")
  public AliadoRespuesta crear(SolicitudAliado solicitud) {
    UUID organizacionId = actorActual.requerido().organizacionId();
    if (aliados.existsByOrganizacionIdAndCodigo(organizacionId, solicitud.codigo().trim().toUpperCase())) {
      throw new ConflictoException("CODIGO_DUPLICADO", "Ya existe un aliado con el código " + solicitud.codigo() + ".");
    }
    AliadoComercial aliado = new AliadoComercial(organizacionId);
    aliado.actualizar(solicitud.codigo(), solicitud.nombre(), solicitud.comisionPorcentaje(),
        solicitud.activo() == null || solicitud.activo(), solicitud.notas());
    aliados.saveAndFlush(aliado);
    bitacora.registrar(EntradaBitacora.accion("ALIADO_REGISTRADO").entidad(ENTIDAD, aliado.getId()));
    return AliadoRespuesta.de(aliado);
  }

  @Transactional
  @PreAuthorize("hasAuthority('auditores:gestionar')")
  public AliadoRespuesta actualizar(UUID id, SolicitudAliado solicitud) {
    UUID organizacionId = actorActual.requerido().organizacionId();
    AliadoComercial aliado = aliados.findByIdAndOrganizacionId(id, organizacionId)
        .orElseThrow(() -> new RecursoNoEncontradoException("Aliado comercial", id));
    if (!Objects.equals(aliado.getVersion(), solicitud.version())) {
      throw new ConflictoException("CONFLICTO_VERSION", "El aliado fue modificado por otra persona. Recarga los datos.");
    }
    if (aliados.existsByOrganizacionIdAndCodigoAndIdNot(organizacionId, solicitud.codigo().trim().toUpperCase(), id)) {
      throw new ConflictoException("CODIGO_DUPLICADO", "Ya existe un aliado con el código " + solicitud.codigo() + ".");
    }
    aliado.actualizar(solicitud.codigo(), solicitud.nombre(), solicitud.comisionPorcentaje(),
        solicitud.activo() == null || solicitud.activo(), solicitud.notas());
    aliados.flush();
    bitacora.registrar(EntradaBitacora.accion("ALIADO_ACTUALIZADO").entidad(ENTIDAD, aliado.getId()));
    return AliadoRespuesta.de(aliado);
  }

  /**para otros módulos: aliado de la organización, si existe*/
  @Transactional(readOnly = true)
  public Optional<AliadoRespuesta> buscar(UUID organizacionId, UUID id) {
    return aliados.findByIdAndOrganizacionId(id, organizacionId).map(AliadoRespuesta::de);
  }

  /**la comisión es el porcentaje por defecto; cada servicio puede usar otro*/
  public record SolicitudAliado(
      @NotBlank @Size(max = 10) String codigo,
      @NotBlank @Size(max = 200) String nombre,
      @DecimalMin("0") @DecimalMax("100") BigDecimal comisionPorcentaje,
      Boolean activo,
      @Size(max = 1000) String notas,
      Long version) {
  }

  public record AliadoRespuesta(UUID id, String codigo, String nombre, BigDecimal comisionPorcentaje, boolean activo,
      String notas, long version) {
    static AliadoRespuesta de(AliadoComercial a) {
      return new AliadoRespuesta(a.getId(), a.getCodigo(), a.getNombre(), a.getComisionPorcentaje(), a.isActivo(),
          a.getNotas(), a.getVersion());
    }
  }
}
