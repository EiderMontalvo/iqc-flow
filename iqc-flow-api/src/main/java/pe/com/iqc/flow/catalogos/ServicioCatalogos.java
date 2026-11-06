package pe.com.iqc.flow.catalogos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.catalogos.domain.EdicionNorma;
import pe.com.iqc.flow.catalogos.domain.EsquemaAcreditacion;
import pe.com.iqc.flow.catalogos.domain.Norma;
import pe.com.iqc.flow.catalogos.domain.Pais;
import pe.com.iqc.flow.catalogos.infrastructure.EsquemaAcreditacionRepository;
import pe.com.iqc.flow.catalogos.infrastructure.NormaRepository;
import pe.com.iqc.flow.catalogos.infrastructure.PaisRepository;

/**API pública de catálogos de referencia: países, esquemas de acreditación y normas con sus ediciones*/
@Service
public class ServicioCatalogos {
  private final PaisRepository paises;
  private final EsquemaAcreditacionRepository esquemas;
  private final NormaRepository normas;

  public ServicioCatalogos(PaisRepository paises, EsquemaAcreditacionRepository esquemas, NormaRepository normas) {
    this.paises = paises;
    this.esquemas = esquemas;
    this.normas = normas;
  }

  @Transactional(readOnly = true)
  public List<PaisCatalogo> paises() {
    return paises.findByActivoTrueOrderByNombreAsc().stream().map(ServicioCatalogos::pais).toList();
  }

  @Transactional(readOnly = true)
  public Optional<PaisCatalogo> pais(String codigo) {
    return paises.findById(codigo).map(ServicioCatalogos::pais);
  }

  @Transactional(readOnly = true)
  public List<EsquemaCatalogo> esquemas() {
    return esquemas.findByActivoTrueOrderByNombreAsc().stream().map(ServicioCatalogos::esquema).toList();
  }

  @Transactional(readOnly = true)
  public Optional<EsquemaCatalogo> esquema(String codigo) {
    return esquemas.findById(codigo).map(ServicioCatalogos::esquema);
  }

  @Transactional(readOnly = true)
  public List<NormaCatalogo> normas() {
    return normas.findByActivoTrueOrderByOrdenAsc().stream().map(ServicioCatalogos::norma).toList();
  }

  /**traduce la abreviatura del cuadro de control de operaciones ("9", "14", "45"…) a la norma*/
  @Transactional(readOnly = true)
  public Optional<NormaCatalogo> normaPorCodigoOperaciones(String abreviatura) {
    return normas.findByCodigoOperaciones(abreviatura == null ? "" : abreviatura.trim()).map(ServicioCatalogos::norma);
  }

  /**edición concreta de una norma, con los datos de la norma; vacío si no existe*/
  @Transactional(readOnly = true)
  public Optional<NormaEdicion> buscarEdicion(String normaCodigo, String edicion) {
    return normas.findById(normaCodigo == null ? "" : normaCodigo)
        .flatMap(n -> n.getEdiciones().stream().filter(e -> e.getEdicion().equals(edicion)).findFirst()
            .map(e -> new NormaEdicion(n.getCodigo(), n.getNombreCorto(), n.getCodigoSistema(), e.getEdicion(),
                n.getNombreCorto() + ":" + e.getEdicion(), e.isVigente())));
  }

  private static PaisCatalogo pais(Pais p) {
    return new PaisCatalogo(p.getCodigo(), p.getNombre(), p.getDocumentoFiscal(), p.getMoneda(),
        p.getNombreImpuesto(), p.getTasaImpuesto(),
        new EtiquetasDireccion(p.getEtiquetaNivel1(), p.getEtiquetaNivel2(), p.getEtiquetaNivel3(),
            p.getEtiquetaCodigoUbicacion()),
        p.getZonaHoraria());
  }

  private static EsquemaCatalogo esquema(EsquemaAcreditacion e) {
    return new EsquemaCatalogo(e.getCodigo(), e.getNombre(), e.getOrganismoAcreditador(), e.getPaisAcreditador(),
        e.getPrefijoCertificado(), e.getAniosCiclo(), e.getVigilanciasPorCiclo(), e.getDecideIqcPeru(), e.getNotas());
  }

  private static NormaCatalogo norma(Norma n) {
    return new NormaCatalogo(n.getCodigo(), n.getNombreCorto(), n.getTitulo(), n.getCodigoSistema(),
        n.getCodigoOperaciones(), n.getEdiciones().stream().map(e -> edicion(e, n.getNombreCorto())).toList());
  }

  private static EdicionCatalogo edicion(EdicionNorma e, String nombreNorma) {
    return new EdicionCatalogo(e.getEdicion(), nombreNorma + ":" + e.getEdicion(), e.isVigente(), e.getNotas());
  }

  /**en direcciones: nivel1 = departamento (PE) o provincia (EC); nivel2 = provincia o cantón; nivel3 = distrito o parroquia*/
  public record EtiquetasDireccion(String nivel1, String nivel2, String nivel3, String codigoUbicacion) {
  }

  public record PaisCatalogo(String codigo, String nombre, String documentoFiscal, String moneda,
      String nombreImpuesto, BigDecimal tasaImpuesto, EtiquetasDireccion etiquetasDireccion, String zonaHoraria) {
  }

  /**decideIqcPeru nulo: por confirmar quién decide la certificación en ese esquema*/
  public record EsquemaCatalogo(String codigo, String nombre, String organismoAcreditador, String paisAcreditador,
      String prefijoCertificado, int aniosCiclo, int vigilanciasPorCiclo, Boolean decideIqcPeru, String notas) {
  }

  public record NormaCatalogo(String codigo, String nombreCorto, String titulo, String codigoSistema,
      String codigoOperaciones, List<EdicionCatalogo> ediciones) {
  }

  /**designacion: "ISO 9001:2015"*/
  public record EdicionCatalogo(String edicion, String designacion, boolean vigente, String notas) {
  }

  public record NormaEdicion(String normaCodigo, String nombreCorto, String codigoSistema, String edicion,
      String designacion, boolean vigente) {
  }
}
