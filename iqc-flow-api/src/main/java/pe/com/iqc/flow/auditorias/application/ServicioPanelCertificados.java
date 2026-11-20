package pe.com.iqc.flow.auditorias.application;

import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.Referencia;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.CertificadoDirecto;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.CertificadoPanel;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.FiltroCertificados;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.RegistroDirectoRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.SolicitudMarca;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.SolicitudPdf;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.SolicitudRegistroDirecto;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.TitularResumen;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.Vigencia;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.VersionPdf;
import pe.com.iqc.flow.auditorias.domain.Auditoria;
import pe.com.iqc.flow.auditorias.domain.Certificado;
import pe.com.iqc.flow.auditorias.domain.CertificadoPdf;
import pe.com.iqc.flow.auditorias.domain.Expediente;
import pe.com.iqc.flow.auditorias.domain.HistorialExpediente;
import pe.com.iqc.flow.auditorias.domain.NormaExpediente;
import pe.com.iqc.flow.auditorias.domain.TipoAuditoria;
import pe.com.iqc.flow.auditorias.domain.TipoServicio;
import pe.com.iqc.flow.auditorias.infrastructure.AuditoriaRepository;
import pe.com.iqc.flow.auditorias.infrastructure.CertificadoPdfRepository;
import pe.com.iqc.flow.auditorias.infrastructure.CertificadoRepository;
import pe.com.iqc.flow.auditorias.infrastructure.ExpedienteRepository;
import pe.com.iqc.flow.auditorias.infrastructure.HistorialExpedienteRepository;
import pe.com.iqc.flow.auditorias.infrastructure.PropiedadesExpedientes;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaEdicion;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.documentos.ServicioDocumentos.DocumentoReferencia;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas.NuevaTareaAutomatica;

/**
 *panel de certificados: búsqueda general (todas las marcas y países), exportación a Excel, registro directo como en
 *el panel anterior, versiones del PDF y marca con la que se muestra cada certificado. Las suspensiones, levantamientos
 *y retiros se registran como decisiones del expediente (ServicioDecisiones), con su checklist y control de imparcialidad
 */
@Service
public class ServicioPanelCertificados {
  private static final int DIAS_POR_VENCER = 90;
  private static final int MAXIMO_EXPORTACION = 20_000;
  private static final String PDF = "application/pdf";

  private final CertificadoRepository certificados;
  private final CertificadoPdfRepository pdfs;
  private final ExpedienteRepository expedientes;
  private final HistorialExpedienteRepository historial;
  private final AuditoriaRepository auditorias;
  private final ServicioCertificados servicioCertificados;
  private final PresentadorExpedientes presentador;
  private final ConsultaClientes clientes;
  private final ServicioCatalogos catalogos;
  private final ServicioDocumentos documentos;
  private final ProgramadorTareas programador;
  private final PropiedadesExpedientes propiedades;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioPanelCertificados(CertificadoRepository certificados, CertificadoPdfRepository pdfs,
      ExpedienteRepository expedientes, HistorialExpedienteRepository historial, AuditoriaRepository auditorias,
      ServicioCertificados servicioCertificados, PresentadorExpedientes presentador, ConsultaClientes clientes,
      ServicioCatalogos catalogos, ServicioDocumentos documentos, ProgramadorTareas programador,
      PropiedadesExpedientes propiedades, ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.certificados = certificados;
    this.pdfs = pdfs;
    this.expedientes = expedientes;
    this.historial = historial;
    this.auditorias = auditorias;
    this.servicioCertificados = servicioCertificados;
    this.presentador = presentador;
    this.clientes = clientes;
    this.catalogos = catalogos;
    this.documentos = documentos;
    this.programador = programador;
    this.propiedades = propiedades;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public RespuestaPaginada<CertificadoPanel> buscar(FiltroCertificados filtro, int pagina, int tamano) {
    UUID org = actorActual.requerido().organizacionId();
    PageRequest paginacion = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100), orden());
    Page<Certificado> resultado = certificados.findAll(especificacion(org, filtro), paginacion);
    List<CertificadoPanel> contenido = respuestas(org, resultado.getContent(), dias(filtro));
    return new RespuestaPaginada<>(contenido, resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public CertificadoPanel obtener(UUID certificadoId) {
    UUID org = actorActual.requerido().organizacionId();
    return respuestas(org, List.of(cargar(org, certificadoId)), DIAS_POR_VENCER).get(0);
  }

  /**exporta a Excel el resultado completo del filtro (sin paginar)*/
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public byte[] exportar(FiltroCertificados filtro) {
    UUID org = actorActual.requerido().organizacionId();
    List<Certificado> lista = certificados.findAll(especificacion(org, filtro),
        PageRequest.of(0, MAXIMO_EXPORTACION, orden())).getContent();
    List<CertificadoPanel> filas = respuestas(org, lista, dias(filtro));
    try (SXSSFWorkbook libro = new SXSSFWorkbook(200); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
      Sheet hoja = libro.createSheet("Certificados");
      CellStyle titulo = libro.createCellStyle();
      Font negrita = libro.createFont();
      negrita.setBold(true);
      titulo.setFont(negrita);
      CellStyle fecha = libro.createCellStyle();
      fecha.setDataFormat(libro.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
      String[] columnas = {"Código", "Norma", "Esquema", "Estado", "Vigencia", "Cliente", "Documento", "País cliente",
          "Marca", "Otorgamiento", "Emisión", "Expiración", "PDF", "Alcance"};
      Row encabezado = hoja.createRow(0);
      for (int i = 0; i < columnas.length; i++) {
        Cell celda = encabezado.createCell(i);
        celda.setCellValue(columnas[i]);
        celda.setCellStyle(titulo);
      }
      int n = 1;
      for (CertificadoPanel c : filas) {
        Row fila = hoja.createRow(n++);
        fila.createCell(0).setCellValue(c.codigo());
        fila.createCell(1).setCellValue(c.designacion());
        fila.createCell(2).setCellValue(c.esquema());
        fila.createCell(3).setCellValue(c.estado().name());
        fila.createCell(4).setCellValue(c.vigencia().name());
        fila.createCell(5).setCellValue(c.cliente() == null ? "" : c.cliente().razonSocial());
        fila.createCell(6).setCellValue(c.cliente() == null || c.cliente().numeroDocumento() == null ? ""
            : c.cliente().tipoDocumento() + " " + c.cliente().numeroDocumento());
        fila.createCell(7).setCellValue(c.cliente() == null ? "" : c.cliente().codigoPais());
        fila.createCell(8).setCellValue(c.paisMarca());
        fecha(fila, 9, c.fechaOtorgamiento(), fecha);
        fecha(fila, 10, c.fechaEmision(), fecha);
        fecha(fila, 11, c.fechaExpiracion(), fecha);
        fila.createCell(12).setCellValue(c.tienePdf() ? "Sí" : "No");
        fila.createCell(13).setCellValue(c.alcance() == null ? "" : c.alcance());
      }
      int[] anchos = {24, 18, 12, 13, 12, 40, 18, 8, 7, 12, 12, 12, 6, 60};
      for (int i = 0; i < anchos.length; i++) hoja.setColumnWidth(i, anchos[i] * 256);
      hoja.createFreezePane(0, 1);
      libro.write(salida);
      return salida.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo generar el Excel", e);
    }
  }

  /**
   *registra uno o más certificados ya emitidos (uno por norma) sin pasar por el flujo del expediente, como en el
   *panel anterior: emitidos por India, atrasados o correcciones. Crea el expediente ya entregado y, con los límites
   *de vigilancia, sus auditorías (y las tareas de las que aún no vencen)
   */
  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public RegistroDirectoRespuesta registrarDirecto(SolicitudRegistroDirecto s) {
    UsuarioAutenticado actor = actorActual.requerido();
    UUID org = actor.organizacionId();
    ClienteReferencia cliente = clientes.buscar(org, s.clienteId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", s.clienteId()));
    String esquema = s.esquema().trim().toUpperCase(Locale.ROOT);
    if (catalogos.esquema(esquema).isEmpty()) {
      throw new ReglaNegocioException("ESQUEMA_INVALIDO", "El esquema " + s.esquema() + " no está en el catálogo.");
    }
    if (!s.fechaExpiracion().isAfter(s.fechaOtorgamiento())) {
      throw new ReglaNegocioException("FECHAS_INVALIDAS", "La fecha de expiración debe ser posterior a la de otorgamiento.");
    }
    if (s.fechaEmision().isBefore(s.fechaOtorgamiento())) {
      throw new ReglaNegocioException("FECHAS_INVALIDAS", "La fecha de emisión no puede ser anterior a la de otorgamiento.");
    }
    Map<String, NormaExpediente> normas = new LinkedHashMap<>();
    for (CertificadoDirecto c : s.certificados()) {
      String norma = c.norma().trim().toUpperCase(Locale.ROOT);
      if (catalogos.buscarEdicion(norma, c.edicion().trim()).isEmpty()) {
        throw new ReglaNegocioException("NORMA_INVALIDA", "La norma " + c.norma() + ":" + c.edicion() + " no está en el catálogo.");
      }
      if (normas.putIfAbsent(norma, new NormaExpediente(norma, c.edicion().trim())) != null) {
        throw new ReglaNegocioException("NORMA_REPETIDA", "Cada norma va una sola vez: " + norma + " está repetida.");
      }
      servicioCertificados.exigirCodigoLibre(org, codigo(c.codigo()), norma, null);
    }
    boolean india = s.emitidoPorIndia() != null ? s.emitidoPorIndia() : "IQC_GLOBAL".equals(esquema);
    TipoServicio servicio = s.tipoServicio() != null ? s.tipoServicio() : TipoServicio.CERTIFICACION_INICIAL;
    UUID responsable = cliente.responsableId() != null ? cliente.responsableId() : actor.id();
    Instant ahora = Instant.now(reloj);
    String alcance = s.alcance() == null || s.alcance().isBlank() ? null : s.alcance().trim();
    Expediente e = expedientes.saveAndFlush(Expediente.registroDirecto(org, cliente.id(), esquema, servicio,
        normas.values(), alcance, s.fechaOtorgamiento(), s.fechaExpiracion(), responsable, actor.id(), s.motivo().trim(),
        ahora));
    historial.save(new HistorialExpediente(e.getId(), null, e.getEstado(), "Registro directo: " + s.motivo().trim(),
        actor.id(), ahora));
    List<Certificado> creados = new ArrayList<>();
    for (CertificadoDirecto d : s.certificados()) {
      NormaExpediente norma = normas.get(d.norma().trim().toUpperCase(Locale.ROOT));
      Certificado c = certificados.saveAndFlush(Certificado.registroDirecto(e, norma, codigo(d.codigo()), india,
          d.revisionAlcance() != null ? d.revisionAlcance() : 1, d.ciclo() != null ? d.ciclo() : 1,
          india ? null : s.fechaOtorgamiento().getYear(), alcance, s.fechaOtorgamiento(), s.fechaEmision(),
          s.fechaExpiracion()));
      servicioCertificados.sustituirAnteriores(c);
      if (d.documentoPdfId() != null) registrarPdf(org, c, d.documentoPdfId(), "Registro directo", actor.id(), ahora);
      creados.add(c);
    }
    int vigilancias = 0;
    int tareas = 0;
    LocalDate hoy = LocalDate.now(reloj);
    LocalDate[] limites = {s.limiteVigilancia1(), s.limiteVigilancia2()};
    for (int i = 0; i < limites.length; i++) {
      if (limites[i] == null) continue;
      auditorias.save(new Auditoria(e.getId(), i == 0 ? TipoAuditoria.VIGILANCIA_1 : TipoAuditoria.VIGILANCIA_2, limites[i]));
      vigilancias++;
      if (!limites[i].isBefore(hoy)) {
        programador.crearTareaAutomatica(new NuevaTareaAutomatica(org,
            "Programar " + (i + 1) + "ª vigilancia de " + e.getCodigo(),
            "La " + (i + 1) + "ª vigilancia debe realizarse antes del " + limites[i] + " (registro directo).",
            "PROGRAMAR_AUDITORIA", ServicioExpedientes.ENTIDAD, e.getId(), cliente.id(), responsable,
            limites[i].minusDays(propiedades.diasAnticipacionVigilancia()),
            "expediente:" + e.getId() + ":vigilancia:" + (i + 1)));
        tareas++;
      }
    }
    certificados.flush();
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_REGISTRO_DIRECTO").entidad(ServicioExpedientes.ENTIDAD, e.getId())
        .estadoNuevo(Map.of("motivo", s.motivo().trim(), "esquema", esquema,
            "certificados", creados.stream().map(Certificado::getCodigo).toList())));
    return new RegistroDirectoRespuesta(e.getId(), e.getCodigo(), respuestas(org, creados, DIAS_POR_VENCER),
        vigilancias, tareas);
  }

  /**sube una nueva versión del PDF; la anterior queda en el historial. La consulta pública muestra la vigente*/
  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoPanel adjuntarPdf(UUID certificadoId, SolicitudPdf s) {
    UsuarioAutenticado actor = actorActual.requerido();
    Certificado c = cargar(actor.organizacionId(), certificadoId);
    ServicioExpedientes.verificarVersion(c.getVersion(), s.version());
    registrarPdf(actor.organizacionId(), c, s.documentoId(), s.motivo(), actor.id(), Instant.now(reloj));
    certificados.flush();
    return respuestas(actor.organizacionId(), List.of(c), DIAS_POR_VENCER).get(0);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public List<VersionPdf> historialPdf(UUID certificadoId) {
    UUID org = actorActual.requerido().organizacionId();
    Certificado c = cargar(org, certificadoId);
    List<CertificadoPdf> versiones = pdfs.findByCertificadoIdOrderByNumeroDesc(c.getId());
    Set<UUID> personas = new HashSet<>();
    versiones.forEach(v -> personas.add(v.getSubidoPor()));
    Map<UUID, Referencia> refs = presentador.personas(org, personas);
    return versiones.stream().map(v -> {
      DocumentoReferencia d = documentos.buscar(org, v.getDocumentoId()).orElse(null);
      return new VersionPdf(v.getNumero(), v.getDocumentoId(), d == null ? null : d.nombreOriginal(),
          d == null ? 0 : d.tamanoBytes(), d == null ? null : d.sha256(), v.getMotivo(),
          refs.getOrDefault(v.getSubidoPor(), new Referencia(v.getSubidoPor(), null)), v.getSubidoEn(),
          v.getDocumentoId().equals(c.getDocumentoPdfId()));
    }).toList();
  }

  /**elige a mano la marca con que se muestra el certificado (p. ej. cliente peruano atendido por IQC Ecuador)*/
  @Transactional
  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public CertificadoPanel cambiarMarca(UUID certificadoId, SolicitudMarca s) {
    UUID org = actorActual.requerido().organizacionId();
    Certificado c = cargar(org, certificadoId);
    ServicioExpedientes.verificarVersion(c.getVersion(), s.version());
    String pais = s.pais() == null ? null : s.pais().trim().toUpperCase(Locale.ROOT);
    if (pais != null && catalogos.pais(pais).isEmpty()) {
      throw new ReglaNegocioException("PAIS_INVALIDO", "El país " + s.pais() + " no está en el catálogo.");
    }
    String anterior = c.getPaisMarca();
    c.cambiarMarca(pais);
    certificados.flush();
    Map<String, Object> antes = new LinkedHashMap<>();
    antes.put("paisMarca", anterior);
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("paisMarca", pais);
    despues.put("motivo", s.motivo().trim());
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_MARCA_CAMBIADA").entidad(ServicioCertificados.ENTIDAD, c.getId())
        .estadoAnterior(antes).estadoNuevo(despues));
    return respuestas(org, List.of(c), DIAS_POR_VENCER).get(0);
  }

  private void registrarPdf(UUID org, Certificado c, UUID documentoId, String motivo, UUID actor, Instant ahora) {
    DocumentoReferencia d = documentos.exigir(org, documentoId, CategoriaDocumento.CERTIFICADO_PDF);
    if (!PDF.equals(d.tipoContenido())) {
      throw new ReglaNegocioException("FORMATO_NO_ADMITIDO", "El certificado debe subirse en PDF.");
    }
    if (documentoId.equals(c.getDocumentoPdfId())) {
      throw new ReglaNegocioException("PDF_YA_VIGENTE", "Ese PDF ya es el vigente del certificado.");
    }
    int numero = pdfs.countByCertificadoId(c.getId()) + 1;
    String limpio = motivo == null || motivo.isBlank() ? null : motivo.trim();
    pdfs.save(new CertificadoPdf(c.getId(), documentoId, numero, limpio, actor, ahora));
    c.asignarPdf(documentoId);
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_PDF_ACTUALIZADO").entidad(ServicioCertificados.ENTIDAD, c.getId())
        .estadoNuevo(Map.of("codigo", c.getCodigo(), "version", numero, "documentoId", documentoId.toString(),
            "sha256", d.sha256())));
  }

  private Specification<Certificado> especificacion(UUID org, FiltroCertificados f) {
    LocalDate hoy = LocalDate.now(reloj);
    int dias = dias(f);
    Set<UUID> porTexto = vacio(f.texto()) ? Set.of() : clientes.idsPorTexto(org, f.texto());
    String pais = vacio(f.pais()) ? null : f.pais().trim().toUpperCase(Locale.ROOT);
    Set<UUID> porPais = pais == null ? Set.of() : clientes.idsPorPais(org, pais);
    return (raiz, consulta, cb) -> {
      List<Predicate> condiciones = new ArrayList<>();
      condiciones.add(cb.equal(raiz.get("organizacionId"), org));
      if (!vacio(f.texto())) {
        Predicate porCodigo = cb.like(cb.upper(raiz.get("codigo")), "%" + f.texto().trim().toUpperCase(Locale.ROOT) + "%");
        condiciones.add(porTexto.isEmpty() ? porCodigo : cb.or(porCodigo, raiz.get("clienteId").in(porTexto)));
      }
      if (!vacio(f.esquema())) condiciones.add(cb.equal(raiz.get("esquemaCodigo"), f.esquema().trim().toUpperCase(Locale.ROOT)));
      if (!vacio(f.norma())) condiciones.add(cb.equal(raiz.get("normaCodigo"), f.norma().trim().toUpperCase(Locale.ROOT)));
      if (f.estado() != null) condiciones.add(cb.equal(raiz.get("estado"), f.estado()));
      if (pais != null) {
        Predicate manual = cb.equal(raiz.get("paisMarca"), pais);
        condiciones.add(porPais.isEmpty() ? manual
            : cb.or(manual, cb.and(cb.isNull(raiz.get("paisMarca")), raiz.get("clienteId").in(porPais))));
      }
      if (f.vigencia() != null) {
        var expiracion = raiz.<LocalDate>get("fechaExpiracion");
        condiciones.add(switch (f.vigencia()) {
          case VIGENTE -> cb.greaterThanOrEqualTo(expiracion, hoy);
          case POR_VENCER -> cb.between(expiracion, hoy, hoy.plusDays(dias));
          case VENCIDO -> cb.lessThan(expiracion, hoy);
          case SIN_FECHA -> cb.isNull(expiracion);
        });
      }
      return cb.and(condiciones.toArray(Predicate[]::new));
    };
  }

  private List<CertificadoPanel> respuestas(UUID org, List<Certificado> lista, int dias) {
    Map<UUID, ClienteReferencia> refs = clientes.buscarPorIds(org,
        lista.stream().map(Certificado::getClienteId).collect(java.util.stream.Collectors.toSet()));
    Map<String, String> designaciones = new LinkedHashMap<>();
    LocalDate hoy = LocalDate.now(reloj);
    return lista.stream().map(c -> {
      ClienteReferencia cli = refs.get(c.getClienteId());
      TitularResumen titular = cli == null ? null
          : new TitularResumen(cli.id(), cli.razonSocial(), cli.tipoDocumento(), cli.numeroDocumento(), cli.codigoPais());
      String paisMarca = c.getPaisMarca() != null ? c.getPaisMarca() : cli == null ? null : cli.codigoPais();
      String designacion = designaciones.computeIfAbsent(c.getNormaCodigo() + ":" + c.getEdicion(), k -> catalogos
          .buscarEdicion(c.getNormaCodigo(), c.getEdicion()).map(NormaEdicion::designacion).orElse(k));
      return new CertificadoPanel(c.getId(), c.getExpedienteId(), c.getCodigo(), c.getNormaCodigo(), designacion,
          c.getEsquemaCodigo(), c.isEmitidoPorIndia(), c.getEstado(), vigencia(c.getFechaExpiracion(), hoy, dias), titular,
          paisMarca, c.getPaisMarca() != null, c.getAlcance(), c.getFechaOtorgamiento(), c.getFechaEmision(),
          c.getFechaExpiracion(), c.getDocumentoPdfId() != null || c.getUrlDocumentoLegado() != null,
          c.getDocumentoPdfId(), c.getUrlDocumentoLegado(), c.getVersion());
    }).toList();
  }

  static Vigencia vigencia(LocalDate expiracion, LocalDate hoy, int dias) {
    if (expiracion == null) return Vigencia.SIN_FECHA;
    if (expiracion.isBefore(hoy)) return Vigencia.VENCIDO;
    return expiracion.isAfter(hoy.plusDays(dias)) ? Vigencia.VIGENTE : Vigencia.POR_VENCER;
  }

  private Certificado cargar(UUID org, UUID id) {
    return certificados.findByIdAndOrganizacionId(id, org)
        .orElseThrow(() -> new RecursoNoEncontradoException("Certificado", id));
  }

  private static Sort orden() {
    return Sort.by(Sort.Order.desc("fechaEmision").nullsLast(), Sort.Order.asc("codigo"), Sort.Order.asc("normaCodigo"));
  }

  private static int dias(FiltroCertificados f) {
    return f.diasPorVencer() == null ? DIAS_POR_VENCER : Math.max(1, Math.min(f.diasPorVencer(), 730));
  }

  private static String codigo(String codigo) { return codigo.trim().toUpperCase(Locale.ROOT); }

  private static boolean vacio(String texto) { return texto == null || texto.isBlank(); }

  private static void fecha(Row fila, int columna, LocalDate valor, CellStyle estilo) {
    Cell celda = fila.createCell(columna);
    if (valor == null) return;
    celda.setCellValue(valor);
    celda.setCellStyle(estilo);
  }
}
