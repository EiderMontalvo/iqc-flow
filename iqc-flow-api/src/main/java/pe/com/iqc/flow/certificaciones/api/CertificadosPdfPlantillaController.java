package pe.com.iqc.flow.certificaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.CertificadoPersonalRespuesta;
import pe.com.iqc.flow.certificaciones.application.ServicioPdfCertificados;
import pe.com.iqc.flow.certificaciones.application.ServicioPdfCertificados.BorradorEmpresarial;
import pe.com.iqc.flow.certificaciones.application.ServicioPdfCertificados.BorradorPersonal;
import pe.com.iqc.flow.certificaciones.application.ServicioPdfCertificados.PdfGenerado;
import pe.com.iqc.flow.certificaciones.application.ServicioPdfCertificados.VistaPrevia;

/**modo «Desde plantilla»: el PDF del certificado se dibuja con la plantilla de IQC en lugar de subirse*/
@RestController
@Tag(name = "PDF desde plantilla")
public class CertificadosPdfPlantillaController {
  private final ServicioPdfCertificados servicio;

  public CertificadosPdfPlantillaController(ServicioPdfCertificados servicio) { this.servicio = servicio; }

  @GetMapping("/api/v1/certificados/{id}/vista-previa-plantilla")
  @Operation(summary = "PDF del certificado empresarial emitido (SAE o INACAL) con la plantilla, sin guardarlo (certificados:generar-pdf)")
  public ResponseEntity<byte[]> vistaPreviaEmpresarial(@PathVariable UUID id) { return pdf(servicio.vistaPreviaEmpresarial(id)); }

  @PostMapping("/api/v1/certificados/{id}/pdf-plantilla")
  @Operation(summary = "Genera el PDF con la plantilla y lo deja como versión vigente; el anterior queda en el historial (certificados:generar-pdf)")
  public PdfGenerado generarEmpresarial(@PathVariable UUID id) { return servicio.generarEmpresarial(id); }

  @GetMapping("/api/v1/certificados-personales/{id}/vista-previa-plantilla")
  @Operation(summary = "PDF del certificado personal con la plantilla, sin guardarlo (certificados:generar-pdf)")
  public ResponseEntity<byte[]> vistaPreviaPersonal(@PathVariable UUID id) { return pdf(servicio.vistaPreviaPersonal(id)); }

  @PostMapping("/api/v1/certificados-personales/{id}/pdf-plantilla")
  @Operation(summary = "Genera el PDF con la plantilla y lo adjunta al certificado personal (certificados:generar-pdf y certificados:gestionar)")
  public CertificadoPersonalRespuesta generarPersonal(@PathVariable UUID id) { return servicio.generarPersonal(id); }

  @PostMapping("/api/v1/certificados/vista-previa-borrador")
  @Operation(summary = "PDF con la plantilla de lo que hay en el formulario empresarial, sin registrar ni guardar nada (certificados:generar-pdf)")
  public ResponseEntity<byte[]> vistaPreviaBorradorEmpresarial(@Valid @RequestBody BorradorEmpresarial borrador) {
    return pdf(servicio.vistaPreviaBorradorEmpresarial(borrador));
  }

  @PostMapping("/api/v1/certificados-personales/vista-previa-borrador")
  @Operation(summary = "PDF con la plantilla de lo que hay en el formulario personal, sin registrar ni guardar nada (certificados:generar-pdf)")
  public ResponseEntity<byte[]> vistaPreviaBorradorPersonal(@Valid @RequestBody BorradorPersonal borrador) {
    return pdf(servicio.vistaPreviaBorradorPersonal(borrador));
  }

  private static ResponseEntity<byte[]> pdf(VistaPrevia vista) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(vista.nombreArchivo()).build().toString())
        .body(vista.contenido());
  }
}
