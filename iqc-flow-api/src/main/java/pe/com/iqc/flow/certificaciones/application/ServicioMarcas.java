package pe.com.iqc.flow.certificaciones.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.PaisCatalogo;
import pe.com.iqc.flow.certificaciones.MarcasPais;
import pe.com.iqc.flow.certificaciones.domain.MarcaPais;
import pe.com.iqc.flow.certificaciones.domain.MarcasPredeterminadas;
import pe.com.iqc.flow.certificaciones.infrastructure.MarcaPaisRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.organizaciones.ServicioOrganizaciones;

/**marcas por país: lectura para todos los módulos y cambio solo con organizacion:configurar (MASTER)*/
@Service
public class ServicioMarcas implements MarcasPais {
  private static final Pattern URL_CONSULTA = Pattern.compile("https://[a-z0-9.-]+\\.[a-z]{2,}(/[A-Za-z0-9._~-]+)*/");
  private static final Set<String> IMAGENES = Set.of("image/png", "image/jpeg");

  private final MarcaPaisRepository marcas;
  private final ServicioCatalogos catalogos;
  private final ServicioDocumentos documentos;
  private final ServicioOrganizaciones organizaciones;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public ServicioMarcas(MarcaPaisRepository marcas, ServicioCatalogos catalogos, ServicioDocumentos documentos,
      ServicioOrganizaciones organizaciones, ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.marcas = marcas;
    this.catalogos = catalogos;
    this.documentos = documentos;
    this.organizaciones = organizaciones;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  public record MarcaRespuesta(String codigoPais, String nombrePais, String nombre, String urlConsulta, String sitioWeb,
      String correo, String telefono, String direccion, UUID logoDocumentoId, boolean predeterminada,
      UUID actualizadoPor, Instant actualizadoEn, Long version) {
  }

  /**version: la de la marca configurada (nula si aún es la predeterminada)*/
  public record SolicitudMarcaPais(@NotBlank @Size(max = 80) String nombre, @NotBlank @Size(max = 255) String urlConsulta,
      @Size(max = 255) String sitioWeb, @Email @Size(max = 255) String correo, @Size(max = 60) String telefono,
      @Size(max = 500) String direccion, UUID logoDocumentoId, Long version) {
  }

  @Override
  @Transactional(readOnly = true)
  public MarcaVigente marca(UUID organizacionId, String codigoPais) {
    String pais = codigoPais == null ? null : codigoPais.toUpperCase(Locale.ROOT);
    if (pais != null) {
      var configurada = marcas.findById(new MarcaPais.Clave(organizacionId, pais));
      if (configurada.isPresent()) return configurada.get().vigente();
      var predeterminada = MarcasPredeterminadas.de(pais);
      if (predeterminada.isPresent()) return predeterminada.get();
    }
    // País sin marca propia (p. ej. un cliente de otro país): se muestra con la marca del país de la organización
    String propio = organizaciones.obtener(organizacionId).codigoPais();
    if (propio.equals(pais)) return MarcasPredeterminadas.de("PE").orElseThrow();
    return marca(organizacionId, propio);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public List<MarcaRespuesta> listar() {
    UUID org = actorActual.requerido().organizacionId();
    Map<String, MarcaRespuesta> porPais = new LinkedHashMap<>();
    MarcasPredeterminadas.todas().values().forEach(m -> porPais.put(m.codigoPais(), respuesta(m, null)));
    marcas.deOrganizacion(org).forEach(m -> porPais.put(m.getCodigoPais(), respuesta(m.vigente(), m)));
    return porPais.values().stream()
        .sorted(Comparator.comparing((MarcaRespuesta m) -> !"PE".equals(m.codigoPais())).thenComparing(MarcaRespuesta::nombre))
        .toList();
  }

  @Transactional
  @PreAuthorize("hasAuthority('organizacion:configurar')")
  public MarcaRespuesta definir(String codigoPais, SolicitudMarcaPais s) {
    UsuarioAutenticado actor = actorActual.requerido();
    String pais = codigoPais.trim().toUpperCase(Locale.ROOT);
    if (catalogos.pais(pais).isEmpty()) {
      throw new ReglaNegocioException("PAIS_INVALIDO", "El país " + codigoPais + " no está en el catálogo.");
    }
    String url = s.urlConsulta().trim();
    if (!url.endsWith("/")) url = url + "/";
    if (!URL_CONSULTA.matcher(url).matches()) {
      throw new ReglaNegocioException("URL_CONSULTA_INVALIDA",
          "La dirección de consulta debe ser https y sin parámetros, por ejemplo https://consultas.iqc-latam.com/");
    }
    if (s.logoDocumentoId() != null) {
      var logo = documentos.exigir(actor.organizacionId(), s.logoDocumentoId(), CategoriaDocumento.LOGO_MARCA);
      if (!IMAGENES.contains(logo.tipoContenido())) {
        throw new ReglaNegocioException("FORMATO_NO_ADMITIDO", "El logo debe ser PNG o JPEG.");
      }
    }
    MarcaVigente antes = marca(actor.organizacionId(), pais);
    MarcaPais.Clave clave = new MarcaPais.Clave(actor.organizacionId(), pais);
    MarcaPais marca = marcas.findById(clave).orElse(null);
    if (marca == null) {
      if (s.version() != null) throw conflicto();
      marca = new MarcaPais(actor.organizacionId(), pais);
    } else if (s.version() == null || s.version() != marca.getVersion()) {
      throw conflicto();
    }
    marca.definir(s.nombre().trim(), url, limpio(s.sitioWeb()), limpio(s.correo()), limpio(s.telefono()),
        limpio(s.direccion()), s.logoDocumentoId(), actor.id(), Instant.now(reloj));
    marca = marcas.saveAndFlush(marca);
    Map<String, Object> anterior = new LinkedHashMap<>();
    anterior.put("nombre", antes.nombre());
    anterior.put("urlConsulta", antes.urlConsulta());
    bitacora.registrar(EntradaBitacora.accion("MARCA_PAIS_DEFINIDA").entidad("MARCA_PAIS", pais)
        .estadoAnterior(anterior).estadoNuevo(Map.of("nombre", marca.vigente().nombre(), "urlConsulta", url)));
    return respuesta(marca.vigente(), marca);
  }

  private MarcaRespuesta respuesta(MarcaVigente m, MarcaPais fila) {
    String nombrePais = catalogos.pais(m.codigoPais()).map(PaisCatalogo::nombre).orElse(m.codigoPais());
    return new MarcaRespuesta(m.codigoPais(), nombrePais, m.nombre(), m.urlConsulta(), m.sitioWeb(), m.correo(),
        m.telefono(), m.direccion(), m.logoDocumentoId(), m.predeterminada(),
        fila == null ? null : fila.getActualizadoPor(), fila == null ? null : fila.getActualizadoEn(),
        fila == null ? null : fila.getVersion());
  }

  private static ConflictoException conflicto() {
    return new ConflictoException("CONFLICTO_VERSION", "La marca fue modificada por otra persona. Recarga los datos y vuelve a intentarlo.");
  }

  private static String limpio(String texto) { return texto == null || texto.isBlank() ? null : texto.trim(); }
}
