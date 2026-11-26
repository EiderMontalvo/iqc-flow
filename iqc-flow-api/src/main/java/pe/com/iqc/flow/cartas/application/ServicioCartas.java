package pe.com.iqc.flow.cartas.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.CertificadosPublicables;
import pe.com.iqc.flow.auditorias.CertificadosPublicables.CertificadoPublicable;
import pe.com.iqc.flow.cartas.domain.TipoCarta;
import pe.com.iqc.flow.cartas.infrastructure.ConvertidorPdf;
import pe.com.iqc.flow.cartas.infrastructure.PropiedadesCartas;
import pe.com.iqc.flow.cartas.infrastructure.RellenadorDocx;
import pe.com.iqc.flow.cartas.infrastructure.RepositorioCartas;
import pe.com.iqc.flow.cartas.infrastructure.RepositorioCartas.FilaCarta;
import pe.com.iqc.flow.cartas.infrastructure.RepositorioCartas.FilaFirma;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaCatalogo;
import pe.com.iqc.flow.certificaciones.MarcasPais;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.infrastructure.Correlativos;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;
import tools.jackson.databind.json.JsonMapper;

/**
 *cartas a clientes desde las plantillas Word de plantillas/cartas: se arman con los datos del cliente, de sus
 *certificados, de la marca de su país y de quien firma; se revisan en una vista previa y al emitirlas reciben un número
 *CAR-AAAA-0001 y su PDF queda guardado. También guarda la firma de cada persona (la sube ella misma)
 */
@Service
public class ServicioCartas {
  private static final ZoneId LIMA = ZoneId.of("America/Lima");
  private static final DateTimeFormatter FECHA_TEXTO = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));
  private static final long FIRMA_MAXIMA = 2 * 1024 * 1024;

  /**pedido de vista previa o de emisión; los campos que no usa el tipo de carta se ignoran*/
  public record SolicitudCarta(@NotNull TipoCarta tipo, @NotNull UUID clienteId, @Size(max = 12) List<UUID> certificadoIds,
      LocalDate fecha, @Size(max = 80) String ciudad, UUID firmanteId, @Size(max = 40) String tipoServicio,
      @Size(max = 60) String etapa, @Size(max = 4000) String alcance, @Size(max = 300) String asunto,
      @Size(max = 20000) String cuerpo) {
  }

  public record TipoInfo(TipoCarta codigo, String nombre, String descripcion, boolean requiereCertificados) {
  }

  /**norma: «ISO 9001:2015»*/
  public record CertificadoCliente(UUID id, String codigo, String norma, String sistema, String alcance, String estado,
      LocalDate fechaExpiracion) {
  }

  public record Firmante(UUID usuarioId, String nombre, String cargo) {
  }

  public record MiFirma(boolean tieneFirma, String nombreUsuario, String nombreMostrado, String cargo, Instant actualizadoEn) {
  }

  public record Referencia(UUID id, String nombre) {
  }

  public record CartaResumen(UUID id, String numero, TipoCarta tipo, String tipoNombre, Referencia cliente, String asunto,
      LocalDate fecha, String firmante, String creadoPor, Instant creadoEn) {
  }

  public record Archivo(String nombre, String tipoContenido, byte[] contenido) {
  }

  /**lo que se guarda de cada carta para poder volver a generar su Word*/
  record DatosGuardados(Map<String, String> campos, List<Map<String, String>> normas, String cuerpo, UUID firmanteId) {
  }

  private record Armado(RellenadorDocx.Datos datos, String asunto, LocalDate fecha, UUID firmanteId) {
  }

  private final RepositorioCartas repositorio;
  private final RellenadorDocx rellenador;
  private final ConvertidorPdf convertidor;
  private final PropiedadesCartas propiedades;
  private final ConsultaClientes clientes;
  private final CertificadosPublicables certificados;
  private final ServicioCatalogos catalogos;
  private final MarcasPais marcas;
  private final ServicioDocumentos documentos;
  private final DirectorioUsuarios usuarios;
  private final Correlativos correlativos;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final JsonMapper json;
  private final Clock reloj;

  public ServicioCartas(RepositorioCartas repositorio, RellenadorDocx rellenador, ConvertidorPdf convertidor, PropiedadesCartas propiedades,
      ConsultaClientes clientes, CertificadosPublicables certificados, ServicioCatalogos catalogos, MarcasPais marcas,
      ServicioDocumentos documentos, DirectorioUsuarios usuarios, Correlativos correlativos, ActorActual actorActual,
      Bitacora bitacora, JsonMapper json, Clock reloj) {
    this.repositorio = repositorio;
    this.rellenador = rellenador;
    this.convertidor = convertidor;
    this.propiedades = propiedades;
    this.clientes = clientes;
    this.certificados = certificados;
    this.catalogos = catalogos;
    this.marcas = marcas;
    this.documentos = documentos;
    this.usuarios = usuarios;
    this.correlativos = correlativos;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.json = json;
    this.reloj = reloj;
  }

  @PreAuthorize("hasAuthority('cartas:leer')")
  public List<TipoInfo> tipos() {
    return java.util.Arrays.stream(TipoCarta.values()).map(t -> new TipoInfo(t, t.nombre(), t.descripcion(), t.requiereCertificados())).toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cartas:generar')")
  public List<CertificadoCliente> certificadosDelCliente(UUID clienteId) {
    UUID org = actorActual.requerido().organizacionId();
    cliente(org, clienteId);
    Map<String, NormaCatalogo> normas = normasPorCodigo();
    return certificados.porCliente(org, clienteId).stream()
        .sorted(java.util.Comparator.comparing(CertificadoPublicable::fechaEmision, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
        .map(c -> new CertificadoCliente(c.id(), c.codigo(), designacion(normas, c), sistema(normas, c), c.alcance(), c.estado(), c.fechaExpiracion()))
        .toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cartas:generar')")
  public List<Firmante> firmantes() {
    UUID org = actorActual.requerido().organizacionId();
    List<FilaFirma> firmas = repositorio.firmas(org);
    Map<UUID, UsuarioResumen> personas = usuarios.buscarPorIds(org, firmas.stream().map(FilaFirma::usuarioId).toList());
    return firmas.stream().filter(f -> personas.containsKey(f.usuarioId()) && personas.get(f.usuarioId()).activo())
        .map(f -> new Firmante(f.usuarioId(), nombreFirma(f, personas.get(f.usuarioId())), f.cargo()))
        .sorted(java.util.Comparator.comparing(Firmante::nombre)).toList();
  }

  // ---------------------------------------------------------------- firma de cada persona

  @Transactional(readOnly = true)
  public MiFirma miFirma() {
    UsuarioAutenticado actor = actorActual.requerido();
    String nombre = usuarios.buscarActivo(actor.organizacionId(), actor.id()).map(UsuarioResumen::nombre).orElse(null);
    return repositorio.firma(actor.organizacionId(), actor.id())
        .map(f -> new MiFirma(true, nombre, f.nombreMostrado(), f.cargo(), f.actualizadoEn()))
        .orElse(new MiFirma(false, nombre, null, null, null));
  }

  /**imagen: PNG o JPEG de la firma (fondo blanco o transparente); nula para cambiar solo el nombre o el cargo*/
  @Transactional
  public MiFirma guardarMiFirma(byte[] imagen, String nombreMostrado, String cargo) {
    UsuarioAutenticado actor = actorActual.requerido();
    if (cargo == null || cargo.isBlank()) throw new ReglaNegocioException("FIRMA_SIN_CARGO", "Escribe el cargo con el que firmas.");
    if (cargo.length() > 120 || (nombreMostrado != null && nombreMostrado.length() > 160)) {
      throw new ReglaNegocioException("FIRMA_TEXTO_LARGO", "El nombre o el cargo son demasiado largos.");
    }
    UUID documento;
    if (imagen != null && imagen.length > 0) {
      if (imagen.length > FIRMA_MAXIMA) throw new ReglaNegocioException("FIRMA_GRANDE", "La imagen de la firma no puede pasar de 2 MB.");
      documento = documentos.guardarGenerado(CategoriaDocumento.FIRMA_USUARIO, "firma.png", imagen).id();
    } else {
      documento = repositorio.firma(actor.organizacionId(), actor.id()).map(FilaFirma::documentoId)
          .orElseThrow(() -> new ReglaNegocioException("FIRMA_SIN_IMAGEN", "Sube la imagen de tu firma."));
    }
    repositorio.guardarFirma(actor.organizacionId(), new FilaFirma(actor.id(), documento,
        nombreMostrado == null || nombreMostrado.isBlank() ? null : nombreMostrado.trim(), cargo.trim(), Instant.now(reloj)));
    bitacora.registrar(EntradaBitacora.accion("FIRMA_ACTUALIZADA").entidad("USUARIO", actor.id()));
    return miFirma();
  }

  @Transactional
  public void borrarMiFirma() {
    UsuarioAutenticado actor = actorActual.requerido();
    repositorio.borrarFirma(actor.organizacionId(), actor.id());
    bitacora.registrar(EntradaBitacora.accion("FIRMA_ELIMINADA").entidad("USUARIO", actor.id()));
  }

  // ---------------------------------------------------------------- cartas

  /**PDF de la carta sin guardarla ni numerarla; acepta datos incompletos (lo que falta sale en blanco)*/
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cartas:generar')")
  public byte[] vistaPrevia(SolicitudCarta s) {
    Armado a = armar(actorActual.requerido().organizacionId(), s, false);
    return convertidor.aPdf(rellenador.llenar(plantilla(s.tipo()), a.datos()));
  }

  @Transactional
  @PreAuthorize("hasAuthority('cartas:generar')")
  public CartaResumen emitir(SolicitudCarta s) {
    UsuarioAutenticado actor = actorActual.requerido();
    UUID org = actor.organizacionId();
    Armado a = armar(org, s, true);
    byte[] pdf = convertidor.aPdf(rellenador.llenar(plantilla(s.tipo()), a.datos()));
    String numero = correlativos.siguienteCodigo(org, "CAR", a.fecha().getYear());
    UUID documento = documentos.guardarGenerado(CategoriaDocumento.CARTA_PDF, numero + ".pdf", pdf).id();
    String datos = json.writeValueAsString(new DatosGuardados(a.datos().campos(), a.datos().normas(), a.datos().cuerpo(), a.firmanteId()));
    FilaCarta fila = new FilaCarta(UUID.randomUUID(), numero, s.tipo(), s.clienteId(), recortar(a.asunto(), 400), a.fecha(),
        a.firmanteId(), datos, documento, actor.id(), Instant.now(reloj));
    repositorio.insertar(org, fila);
    bitacora.registrar(EntradaBitacora.accion("CARTA_EMITIDA").entidad("CARTA", fila.id())
        .estadoNuevo(Map.of("numero", numero, "tipo", s.tipo().name(), "clienteId", s.clienteId().toString())));
    return resumenes(org, List.of(fila)).get(0);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cartas:leer')")
  public RespuestaPaginada<CartaResumen> listar(UUID clienteId, String texto, int pagina, int tamano) {
    UUID org = actorActual.requerido().organizacionId();
    String t = texto == null || texto.isBlank() ? null : texto.trim();
    List<UUID> porTexto = t == null ? null : new ArrayList<>(clientes.idsPorTexto(org, t));
    List<FilaCarta> filas = repositorio.listar(org, clienteId, porTexto, t, pagina, tamano);
    long total = repositorio.contar(org, clienteId, porTexto, t);
    return new RespuestaPaginada<>(resumenes(org, filas), pagina, tamano, total, (int) Math.ceil((double) total / tamano));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cartas:leer')")
  public Archivo pdf(UUID id) {
    UUID org = actorActual.requerido().organizacionId();
    FilaCarta c = carta(org, id);
    return new Archivo(c.numero() + ".pdf", "application/pdf", documentos.leerContenido(org, c.documentoPdfId()));
  }

  /**Word de una carta emitida, para corregirla a mano: se vuelve a llenar la plantilla con los datos guardados*/
  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('cartas:generar')")
  public Archivo word(UUID id) {
    UUID org = actorActual.requerido().organizacionId();
    FilaCarta c = carta(org, id);
    DatosGuardados g = json.readValue(c.datos(), DatosGuardados.class);
    ClienteReferencia cliente = cliente(org, c.clienteId());
    RellenadorDocx.Datos datos = new RellenadorDocx.Datos(g.campos(), g.normas(), g.cuerpo(), imagenes(org, cliente, g.firmanteId()));
    return new Archivo(c.numero() + ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        rellenador.llenar(plantilla(c.tipo()), datos));
  }

  // ---------------------------------------------------------------- armado de los datos

  private Armado armar(UUID org, SolicitudCarta s, boolean estricto) {
    ClienteReferencia cliente = cliente(org, s.clienteId());
    TipoCarta tipo = s.tipo();
    LocalDate fecha = s.fecha() != null ? s.fecha() : LocalDate.now(reloj.withZone(LIMA));

    List<CertificadoPublicable> elegidos = new ArrayList<>();
    if (tipo.requiereCertificados() && s.certificadoIds() != null && !s.certificadoIds().isEmpty()) {
      Map<UUID, CertificadoPublicable> delCliente = certificados.porCliente(org, cliente.id()).stream()
          .collect(Collectors.toMap(CertificadoPublicable::id, Function.identity()));
      for (UUID id : new LinkedHashSet<>(s.certificadoIds())) {
        CertificadoPublicable c = delCliente.get(id);
        if (c == null) throw new ReglaNegocioException("CERTIFICADO_AJENO", "Uno de los certificados elegidos no es de este cliente.");
        elegidos.add(c);
      }
    }
    Map<String, NormaCatalogo> catalogo = normasPorCodigo();
    Map<String, Map<String, String>> normas = new LinkedHashMap<>();
    for (CertificadoPublicable c : elegidos) {
      String designacion = designacion(catalogo, c);
      NormaCatalogo n = catalogo.get(c.norma());
      normas.putIfAbsent(designacion, Map.of("codigo", n != null ? n.nombreCorto() : designacion.replaceAll(":.*", ""),
          "nombre", sistema(catalogo, c), "designacion", designacion));
    }
    List<String> designaciones = new ArrayList<>(normas.keySet());
    List<String> sinEdicion = normas.values().stream().map(m -> m.get("codigo")).distinct().toList();

    String alcance = texto(s.alcance());
    if (alcance == null) alcance = elegidos.stream().map(CertificadoPublicable::alcance).filter(Objects::nonNull).findFirst().orElse(null);
    String tipoServicio = Objects.requireNonNullElse(texto(s.tipoServicio()), "certificación").toLowerCase(Locale.ROOT);
    String etapa = texto(s.etapa());

    FilaFirma firma = s.firmanteId() == null ? null : repositorio.firma(org, s.firmanteId())
        .orElseThrow(() -> new ReglaNegocioException("FIRMANTE_SIN_FIRMA", "Esa persona aún no subió su firma en su perfil."));
    UsuarioResumen firmante = firma == null ? null : usuarios.buscarActivo(org, firma.usuarioId())
        .orElseThrow(() -> new ReglaNegocioException("FIRMANTE_INACTIVO", "Esa persona ya no está activa en IQC Flow."));

    if (estricto) {
      if (tipo.requiereCertificados() && elegidos.isEmpty()) throw new ReglaNegocioException("CARTA_SIN_CERTIFICADOS", "Elige al menos un certificado del cliente.");
      if (tipo == TipoCarta.CONTINUIDAD && etapa == null) throw new ReglaNegocioException("CARTA_SIN_ETAPA", "Indica la etapa, por ejemplo «2da Vigilancia».");
      if (tipo == TipoCarta.CERTIFICACION && alcance == null) throw new ReglaNegocioException("CARTA_SIN_ALCANCE", "Escribe el alcance de la certificación.");
      if (tipo == TipoCarta.LIBRE && (texto(s.asunto()) == null || texto(s.cuerpo()) == null)) {
        throw new ReglaNegocioException("CARTA_INCOMPLETA", "Escribe el asunto y el texto de la carta.");
      }
      if (firma == null) throw new ReglaNegocioException("CARTA_SIN_FIRMANTE", "Elige quién firma la carta.");
    }

    var marca = marcas.marca(org, cliente.codigoPais());
    Map<String, String> campos = new HashMap<>();
    campos.put("marcaNombre", marca.nombre());
    // si la marca aún no tiene su membrete cargado (Marcas y portal), el de respaldo de la configuración
    campos.put("marcaDireccion", Objects.requireNonNullElse(texto(marca.direccion()), propiedades.membrete().direccion()));
    campos.put("marcaTelefono", Objects.requireNonNullElse(texto(marca.telefono()), propiedades.membrete().telefono()));
    campos.put("marcaCorreo", Objects.requireNonNullElse(texto(marca.correo()), propiedades.membrete().correo()));
    campos.put("marcaSitioWeb", marca.sitioWeb());
    campos.put("marcaRazonSocial", propiedades.empresa().razonSocial());
    campos.put("marcaRuc", propiedades.empresa().ruc());
    campos.put("marcaDireccionFiscal", propiedades.empresa().direccionFiscal());
    campos.put("ciudad", Objects.requireNonNullElse(texto(s.ciudad()), propiedades.ciudad()));
    campos.put("fechaTexto", FECHA_TEXTO.format(fecha));
    campos.put("fecha", fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    campos.put("razonSocial", cliente.razonSocial());
    campos.put("numeroDocumento", cliente.numeroDocumento());
    campos.put("tipoServicio", tipoServicio);
    campos.put("normas", unir(designaciones));
    campos.put("normasSinEdicion", unir(sinEdicion));
    campos.put("sistema", sinEdicion.size() > 1 ? "Sistema Integrado de Gestión" : "Sistema de Gestión");
    if (etapa != null) campos.put("etapa", etapa);
    if (alcance != null) campos.put("alcance", alcance);
    if (texto(s.asunto()) != null) campos.put("asunto", texto(s.asunto()));
    if (firma != null) {
      campos.put("firmanteNombre", nombreFirma(firma, firmante));
      campos.put("firmanteCargo", firma.cargo());
    }
    campos.values().removeIf(Objects::isNull);

    String asunto = switch (tipo) {
      case CERTIFICACION -> "Carta de aprobación del Sistema de Gestión en las normas " + campos.get("normas");
      case CONTINUIDAD -> "Continuidad de las certificaciones " + campos.get("normas") + (etapa == null ? "" : " – " + etapa);
      case SUSPENSION_PAGO -> "Suspensión de las certificaciones " + campos.get("normasSinEdicion") + " por falta de pago";
      case LIBRE -> Objects.requireNonNullElse(texto(s.asunto()), "Carta");
    };
    List<Map<String, String>> filasNormas = normas.values().stream().map(m -> Map.of("codigo", m.get("codigo"), "nombre", m.get("nombre"))).toList();
    RellenadorDocx.Datos datos = new RellenadorDocx.Datos(Map.copyOf(campos), filasNormas, s.cuerpo(),
        imagenes(org, cliente, firma == null ? null : firma.usuarioId()));
    return new Armado(datos, asunto, fecha, firma == null ? null : firma.usuarioId());
  }

  /**logo de la marca del país del cliente (si se subió uno) y firma de quien firma; sin dato queda la imagen de la plantilla*/
  private Map<String, byte[]> imagenes(UUID org, ClienteReferencia cliente, UUID firmanteId) {
    Map<String, byte[]> imagenes = new HashMap<>();
    var marca = marcas.marca(org, cliente.codigoPais());
    if (marca.logoDocumentoId() != null) imagenes.put("logo", documentos.leerContenido(org, marca.logoDocumentoId()));
    if (firmanteId != null) repositorio.firma(org, firmanteId).ifPresent(f -> imagenes.put("firma", documentos.leerContenido(org, f.documentoId())));
    return imagenes;
  }

  private InputStream plantilla(TipoCarta tipo) {
    try {
      return new ClassPathResource("plantillas/cartas/" + tipo.plantilla()).getInputStream();
    } catch (IOException e) {
      throw new UncheckedIOException("Falta la plantilla " + tipo.plantilla(), e);
    }
  }

  private List<CartaResumen> resumenes(UUID org, List<FilaCarta> filas) {
    Set<UUID> idsClientes = filas.stream().map(FilaCarta::clienteId).collect(Collectors.toSet());
    Map<UUID, ClienteReferencia> cs = clientes.buscarPorIds(org, idsClientes);
    Set<UUID> idsPersonas = new java.util.HashSet<>();
    filas.forEach(f -> { idsPersonas.add(f.creadoPor()); if (f.firmanteId() != null) idsPersonas.add(f.firmanteId()); });
    Map<UUID, UsuarioResumen> personas = usuarios.buscarPorIds(org, idsPersonas);
    Map<UUID, FilaFirma> firmas = repositorio.firmas(org).stream().collect(Collectors.toMap(FilaFirma::usuarioId, Function.identity()));
    return filas.stream().map(f -> {
      ClienteReferencia c = cs.get(f.clienteId());
      UsuarioResumen firmante = f.firmanteId() == null ? null : personas.get(f.firmanteId());
      String nombreFirmante = firmante == null ? null : firmas.containsKey(f.firmanteId()) ? nombreFirma(firmas.get(f.firmanteId()), firmante) : firmante.nombre();
      UsuarioResumen autor = personas.get(f.creadoPor());
      return new CartaResumen(f.id(), f.numero(), f.tipo(), f.tipo().nombre(), new Referencia(f.clienteId(), c == null ? null : c.razonSocial()),
          f.asunto(), f.fecha(), nombreFirmante, autor == null ? null : autor.nombre(), f.creadoEn());
    }).toList();
  }

  private FilaCarta carta(UUID org, UUID id) {
    return repositorio.buscar(org, id).orElseThrow(() -> new RecursoNoEncontradoException("Carta", id));
  }

  private ClienteReferencia cliente(UUID org, UUID id) {
    return clientes.buscar(org, id).orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
  }

  private Map<String, NormaCatalogo> normasPorCodigo() {
    return catalogos.normas().stream().collect(Collectors.toMap(NormaCatalogo::codigo, Function.identity(), (a, b) -> a));
  }

  /**«ISO 9001:2015» con la designación del catálogo; si no está, nombre corto y edición*/
  private static String designacion(Map<String, NormaCatalogo> catalogo, CertificadoPublicable c) {
    NormaCatalogo n = catalogo.get(c.norma());
    if (n == null) return c.norma() + (c.edicion() == null ? "" : ":" + c.edicion());
    return n.ediciones().stream().filter(e -> e.edicion().equals(c.edicion())).map(ServicioCatalogos.EdicionCatalogo::designacion)
        .filter(Objects::nonNull).findFirst().orElse(n.nombreCorto() + (c.edicion() == null ? "" : ":" + c.edicion()));
  }

  /**«Sistema de Gestión de la Calidad» (el catálogo lo tiene en plural)*/
  private static String sistema(Map<String, NormaCatalogo> catalogo, CertificadoPublicable c) {
    NormaCatalogo n = catalogo.get(c.norma());
    if (n == null || n.titulo() == null) return "";
    return n.titulo().replaceFirst("^Sistemas de", "Sistema de");
  }

  private static String nombreFirma(FilaFirma f, UsuarioResumen u) {
    return f.nombreMostrado() != null ? f.nombreMostrado() : u == null ? "" : u.nombre();
  }

  /**«A», «A e B», «A, B e C»: «e» antes de palabra que empieza con i (ISO), «y» en los demás casos*/
  static String unir(List<String> partes) {
    if (partes.isEmpty()) return "";
    if (partes.size() == 1) return partes.get(0);
    String ultimo = partes.get(partes.size() - 1);
    String conjuncion = ultimo.toLowerCase(Locale.ROOT).matches("^h?i[^aeou].*|^h?i$") ? " e " : " y ";
    return String.join(", ", partes.subList(0, partes.size() - 1)) + conjuncion + ultimo;
  }

  private static String texto(String s) { return s == null || s.isBlank() ? null : s.trim(); }

  private static String recortar(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
}
