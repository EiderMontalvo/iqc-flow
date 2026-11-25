package pe.com.iqc.flow.auditorias.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.CertificadoPanel;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.FiltroCertificados;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.RegistroDirectoRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.SolicitudMarca;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.SolicitudPdf;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.SolicitudRegistroDirecto;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.Vigencia;
import pe.com.iqc.flow.auditorias.application.DtosPanelCertificados.VersionPdf;
import pe.com.iqc.flow.auditorias.application.ServicioPanelCertificados;
import pe.com.iqc.flow.auditorias.domain.Certificado;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;

@RestController
@RequestMapping("/api/v1/certificados")
@Tag(name = "Panel de certificados")
public class PanelCertificadosController {
  private static final MediaType EXCEL =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ServicioPanelCertificados panel;

  public PanelCertificadosController(ServicioPanelCertificados panel) { this.panel = panel; }

  @GetMapping
  @Operation(summary = "Busca certificados de todas las marcas: texto (código, razón social o documento), esquema, norma, "
      + "país de la marca, estado y vigencia (VIGENTE, POR_VENCER, VENCIDO, SIN_FECHA)")
  public RespuestaPaginada<CertificadoPanel> buscar(@RequestParam(required = false) String texto,
      @RequestParam(required = false) String esquema, @RequestParam(required = false) String norma,
      @RequestParam(required = false) String pais, @RequestParam(required = false) Certificado.Estado estado,
      @RequestParam(required = false) Vigencia vigencia, @RequestParam(required = false) Integer diasPorVencer,
      @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "25") int tamano) {
    return panel.buscar(new FiltroCertificados(texto, esquema, norma, pais, estado, vigencia, diasPorVencer), pagina, tamano);
  }

  @GetMapping("/exportacion")
  @Operation(summary = "Exporta a Excel el resultado completo de la búsqueda (mismos filtros)")
  public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String texto,
      @RequestParam(required = false) String esquema, @RequestParam(required = false) String norma,
      @RequestParam(required = false) String pais, @RequestParam(required = false) Certificado.Estado estado,
      @RequestParam(required = false) Vigencia vigencia, @RequestParam(required = false) Integer diasPorVencer) {
    byte[] excel = panel.exportar(new FiltroCertificados(texto, esquema, norma, pais, estado, vigencia, diasPorVencer));
    return ResponseEntity.ok().contentType(EXCEL)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename("certificados-" + LocalDate.now() + ".xlsx").build().toString())
        .body(excel);
  }

  @GetMapping("/resumen")
  @Operation(summary = "Certificados empresariales por marca (país): vigentes, por vencer, vencidos, suspendidos y retirados")
  public List<DtosPanelCertificados.ResumenMarca> resumen() { return panel.resumenPorMarca(); }

  @GetMapping("/{id}")
  public CertificadoPanel obtener(@PathVariable UUID id) { return panel.obtener(id); }

  @PostMapping("/registro-directo")
  @Operation(summary = "Registra certificados ya emitidos sin el flujo del expediente (certificados:gestionar); queda en la bitácora con su motivo")
  public ResponseEntity<RegistroDirectoRespuesta> registrarDirecto(@Valid @RequestBody SolicitudRegistroDirecto solicitud) {
    RegistroDirectoRespuesta r = panel.registrarDirecto(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/expedientes/" + r.expedienteId())).body(r);
  }

  @PutMapping("/{id}/pdf")
  @Operation(summary = "Sube una nueva versión del PDF (documento de categoría CERTIFICADO_PDF); la anterior queda en el historial")
  public CertificadoPanel adjuntarPdf(@PathVariable UUID id, @Valid @RequestBody SolicitudPdf solicitud) {
    return panel.adjuntarPdf(id, solicitud);
  }

  @GetMapping("/{id}/pdf/versiones")
  public List<VersionPdf> versionesPdf(@PathVariable UUID id) { return panel.historialPdf(id); }

  @PutMapping("/{id}/marca")
  @Operation(summary = "Elige la marca (país) con que se muestra el certificado; pais nulo vuelve a la del país del cliente")
  public CertificadoPanel cambiarMarca(@PathVariable UUID id, @Valid @RequestBody SolicitudMarca solicitud) {
    return panel.cambiarMarca(id, solicitud);
  }
}
