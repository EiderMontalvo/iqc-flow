package pe.com.iqc.flow.auditores.application;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditores.ConsultaAuditores;
import pe.com.iqc.flow.auditores.RolAuditor;
import pe.com.iqc.flow.auditores.domain.Auditor;
import pe.com.iqc.flow.auditores.infrastructure.AuditorRepository;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;

/**registro de auditores, sus roles posibles, competencias por norma y vínculo con usuarios*/
@Service
public class ServicioAuditores implements ConsultaAuditores {
  private static final String ENTIDAD = "AUDITOR";

  private final AuditorRepository auditores;
  private final ServicioCatalogos catalogos;
  private final DirectorioUsuarios directorio;
  private final ActorActual actorActual;
  private final Bitacora bitacora;

  public ServicioAuditores(AuditorRepository auditores, ServicioCatalogos catalogos, DirectorioUsuarios directorio,
      ActorActual actorActual, Bitacora bitacora) {
    this.auditores = auditores;
    this.catalogos = catalogos;
    this.directorio = directorio;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('auditores:leer')")
  public Page<AuditorRespuesta> listar(String texto, RolAuditor rol, String norma, Boolean activo, int pagina,
      int tamano) {
    UUID organizacionId = actorActual.requerido().organizacionId();
    Specification<Auditor> filtro = (raiz, consulta, cb) -> {
      List<Predicate> c = new ArrayList<>();
      c.add(cb.equal(raiz.get("organizacionId"), organizacionId));
      if (texto != null && !texto.isBlank()) {
        c.add(cb.like(cb.lower(raiz.get("nombreCompleto")), "%" + texto.trim().toLowerCase() + "%"));
      }
      if (rol != null) c.add(cb.like(raiz.get("roles").as(String.class), "%" + rol.name() + "%"));
      if (norma != null && !norma.isBlank()) {
        consulta.distinct(true);
        c.add(cb.equal(raiz.join("normas", JoinType.INNER), norma));
      }
      if (activo != null) c.add(cb.equal(raiz.get("activo"), activo));
      return cb.and(c.toArray(Predicate[]::new));
    };
    return auditores.findAll(filtro, PageRequest.of(pagina, tamano, Sort.by("nombreCompleto")))
        .map(AuditorRespuesta::de);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('auditores:leer')")
  public AuditorRespuesta obtener(UUID id) { return AuditorRespuesta.de(cargar(id)); }

  @Transactional
  @PreAuthorize("hasAuthority('auditores:gestionar')")
  public AuditorRespuesta crear(SolicitudAuditor solicitud) {
    Auditor auditor = new Auditor(actorActual.requerido().organizacionId());
    aplicar(auditor, solicitud);
    auditores.saveAndFlush(auditor);
    bitacora.registrar(EntradaBitacora.accion("AUDITOR_REGISTRADO").entidad(ENTIDAD, auditor.getId())
        .estadoNuevo(instantanea(auditor)));
    return AuditorRespuesta.de(auditor);
  }

  @Transactional
  @PreAuthorize("hasAuthority('auditores:gestionar')")
  public AuditorRespuesta actualizar(UUID id, SolicitudAuditor solicitud) {
    Auditor auditor = cargar(id);
    if (!Objects.equals(auditor.getVersion(), solicitud.version())) {
      throw new ConflictoException("CONFLICTO_VERSION", "El auditor fue modificado por otra persona. Recarga los datos.");
    }
    Map<String, Object> antes = instantanea(auditor);
    aplicar(auditor, solicitud);
    auditores.flush();
    bitacora.registrar(EntradaBitacora.accion("AUDITOR_ACTUALIZADO").entidad(ENTIDAD, auditor.getId())
        .estadoAnterior(antes).estadoNuevo(instantanea(auditor)));
    return AuditorRespuesta.de(auditor);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, AuditorReferencia> buscarPorIds(UUID organizacionId, Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return auditores.findAllById(ids).stream()
        .filter(a -> a.getOrganizacionId().equals(organizacionId))
        .map(a -> new AuditorReferencia(a.getId(), a.getNombreCompleto(), a.getRoles(), a.getNormas(),
            a.getUsuarioId(), a.isActivo()))
        .collect(Collectors.toMap(AuditorReferencia::id, Function.identity()));
  }

  private void aplicar(Auditor auditor, SolicitudAuditor s) {
    Set<String> normasValidas = catalogos.normas().stream().map(ServicioCatalogos.NormaCatalogo::codigo)
        .collect(Collectors.toSet());
    Set<String> desconocidas = new TreeSet<>(s.normas() == null ? Set.of() : s.normas());
    desconocidas.removeAll(normasValidas);
    if (!desconocidas.isEmpty()) {
      throw new ReglaNegocioException("NORMA_INVALIDA", "Normas desconocidas: " + String.join(", ", desconocidas));
    }
    if (s.usuarioId() != null) {
      directorio.buscarActivo(auditor.getOrganizacionId(), s.usuarioId())
          .orElseThrow(() -> new ReglaNegocioException("USUARIO_INVALIDO", "El usuario indicado no existe o está deshabilitado."));
      auditores.findByUsuarioId(s.usuarioId()).filter(otro -> !otro.getId().equals(auditor.getId()))
          .ifPresent(otro -> {
            throw new ConflictoException("USUARIO_YA_VINCULADO",
                "El usuario ya está vinculado al auditor " + otro.getNombreCompleto() + ".");
          });
    }
    auditor.actualizar(s.nombreCompleto(), limpio(s.correo()), limpio(s.telefono()),
        s.codigoPais() == null ? "PE" : s.codigoPais().toUpperCase(), s.roles(), s.normas(), s.usuarioId(),
        s.activo() == null || s.activo(), limpio(s.notas()));
  }

  private Auditor cargar(UUID id) {
    return auditores.findByIdAndOrganizacionId(id, actorActual.requerido().organizacionId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Auditor", id));
  }

  private static Map<String, Object> instantanea(Auditor a) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("nombreCompleto", a.getNombreCompleto());
    datos.put("roles", a.getRoles().stream().map(Enum::name).sorted().toList());
    datos.put("normas", List.copyOf(a.getNormas()));
    datos.put("usuarioId", a.getUsuarioId());
    datos.put("activo", a.isActivo());
    return datos;
  }

  private static String limpio(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }

  /**las normas son códigos del catálogo (ISO_9001, ISO_45001…). La versión se exige al modificar*/
  public record SolicitudAuditor(
      @NotBlank @Size(max = 200) String nombreCompleto,
      @Email @Size(max = 320) String correo,
      @Size(max = 40) String telefono,
      @Pattern(regexp = "[A-Za-z]{2}") String codigoPais,
      @NotEmpty Set<@NotNull RolAuditor> roles,
      Set<@NotBlank String> normas,
      UUID usuarioId,
      Boolean activo,
      @Size(max = 1000) String notas,
      Long version) {
  }

  public record AuditorRespuesta(UUID id, String nombreCompleto, String correo, String telefono, String codigoPais,
      Set<RolAuditor> roles, Set<String> normas, UUID usuarioId, boolean activo, String notas, long version) {
    static AuditorRespuesta de(Auditor a) {
      return new AuditorRespuesta(a.getId(), a.getNombreCompleto(), a.getCorreo(), a.getTelefono(), a.getCodigoPais(),
          a.getRoles(), a.getNormas(), a.getUsuarioId(), a.isActivo(), a.getNotas(), a.getVersion());
    }
  }
}
