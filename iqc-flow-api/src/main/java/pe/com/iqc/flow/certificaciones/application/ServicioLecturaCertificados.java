package pe.com.iqc.flow.certificaciones.application;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaCatalogo;
import pe.com.iqc.flow.certificaciones.application.LectorIaCertificados.Tipo;
import pe.com.iqc.flow.certificaciones.domain.AnalizadorTextoCertificado;
import pe.com.iqc.flow.certificaciones.domain.AnalizadorTextoCertificado.Formato;
import pe.com.iqc.flow.certificaciones.domain.AnalizadorTextoCertificado.Resultado;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;

/**
 *modo de registro automatizado: lee el PDF subido y propone los campos del formulario. Primero lectura local (texto del
 *PDF y reglas por formato); si no reconoce el formato y la IA está encendida y tiene proveedor, se usa la IA
 */
@Service
public class ServicioLecturaCertificados {
  /**los certificados son de una o dos páginas; no se lee más para acotar el costo con PDF anómalos*/
  private static final int PAGINAS_MAXIMAS = 3;

  private final ServicioDocumentos documentos;
  private final ParametrosOrganizacion parametros;
  private final ServicioCatalogos catalogos;
  private final ActorActual actorActual;
  private final ObjectProvider<LectorIaCertificados> ia;

  public ServicioLecturaCertificados(ServicioDocumentos documentos, ParametrosOrganizacion parametros,
      ServicioCatalogos catalogos, ActorActual actorActual, ObjectProvider<LectorIaCertificados> ia) {
    this.documentos = documentos;
    this.parametros = parametros;
    this.catalogos = catalogos;
    this.actorActual = actorActual;
    this.ia = ia;
  }

  public enum Modo { MANUAL, AUTOMATIZADO, PLANTILLA }

  public record SolicitudLectura(@NotNull Tipo tipo, @NotNull UUID documentoId) {
  }

  /**normaCodigo y edicionValida: la norma leída traducida al catálogo (nulos si no está)*/
  public record LecturaRespuesta(Formato formato, String origen, AnalizadorTextoCertificado.Empresarial empresarial,
      String normaCodigo, boolean edicionValida, AnalizadorTextoCertificado.Personal personal, List<String> advertencias) {
  }

  /**qué puede hacer el usuario al registrar certificados, según la configuración de MASTER y sus permisos*/
  public record Herramientas(List<Modo> modosEmpresarial, List<Modo> modosPersonal, boolean autocompletado,
      boolean generacionPdf, boolean ia, boolean automatizaciones) {
  }

  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public Herramientas herramientas() {
    UUID org = actorActual.requerido().organizacionId(), yo = actorActual.requerido().id();
    boolean autocompletar = permiso("certificados:autocompletar");
    boolean generar = permiso("certificados:generar-pdf");
    return new Herramientas(
        modos(org, yo, ParametroOrganizacion.CERT_EMPRESARIAL_MODO_MANUAL, ParametroOrganizacion.CERT_EMPRESARIAL_MODO_AUTOMATIZADO,
            ParametroOrganizacion.CERT_EMPRESARIAL_MODO_PLANTILLA, autocompletar, generar),
        modos(org, yo, ParametroOrganizacion.CERT_PERSONAL_MODO_MANUAL, ParametroOrganizacion.CERT_PERSONAL_MODO_AUTOMATIZADO,
            ParametroOrganizacion.CERT_PERSONAL_MODO_PLANTILLA, autocompletar, generar),
        autocompletar, generar, parametros.activoPara(org, yo, ParametroOrganizacion.IA_HABILITADA) && ia.getIfAvailable() != null,
        parametros.activoPara(org, yo, ParametroOrganizacion.AUTOMATIZACIONES_HABILITADAS));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('certificados:autocompletar')")
  public LecturaRespuesta leer(SolicitudLectura s) {
    UUID org = actorActual.requerido().organizacionId(), yo = actorActual.requerido().id();
    ParametroOrganizacion modo = s.tipo() == Tipo.EMPRESARIAL ? ParametroOrganizacion.CERT_EMPRESARIAL_MODO_AUTOMATIZADO
        : ParametroOrganizacion.CERT_PERSONAL_MODO_AUTOMATIZADO;
    if (!parametros.activoPara(org, yo, modo)) {
      throw new ReglaNegocioException("MODO_DESHABILITADO", "El registro automatizado está desactivado por la configuración.");
    }
    documentos.exigir(org, s.documentoId(), CategoriaDocumento.CERTIFICADO_PDF);
    byte[] pdf = documentos.leerContenido(org, s.documentoId());
    List<String> avisos = new ArrayList<>();
    String texto = texto(pdf);
    Resultado r = AnalizadorTextoCertificado.analizar(texto);
    String origen = "LOCAL";
    boolean reconocido = r.formato() != Formato.DESCONOCIDO;
    if (!reconocido) {
      if (texto.isBlank()) avisos.add("El PDF no tiene texto (parece escaneado).");
      Optional<Resultado> conIa = intentarIa(org, yo, pdf, s.tipo(), avisos);
      if (conIa.isPresent()) {
        r = conIa.get();
        origen = "IA";
      }
    }
    avisos.addAll(r.advertencias());
    if (r.formato() == Formato.PERSONAL && s.tipo() == Tipo.EMPRESARIAL) {
      avisos.add("El PDF parece un certificado personal, no empresarial.");
    } else if (r.empresarial() != null && s.tipo() == Tipo.PERSONAL) {
      avisos.add("El PDF parece un certificado empresarial, no personal.");
    }
    String normaCodigo = null;
    boolean edicionValida = false;
    AnalizadorTextoCertificado.Empresarial leida = r.empresarial();
    if (leida != null && leida.normaNombre() != null) {
      String nombre = leida.normaNombre().replace(" ", "");
      Optional<NormaCatalogo> norma = catalogos.normas().stream()
          .filter(n -> n.nombreCorto().replace(" ", "").equalsIgnoreCase(nombre)).findFirst();
      if (norma.isPresent()) {
        normaCodigo = norma.get().codigo();
        edicionValida = norma.get().ediciones().stream().anyMatch(e -> e.edicion().equals(leida.edicion()));
        if (!edicionValida) avisos.add("La edición " + leida.edicion() + " de " + norma.get().nombreCorto() + " no está en el catálogo.");
      } else {
        avisos.add("La norma " + leida.normaTexto() + " no está en el catálogo de normas.");
      }
    }
    return new LecturaRespuesta(r.formato(), origen, r.empresarial(), normaCodigo, edicionValida, r.personal(), avisos);
  }

  private Optional<Resultado> intentarIa(UUID org, UUID yo, byte[] pdf, Tipo tipo, List<String> avisos) {
    if (!parametros.activoPara(org, yo, ParametroOrganizacion.IA_HABILITADA)) {
      avisos.add("La IA está apagada: completa los datos a mano.");
      return Optional.empty();
    }
    LectorIaCertificados lector = ia.getIfAvailable();
    if (lector == null) {
      avisos.add("La IA está encendida, pero aún no hay un proveedor de IA configurado.");
      return Optional.empty();
    }
    return lector.leer(pdf, tipo);
  }

  static String texto(byte[] pdf) {
    try (PDDocument documento = Loader.loadPDF(pdf)) {
      PDFTextStripper lector = new PDFTextStripper();
      lector.setSortByPosition(true);
      lector.setEndPage(Math.min(documento.getNumberOfPages(), PAGINAS_MAXIMAS));
      return lector.getText(documento);
    } catch (IOException e) {
      throw new ReglaNegocioException("PDF_ILEGIBLE", "No se pudo leer el PDF: puede estar dañado o protegido.");
    }
  }

  private List<Modo> modos(UUID org, UUID yo, ParametroOrganizacion manual, ParametroOrganizacion automatizado,
      ParametroOrganizacion plantilla, boolean autocompletar, boolean generar) {
    List<Modo> modos = new ArrayList<>();
    if (parametros.activoPara(org, yo, manual)) modos.add(Modo.MANUAL);
    if (autocompletar && parametros.activoPara(org, yo, automatizado)) modos.add(Modo.AUTOMATIZADO);
    if (generar && parametros.activoPara(org, yo, plantilla)) modos.add(Modo.PLANTILLA);
    return modos;
  }

  private static boolean permiso(String codigo) {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a != null && a.getAuthorities().stream().anyMatch(x -> codigo.equals(x.getAuthority()));
  }

}
