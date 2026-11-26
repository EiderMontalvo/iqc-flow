package pe.com.iqc.flow.certificaciones.application;

import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import pe.com.iqc.flow.certificaciones.ImportacionCertificadosPersonales;
import pe.com.iqc.flow.certificaciones.MarcasPais;
import pe.com.iqc.flow.certificaciones.domain.CertificadoPersonal;
import pe.com.iqc.flow.certificaciones.domain.DocumentoPersona;
import pe.com.iqc.flow.certificaciones.domain.PersonaCertificada;
import pe.com.iqc.flow.certificaciones.infrastructure.CertificadoPersonalRepository;
import pe.com.iqc.flow.certificaciones.infrastructure.PersonaCertificadaRepository;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;

/**certificados de personas (cursos y competencias): búsqueda, registro, corrección, anulación, PDF y resumen*/
@Service
public class ServicioCertificadosPersonales implements ImportacionCertificadosPersonales {
  private static final String ENTIDAD = "CERTIFICADO_PERSONAL";

  private final CertificadoPersonalRepository certificados;
  private final PersonaCertificadaRepository personas;
  private final ServicioDocumentos documentos;
  private final MarcasPais marcas;
  private final ActorActual actorActual;
  private final Bitacora bitacora;

  public ServicioCertificadosPersonales(CertificadoPersonalRepository certificados, PersonaCertificadaRepository personas,
      ServicioDocumentos documentos, MarcasPais marcas, ActorActual actorActual, Bitacora bitacora) {
    this.certificados = certificados;
    this.personas = personas;
    this.documentos = documentos;
    this.marcas = marcas;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
  }

  public record PersonaRespuesta(UUID id, String codigoPais, String tipoDocumento, String numeroDocumento,
      String nombres, String correo, String telefono) {
  }

  public record CertificadoPersonalRespuesta(UUID id, String codigo, CertificadoPersonal.Estado estado,
      PersonaRespuesta persona, String curso, String normaReferencia, String normaAnio, String subtitulo,
      String descripcion, BigDecimal duracionHoras, String modalidad, LocalDate fechaInicio, LocalDate fechaFin,
      LocalDate fechaEmision, String entrenador, String motivoAnulacion, UUID documentoPdfId,
      String urlDocumentoLegado, boolean tienePdf, String urlConsulta, Instant actualizadoEn, long version) {
  }

  public record SolicitudPersona(
      @NotBlank @Size(min = 2, max = 2) String codigoPais,
      @NotBlank @Size(max = 20) String tipoDocumento,
      @NotBlank @Size(max = 20) String numeroDocumento,
      @NotBlank @Size(max = 255) String nombres,
      @Email @Size(max = 255) String correo,
      @Size(max = 30) String telefono) {
  }

  public record SolicitudDatos(
      @NotBlank @Size(max = 255) String curso,
      @Size(max = 120) String normaReferencia,
      @Pattern(regexp = "\\d{4}") String normaAnio,
      @Size(max = 255) String subtitulo,
      @Size(max = 4000) String descripcion,
      @DecimalMin(value = "0.5") BigDecimal duracionHoras,
      @Size(max = 50) String modalidad,
      LocalDate fechaInicio,
      LocalDate fechaFin,
      @NotNull LocalDate fechaEmision,
      @Size(max = 255) String entrenador) {
  }

  public record SolicitudRegistro(@NotNull @Valid SolicitudPersona persona,
      @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9/._-]+") String codigo,
      @NotNull @Valid SolicitudDatos datos, UUID documentoPdfId) {
  }

  public record SolicitudCorreccion(@NotNull @Valid SolicitudDatos datos, @NotBlank @Size(max = 500) String motivo,
      @NotNull Long version) {
  }

  public record SolicitudAnulacion(@NotBlank @Size(max = 500) String motivo, @NotNull Long version) {
  }

  public record SolicitudPdfPersonal(@NotNull UUID documentoId, @Size(max = 500) String motivo, @NotNull Long version) {
  }

  public record ResumenPais(String codigoPais, long emitidos, long anulados) {
  }

  /**patron: la regla del número (expresión regular completa, ya en mayúsculas), para que el panel valide igual que la API*/
  public record TipoDocumentoRespuesta(String codigo, String etiqueta, String ayuda, String patron) {
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:leer')")
  public RespuestaPaginada<CertificadoPersonalRespuesta> buscar(String texto, String pais, CertificadoPersonal.Estado estado,
      int pagina, int tamano) {
    UUID org = actorActual.requerido().organizacionId();
    String t = texto == null ? "" : texto.trim();
    String p = pais == null || pais.isBlank() ? null : pais.trim().toUpperCase(Locale.ROOT);
    List<UUID> personasTexto = t.isEmpty() ? List.of() : personas.buscarIds(org, t);
    Specification<CertificadoPersonal> spec = (raiz, consulta, cb) -> {
      List<Predicate> c = new ArrayList<>();
      c.add(cb.equal(raiz.get("organizacionId"), org));
      if (estado != null) c.add(cb.equal(raiz.get("estado"), estado));
      if (!t.isEmpty()) {
        String patron = "%" + t.toLowerCase(Locale.ROOT) + "%";
        Predicate porTexto = cb.or(cb.like(cb.lower(raiz.get("codigo")), patron), cb.like(cb.lower(raiz.get("curso")), patron));
        c.add(personasTexto.isEmpty() ? porTexto : cb.or(porTexto, raiz.get("personaId").in(personasTexto)));
      }
      if (p != null) {
        var sub = consulta.subquery(UUID.class);
        var persona = sub.from(PersonaCertificada.class);
        sub.select(persona.get("id")).where(cb.equal(persona.get("codigoPais"), p), cb.equal(persona.get("organizacionId"), org));
        c.add(raiz.get("personaId").in(sub));
      }
      return cb.and(c.toArray(Predicate[]::new));
    };
    PageRequest paginacion = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
        Sort.by(Sort.Order.desc("fechaEmision"), Sort.Order.desc("codigo")));
    Page<CertificadoPersonal> resultado = certificados.findAll(spec, paginacion);
    return new RespuestaPaginada<>(respuestas(org, resultado.getContent()), resultado.getNumber(), resultado.getSize(),
        resultado.getTotalElements(), resultado.getTotalPages());
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:leer')")
  public CertificadoPersonalRespuesta obtener(UUID id) {
    UUID org = actorActual.requerido().organizacionId();
    return respuestas(org, List.of(cargar(org, id))).get(0);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:leer')")
  public List<ResumenPais> resumen() {
    UUID org = actorActual.requerido().organizacionId();
    Map<String, long[]> porPais = new LinkedHashMap<>();
    for (Object[] fila : certificados.contarPorPaisYEstado(org)) {
      long[] cuenta = porPais.computeIfAbsent((String) fila[0], k -> new long[2]);
      cuenta["ANULADO".equals(fila[1]) ? 1 : 0] += ((Number) fila[2]).longValue();
    }
    return porPais.entrySet().stream().map(e -> new ResumenPais(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
  }

  @PreAuthorize("hasAuthority('certificados:leer')")
  public Map<String, List<TipoDocumentoRespuesta>> tiposDocumento() {
    Map<String, List<TipoDocumentoRespuesta>> r = new LinkedHashMap<>();
    DocumentoPersona.tipos().forEach((pais, tipos) -> r.put(pais, tipos.values().stream()
        .map(t -> new TipoDocumentoRespuesta(t.codigo(), t.etiqueta(), t.ayuda(), t.regla().pattern())).toList()));
    return r;
  }

  /**datos de una persona ya registrada, para completar el formulario al escribir su documento*/
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:leer')")
  public PersonaRespuesta personaPorDocumento(String pais, String tipo, String numero) {
    UUID org = actorActual.requerido().organizacionId();
    DocumentoPersona d = DocumentoPersona.validar(pais, tipo, numero);
    return personas.findByOrganizacionIdAndCodigoPaisAndTipoDocumentoAndNumeroDocumento(org, d.pais(), d.tipo(), d.numero())
        .map(ServicioCertificadosPersonales::persona)
        .orElseThrow(() -> new RecursoNoEncontradoException("Persona", d.tipo() + " " + d.numero()));
  }

  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoPersonalRespuesta registrar(SolicitudRegistro s) {
    UsuarioAutenticado actor = actorActual.requerido();
    UUID org = actor.organizacionId();
    String codigo = s.codigo().trim().toUpperCase(Locale.ROOT);
    if (certificados.existsByOrganizacionIdAndCodigoIgnoreCase(org, codigo)) {
      throw new ConflictoException("CODIGO_DUPLICADO", "Ya existe un certificado personal con el código " + codigo + ".");
    }
    if (s.documentoPdfId() != null) documentos.exigir(org, s.documentoPdfId(), CategoriaDocumento.CERTIFICADO_PDF);
    PersonaCertificada persona = personaDe(org, s.persona());
    CertificadoPersonal c = certificados.saveAndFlush(new CertificadoPersonal(org, persona.getId(), codigo,
        datos(s.datos()), s.documentoPdfId(), null, null, null, null));
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_PERSONAL_REGISTRADO").entidad(ENTIDAD, c.getId())
        .estadoNuevo(Map.of("codigo", codigo, "curso", c.getCurso(), "persona", persona.getId().toString())));
    return respuestas(org, List.of(c)).get(0);
  }

  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoPersonalRespuesta corregir(UUID id, SolicitudCorreccion s) {
    UUID org = actorActual.requerido().organizacionId();
    CertificadoPersonal c = cargarVersion(org, id, s.version());
    Map<String, Object> antes = Map.of("curso", c.getCurso(), "fechaEmision", c.getFechaEmision().toString());
    c.corregir(datos(s.datos()));
    certificados.saveAndFlush(c);
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_PERSONAL_CORREGIDO").entidad(ENTIDAD, c.getId())
        .motivo(s.motivo().trim()).estadoAnterior(antes)
        .estadoNuevo(Map.of("curso", c.getCurso(), "fechaEmision", c.getFechaEmision().toString())));
    return respuestas(org, List.of(c)).get(0);
  }

  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoPersonalRespuesta anular(UUID id, SolicitudAnulacion s) {
    UUID org = actorActual.requerido().organizacionId();
    CertificadoPersonal c = cargarVersion(org, id, s.version());
    c.anular(s.motivo().trim());
    certificados.saveAndFlush(c);
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_PERSONAL_ANULADO").entidad(ENTIDAD, c.getId())
        .motivo(s.motivo().trim()).estadoNuevo(Map.of("estado", "ANULADO")));
    return respuestas(org, List.of(c)).get(0);
  }

  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoPersonalRespuesta adjuntarPdf(UUID id, SolicitudPdfPersonal s) {
    UUID org = actorActual.requerido().organizacionId();
    CertificadoPersonal c = cargarVersion(org, id, s.version());
    documentos.exigir(org, s.documentoId(), CategoriaDocumento.CERTIFICADO_PDF);
    UUID anterior = c.getDocumentoPdfId();
    c.asignarPdf(s.documentoId());
    certificados.saveAndFlush(c);
    Map<String, Object> antes = new LinkedHashMap<>();
    antes.put("documentoPdfId", anterior);
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_PERSONAL_PDF").entidad(ENTIDAD, c.getId())
        .motivo(s.motivo() == null || s.motivo().isBlank() ? null : s.motivo().trim())
        .estadoAnterior(antes).estadoNuevo(Map.of("documentoPdfId", s.documentoId().toString())));
    return respuestas(org, List.of(c)).get(0);
  }

  // Importación del histórico (sin sesión: la invoca el importador con la organización y el responsable)

  @Override
  @Transactional
  public boolean importar(PersonaLegado p, CertificadoLegado c, UUID organizacionId) {
    if (certificados.findByFuenteLegadoAndIdLegado(p.fuente(), c.idLegado()).isPresent()) return false;
    DocumentoPersona documento = DocumentoPersona.validar(p.codigoPais(), p.tipoDocumento(), p.numeroDocumento());
    PersonaCertificada persona = personas.findByFuenteLegadoAndIdLegado(p.fuente(), p.idLegado())
        .or(() -> personas.findByOrganizacionIdAndCodigoPaisAndTipoDocumentoAndNumeroDocumento(organizacionId,
            documento.pais(), documento.tipo(), documento.numero()))
        .orElseGet(() -> personas.saveAndFlush(new PersonaCertificada(organizacionId, documento, p.nombres(), p.correo(),
            p.telefono(), p.fuente(), p.idLegado())));
    CertificadoPersonal nuevo = new CertificadoPersonal(organizacionId, persona.getId(), c.codigo().toUpperCase(Locale.ROOT),
        new CertificadoPersonal.Datos(c.curso(), c.normaReferencia(), c.normaAnio(), c.subtitulo(), c.descripcion(),
            c.duracionHoras(), c.modalidad(), c.fechaInicio(), c.fechaFin(), c.fechaEmision(), c.entrenador()),
        null, c.urlDocumento(), c.tokenPdf(), p.fuente(), c.idLegado());
    if (c.anulado()) nuevo.anular(c.motivoAnulacion() == null ? "Anulado en la plataforma anterior" : c.motivoAnulacion());
    certificados.saveAndFlush(nuevo);
    return true;
  }

  // Apoyo

  private PersonaCertificada personaDe(UUID org, SolicitudPersona s) {
    DocumentoPersona d = DocumentoPersona.validar(s.codigoPais(), s.tipoDocumento(), s.numeroDocumento());
    String nombres = s.nombres().trim().replaceAll("\\s+", " ");
    PersonaCertificada persona = personas.findByOrganizacionIdAndCodigoPaisAndTipoDocumentoAndNumeroDocumento(org,
        d.pais(), d.tipo(), d.numero()).orElse(null);
    if (persona == null) {
      return personas.saveAndFlush(new PersonaCertificada(org, d, nombres, limpio(s.correo()), limpio(s.telefono()), null, null));
    }
    persona.actualizarContacto(nombres, limpio(s.correo()) != null ? limpio(s.correo()) : persona.getCorreo(),
        limpio(s.telefono()) != null ? limpio(s.telefono()) : persona.getTelefono());
    return personas.saveAndFlush(persona);
  }

  private static CertificadoPersonal.Datos datos(SolicitudDatos d) {
    return new CertificadoPersonal.Datos(d.curso().trim(), limpio(d.normaReferencia()), limpio(d.normaAnio()),
        limpio(d.subtitulo()), limpio(d.descripcion()), d.duracionHoras(), limpio(d.modalidad()), d.fechaInicio(),
        d.fechaFin(), d.fechaEmision(), limpio(d.entrenador()));
  }

  private CertificadoPersonal cargar(UUID org, UUID id) {
    return certificados.findById(id).filter(c -> c.getOrganizacionId().equals(org))
        .orElseThrow(() -> new RecursoNoEncontradoException("Certificado personal", id));
  }

  private CertificadoPersonal cargarVersion(UUID org, UUID id, Long version) {
    CertificadoPersonal c = cargar(org, id);
    if (version == null || version != c.getVersion()) {
      throw new ConflictoException("CONFLICTO_VERSION",
          "El certificado fue modificado por otra persona. Recarga los datos y vuelve a intentarlo.");
    }
    return c;
  }

  private List<CertificadoPersonalRespuesta> respuestas(UUID org, List<CertificadoPersonal> lista) {
    Map<UUID, PersonaCertificada> porId = personas.findByIdIn(lista.stream().map(CertificadoPersonal::getPersonaId).toList())
        .stream().collect(Collectors.toMap(PersonaCertificada::getId, Function.identity()));
    Map<String, String> consultaPorPais = new LinkedHashMap<>();
    return lista.stream().map(c -> {
      PersonaCertificada p = porId.get(c.getPersonaId());
      String pais = p == null ? "PE" : p.getCodigoPais();
      String base = consultaPorPais.computeIfAbsent(pais, k -> marcas.marca(org, k).urlConsulta());
      return new CertificadoPersonalRespuesta(c.getId(), c.getCodigo(), c.getEstado(), p == null ? null : persona(p),
          c.getCurso(), c.getNormaReferencia(), c.getNormaAnio(), c.getSubtitulo(), c.getDescripcion(), c.getDuracionHoras(),
          c.getModalidad(), c.getFechaInicio(), c.getFechaFin(), c.getFechaEmision(), c.getEntrenador(),
          c.getMotivoAnulacion(), c.getDocumentoPdfId(), c.getUrlDocumentoLegado(),
          c.getDocumentoPdfId() != null || c.getUrlDocumentoLegado() != null,
          c.getUrlDocumentoLegado() != null ? c.getUrlDocumentoLegado() : base + "personal-pdf/" + c.getTokenPdf(),
          c.getActualizadoEn(), c.getVersion());
    }).toList();
  }

  private static PersonaRespuesta persona(PersonaCertificada p) {
    return new PersonaRespuesta(p.getId(), p.getCodigoPais(), p.getTipoDocumento(), p.getNumeroDocumento(), p.getNombres(),
        p.getCorreo(), p.getTelefono());
  }

  private static String limpio(String texto) { return texto == null || texto.isBlank() ? null : texto.trim(); }
}
