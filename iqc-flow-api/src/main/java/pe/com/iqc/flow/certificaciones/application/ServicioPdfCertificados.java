package pe.com.iqc.flow.certificaciones.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.ImpresionCertificados;
import pe.com.iqc.flow.auditorias.ImpresionCertificados.DatosImpresion;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.certificaciones.MarcasPais;
import pe.com.iqc.flow.certificaciones.MarcasPais.MarcaVigente;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.CertificadoPersonalRespuesta;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.SolicitudPdfPersonal;
import pe.com.iqc.flow.certificaciones.domain.CertificadoPersonal;
import pe.com.iqc.flow.certificaciones.domain.PersonaCertificada;
import pe.com.iqc.flow.certificaciones.infrastructure.CertificadoPersonalRepository;
import pe.com.iqc.flow.certificaciones.infrastructure.PersonaCertificadaRepository;
import pe.com.iqc.flow.certificaciones.infrastructure.PropiedadesCertificados;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.clientes.ConsultaClientes.DireccionReferencia;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.documentos.ServicioDocumentos.DocumentoReferencia;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.Acreditadora;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.CertificadoEmpresarial;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.CertificadoIqcGlobal;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.Firmante;

/**
 *PDF de los certificados dibujado desde plantilla («Desde plantilla»): vista previa sin guardar y generación, que deja
 *el PDF como versión vigente del certificado. Solo se genera de certificados ya emitidos y con el modo plantilla
 *encendido para la organización y para quien lo pide. Los de INACAL y SAE usan el diseño DDC-FRM-015 y los de IQC Global
 *(que emite India), el QF-12
 */
@Service
public class ServicioPdfCertificados {
  private static final String MOTIVO = "Generado desde plantilla";
  private static final Pattern CARACTERES_DE_ARCHIVO = Pattern.compile("[^A-Za-z0-9._-]+");

  private final ImpresionCertificados impresion;
  private final ConsultaClientes clientes;
  private final ServicioCatalogos catalogos;
  private final MarcasPais marcas;
  private final ServicioDocumentos documentos;
  private final GeneradorPdfCertificado generador;
  private final ParametrosOrganizacion parametros;
  private final PropiedadesCertificados propiedades;
  private final CertificadoPersonalRepository personales;
  private final PersonaCertificadaRepository personas;
  private final ServicioCertificadosPersonales servicioPersonales;
  private final ActorActual actorActual;
  private final Bitacora bitacora;

  public ServicioPdfCertificados(ImpresionCertificados impresion, ConsultaClientes clientes, ServicioCatalogos catalogos,
      MarcasPais marcas, ServicioDocumentos documentos, GeneradorPdfCertificado generador, ParametrosOrganizacion parametros,
      PropiedadesCertificados propiedades, CertificadoPersonalRepository personales, PersonaCertificadaRepository personas,
      ServicioCertificadosPersonales servicioPersonales, ActorActual actorActual, Bitacora bitacora) {
    this.impresion = impresion;
    this.clientes = clientes;
    this.catalogos = catalogos;
    this.marcas = marcas;
    this.documentos = documentos;
    this.generador = generador;
    this.parametros = parametros;
    this.propiedades = propiedades;
    this.personales = personales;
    this.personas = personas;
    this.servicioPersonales = servicioPersonales;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
  }

  public record VistaPrevia(String nombreArchivo, byte[] contenido) {
  }

  public record PdfGenerado(UUID certificadoId, UUID documentoId, String nombreArchivo, long tamanoBytes) {
  }

  /**
   *lo que hay escrito en el formulario de un certificado empresarial que aún no se registra: el PDF se dibuja con eso, sin
   *guardar nada. Solo se exige lo mínimo para que el diseño tenga sentido; los límites son los del registro real
   */
  public record BorradorEmpresarial(@NotNull UUID clienteId, @NotBlank String esquema, @NotBlank String norma,
      @NotBlank String edicion, @Size(max = 4000) String alcance, @Size(max = 64) String codigo,
      @NotNull LocalDate fechaOtorgamiento, @NotNull LocalDate fechaEmision, @NotNull LocalDate fechaExpiracion,
      LocalDate limiteVigilancia1, LocalDate limiteVigilancia2, @Size(max = 2) String paisMarca,
      @Size(max = 40) @jakarta.validation.constraints.Pattern(regexp = ImpresionCertificados.PATRON_SECTOR) String subdivision,
      @Size(max = 40) @jakarta.validation.constraints.Pattern(regexp = ImpresionCertificados.PATRON_SECTOR) String clase,
      @Size(max = 60) @jakarta.validation.constraints.Pattern(regexp = ImpresionCertificados.PATRON_PROCESOS) String procesosNoAplicables,
      @Size(max = 200) String anexos) {
  }

  /**el formulario del certificado personal tal como va: todo opcional, lo que falte sale con un guion en el PDF*/
  public record BorradorPersonal(@Size(max = 2) String codigoPais, @Size(max = 20) String tipoDocumento,
      @Size(max = 20) String numeroDocumento, @Size(max = 255) String nombres, @Size(max = 64) String codigo,
      @Size(max = 255) String curso, @Size(max = 120) String normaReferencia, @Size(max = 4) String normaAnio,
      @Size(max = 255) String subtitulo, @Size(max = 4000) String descripcion, BigDecimal duracionHoras,
      @Size(max = 50) String modalidad, LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaEmision,
      @Size(max = 255) String entrenador) {
  }

  // Empresarial

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:generar-pdf')")
  public VistaPrevia vistaPreviaEmpresarial(UUID certificadoId) {
    UUID org = exigirModo(ParametroOrganizacion.CERT_EMPRESARIAL_MODO_PLANTILLA);
    DatosImpresion datos = cargarEmpresarial(org, certificadoId);
    return new VistaPrevia(nombreArchivo(datos.codigo()), pdfEmpresarial(org, datos));
  }

  @Transactional
  @PreAuthorize("hasAuthority('certificados:generar-pdf')")
  public PdfGenerado generarEmpresarial(UUID certificadoId) {
    UsuarioAutenticado actor = actorActual.requerido();
    UUID org = exigirModo(ParametroOrganizacion.CERT_EMPRESARIAL_MODO_PLANTILLA);
    DatosImpresion datos = cargarEmpresarial(org, certificadoId);
    String nombre = nombreArchivo(datos.codigo());
    DocumentoReferencia documento = documentos.guardarGenerado(CategoriaDocumento.CERTIFICADO_PDF, nombre, pdfEmpresarial(org, datos));
    impresion.registrarPdfGenerado(org, certificadoId, documento.id(), actor.id(), MOTIVO);
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_PDF_DE_PLANTILLA").entidad("CERTIFICADO", certificadoId)
        .estadoNuevo(Map.of("codigo", datos.codigo(), "documentoId", documento.id().toString())));
    return new PdfGenerado(certificadoId, documento.id(), nombre, documento.tamanoBytes());
  }

  /**vista previa en vivo mientras se llena el formulario: nada se guarda, y vale solo si el modo plantilla está encendido*/
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:generar-pdf')")
  public VistaPrevia vistaPreviaBorradorEmpresarial(BorradorEmpresarial b) {
    UUID org = exigirModo(ParametroOrganizacion.CERT_EMPRESARIAL_MODO_PLANTILLA);
    List<LocalDate> limites = new ArrayList<>();
    if (b.limiteVigilancia1() != null) limites.add(b.limiteVigilancia1());
    if (b.limiteVigilancia2() != null) limites.add(b.limiteVigilancia2());
    String codigo = textoOGuion(b.codigo());
    DatosImpresion datos = new DatosImpresion(null, b.clienteId(), codigo, b.esquema(), b.norma(), b.edicion(), b.alcance(),
        "EMITIDO", 1, 1, b.fechaOtorgamiento(), b.fechaEmision(), b.fechaExpiracion(), limites, b.paisMarca(),
        b.subdivision(), b.clase(), b.procesosNoAplicables(), b.anexos());
    return new VistaPrevia(nombreArchivo(codigo), pdfEmpresarial(org, datos));
  }

  private DatosImpresion cargarEmpresarial(UUID org, UUID certificadoId) {
    DatosImpresion datos = impresion.datos(org, certificadoId)
        .orElseThrow(() -> new RecursoNoEncontradoException("Certificado", certificadoId));
    if (!"EMITIDO".equals(datos.estado())) {
      throw new ReglaNegocioException("CERTIFICADO_NO_EMITIDO", "El PDF se genera cuando el certificado está emitido.");
    }
    return datos;
  }

  /**IQC Global tiene su propio diseño (QF-12); INACAL y SAE comparten el DDC-FRM-015*/
  private byte[] pdfEmpresarial(UUID org, DatosImpresion d) {
    return "IQC_GLOBAL".equals(d.esquema()) ? generador.iqcGlobal(iqcGlobal(org, d)) : generador.empresarial(empresarial(org, d));
  }

  /**lo que el sistema ya sabe del titular, la marca y la norma, común a los dos diseños empresariales*/
  private record Base(ClienteReferencia cliente, MarcaVigente marca, String direccion, String sistemas, String designacion) {
  }

  private Base base(UUID org, DatosImpresion d) {
    ClienteReferencia cliente = clientes.buscar(org, d.clienteId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", d.clienteId()));
    MarcaVigente marca = marcas.marca(org, d.paisMarca() != null ? d.paisMarca() : cliente.codigoPais());
    String direccion = clientes.direccionesDeCliente(cliente.id()).values().stream()
        .filter(a -> "FISCAL".equals(a.tipo())).map(DireccionReferencia::direccion).findFirst().orElse(null);
    var edicion = catalogos.buscarEdicion(d.norma(), d.edicion())
        .orElseThrow(() -> new ReglaNegocioException("NORMA_INVALIDA", "La norma " + d.norma() + ":" + d.edicion() + " no está en el catálogo."));
    String sistemas = catalogos.normas().stream().filter(n -> n.codigo().equals(d.norma())).findFirst()
        .map(ServicioCatalogos.NormaCatalogo::titulo).orElse(edicion.nombreCorto());
    return new Base(cliente, marca, direccion, sistemas, edicion.designacion());
  }

  private CertificadoEmpresarial empresarial(UUID org, DatosImpresion d) {
    Base b = base(org, d);
    List<LocalDate> limites = d.limitesVigilancia();
    return new CertificadoEmpresarial(b.cliente().razonSocial(), b.direccion(),
        // «Sistemas de Gestión Antisoborno» (catálogo) → «Sistema de Gestión Antisoborno» (certificado)
        b.sistemas().replaceFirst("^Sistemas ", "Sistema "), b.designacion(), d.alcance(), d.codigo(), d.ciclo(),
        d.fechaOtorgamiento(), d.fechaEmision(), !limites.isEmpty() ? limites.get(0) : null,
        limites.size() > 1 ? limites.get(1) : null, d.fechaExpiracion(), acreditadora(d.esquema()),
        propiedades.acreditacion(d.esquema(), d.norma()).orElse(null), d.numeroVersion(), b.marca().urlConsulta(),
        lineas(b.marca().direccion()), contacto(b.marca()), firmante(false));
  }

  /**
   *el certificado de IQC Global lleva «Fecha de emisión inicial» y los datos del código de sector. shortcut: el sistema aún
   *no guarda subdivisión, clase, procesos no aplicables ni anexos; salen vacíos («NA» o un guion) hasta que el registro de
   *certificados los pida
   */
  private CertificadoIqcGlobal iqcGlobal(UUID org, DatosImpresion d) {
    Base b = base(org, d);
    List<LocalDate> limites = d.limitesVigilancia();
    return new CertificadoIqcGlobal(b.cliente().razonSocial(), b.direccion(), b.sistemas(), d.norma(), b.designacion(),
        d.alcance(), d.codigo(), d.fechaEmision(), !limites.isEmpty() ? limites.get(0) : null,
        limites.size() > 1 ? limites.get(1) : null, d.fechaExpiracion(), d.subdivision(), d.clase(), d.procesosNoAplicables(),
        d.anexos(), b.marca().urlConsulta());
  }

  private static Acreditadora acreditadora(String esquema) {
    return switch (esquema) {
      case "SAE" -> Acreditadora.SAE;
      case "INACAL" -> Acreditadora.INACAL;
      default -> throw new ReglaNegocioException("ESQUEMA_NO_GENERABLE", "No hay plantilla para el esquema " + esquema + ".");
    };
  }

  // Personal

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:generar-pdf')")
  public VistaPrevia vistaPreviaPersonal(UUID id) {
    UUID org = exigirModo(ParametroOrganizacion.CERT_PERSONAL_MODO_PLANTILLA);
    CertificadoPersonal c = cargarPersonal(org, id);
    return new VistaPrevia(nombreArchivo(c.getCodigo()), pdfPersonal(org, c));
  }

  /**además de certificados:generar-pdf pide certificados:gestionar, que es el permiso de cambiar el PDF de un certificado*/
  @Transactional
  @PreAuthorize("hasAuthority('certificados:generar-pdf')")
  public CertificadoPersonalRespuesta generarPersonal(UUID id) {
    UUID org = exigirModo(ParametroOrganizacion.CERT_PERSONAL_MODO_PLANTILLA);
    CertificadoPersonal c = cargarPersonal(org, id);
    exigirDatosDePlantilla(c);
    DocumentoReferencia documento = documentos.guardarGenerado(CategoriaDocumento.CERTIFICADO_PDF, nombreArchivo(c.getCodigo()),
        pdfPersonal(org, c));
    CertificadoPersonalRespuesta respuesta = servicioPersonales.adjuntarPdf(id, new SolicitudPdfPersonal(documento.id(), MOTIVO, c.getVersion()));
    bitacora.registrar(EntradaBitacora.accion("CERTIFICADO_PDF_DE_PLANTILLA").entidad("CERTIFICADO_PERSONAL", id)
        .estadoNuevo(Map.of("codigo", c.getCodigo(), "documentoId", documento.id().toString())));
    return respuesta;
  }

  /**
   *el PDF definitivo no sale con huecos: la plantilla imprime norma y año, duración, modalidad, fechas del curso y entrenador.
   *La vista previa sí acepta datos a medias (es para ver mientras se llena)
   */
  static void exigirDatosDePlantilla(CertificadoPersonal c) {
    java.util.List<String> faltan = new java.util.ArrayList<>();
    if (c.getNormaReferencia() == null || c.getNormaReferencia().isBlank()) faltan.add("norma de referencia");
    if (c.getNormaAnio() == null || c.getNormaAnio().isBlank()) faltan.add("año de la norma");
    if (c.getDuracionHoras() == null) faltan.add("horas");
    if (c.getModalidad() == null || c.getModalidad().isBlank()) faltan.add("modalidad");
    if (c.getFechaInicio() == null) faltan.add("fecha de inicio");
    if (c.getFechaFin() == null) faltan.add("fecha de fin");
    if (c.getEntrenador() == null || c.getEntrenador().isBlank()) faltan.add("entrenador");
    if (!faltan.isEmpty()) {
      throw new pe.com.iqc.flow.compartido.domain.ReglaNegocioException("DATOS_INCOMPLETOS_PLANTILLA",
          "Para generar el PDF con la plantilla falta: " + String.join(", ", faltan) + ". Complétalos con «Corregir datos».");
    }
  }

  /**vista previa en vivo del certificado personal: sin guardar nada ni crear a la persona*/
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:generar-pdf')")
  public VistaPrevia vistaPreviaBorradorPersonal(BorradorPersonal b) {
    UUID org = exigirModo(ParametroOrganizacion.CERT_PERSONAL_MODO_PLANTILLA);
    MarcaVigente marca = marcas.marca(org, b.codigoPais() == null || b.codigoPais().isBlank() ? "PE" : b.codigoPais());
    byte[] logo = marca.logoDocumentoId() == null ? null : documentos.leerContenido(org, marca.logoDocumentoId());
    String codigo = textoOGuion(b.codigo());
    byte[] pdf = generador.personal(new GeneradorPdfCertificado.CertificadoPersonal(textoOGuion(b.nombres()), b.tipoDocumento(),
        b.numeroDocumento(), textoOGuion(b.curso()), b.normaReferencia(), b.normaAnio(), b.subtitulo(), b.descripcion(),
        b.duracionHoras(), b.modalidad(), b.fechaInicio(), b.fechaFin(), b.fechaEmision() != null ? b.fechaEmision() : LocalDate.now(),
        b.entrenador(), codigo, marca.urlConsulta(), logo, firmante(true)));
    return new VistaPrevia(nombreArchivo(codigo), pdf);
  }

  private static String textoOGuion(String texto) { return texto == null || texto.isBlank() ? "—" : texto.strip(); }

  private CertificadoPersonal cargarPersonal(UUID org, UUID id) {
    CertificadoPersonal c = personales.findById(id).filter(x -> x.getOrganizacionId().equals(org))
        .orElseThrow(() -> new RecursoNoEncontradoException("Certificado personal", id));
    if (c.getEstado() != CertificadoPersonal.Estado.EMITIDO) {
      throw new ReglaNegocioException("CERTIFICADO_ANULADO", "Un certificado anulado no genera PDF.");
    }
    return c;
  }

  private byte[] pdfPersonal(UUID org, CertificadoPersonal c) {
    PersonaCertificada persona = personas.findById(c.getPersonaId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Persona", c.getPersonaId()));
    MarcaVigente marca = marcas.marca(org, persona.getCodigoPais());
    byte[] logo = marca.logoDocumentoId() == null ? null : documentos.leerContenido(org, marca.logoDocumentoId());
    return generador.personal(new GeneradorPdfCertificado.CertificadoPersonal(persona.getNombres(), persona.getTipoDocumento(),
        persona.getNumeroDocumento(), c.getCurso(), c.getNormaReferencia(), c.getNormaAnio(), c.getSubtitulo(),
        c.getDescripcion(), c.getDuracionHoras(), c.getModalidad(), c.getFechaInicio(), c.getFechaFin(), c.getFechaEmision(),
        c.getEntrenador(), c.getCodigo(), marca.urlConsulta(), logo, firmante(true)));
  }

  // Apoyo

  /**el modo plantilla debe estar encendido para la organización y para quien lo pide; devuelve la organización*/
  private UUID exigirModo(ParametroOrganizacion modo) {
    UsuarioAutenticado actor = actorActual.requerido();
    if (!parametros.activoPara(actor.organizacionId(), actor.id(), modo)) {
      throw new ReglaNegocioException("MODO_DESHABILITADO", "La generación desde plantilla está desactivada por la configuración.");
    }
    return actor.organizacionId();
  }

  /**solo se genera de certificados emitidos, así que la firma siempre se imprime; el tratamiento («Lic.») solo va en el personal*/
  private Firmante firmante(boolean conTitulo) {
    var f = propiedades.firmante();
    return new Firmante(conTitulo ? f.titulo() : null, f.nombre(), f.cargo(), f.empresa(), true);
  }

  private static List<String> lineas(String texto) {
    List<String> lineas = new ArrayList<>();
    if (texto != null) for (String l : texto.split("\\R")) if (!l.isBlank()) lineas.add(l.strip());
    return lineas;
  }

  /**«+51 923 980 697 | comercial@iqcperu.net»: lo que haya configurado en la marca*/
  private static String contacto(MarcaVigente marca) {
    return Optional.ofNullable(marca.telefono()).filter(t -> !t.isBlank()).map(t -> marca.correo() == null || marca.correo().isBlank()
        ? t : t + " | " + marca.correo()).orElse(marca.correo());
  }

  private static String nombreArchivo(String codigo) { return "Certificado_" + CARACTERES_DE_ARCHIVO.matcher(codigo).replaceAll("_") + ".pdf"; }
}
