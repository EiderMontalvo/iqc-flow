package pe.com.iqc.flow.certificaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.CertificadoPersonalRespuesta;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.PersonaRespuesta;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.ResumenPais;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.SolicitudAnulacion;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.SolicitudCorreccion;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.SolicitudPdfPersonal;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.SolicitudRegistro;
import pe.com.iqc.flow.certificaciones.application.ServicioCertificadosPersonales.TipoDocumentoRespuesta;
import pe.com.iqc.flow.certificaciones.domain.CertificadoPersonal;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;

@RestController
@RequestMapping("/api/v1/certificados-personales")
@Tag(name = "Certificados personales")
public class CertificadosPersonalesController {
  private final ServicioCertificadosPersonales servicio;

  public CertificadosPersonalesController(ServicioCertificadosPersonales servicio) { this.servicio = servicio; }

  @GetMapping
  @Operation(summary = "Busca por código, curso, nombre o documento de la persona; filtra por país y estado")
  public RespuestaPaginada<CertificadoPersonalRespuesta> buscar(@RequestParam(required = false) String texto,
      @RequestParam(required = false) String pais, @RequestParam(required = false) CertificadoPersonal.Estado estado,
      @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "25") int tamano) {
    return servicio.buscar(texto, pais, estado, pagina, tamano);
  }

  @GetMapping("/resumen")
  @Operation(summary = "Emitidos y anulados por país de la persona")
  public List<ResumenPais> resumen() { return servicio.resumen(); }

  @GetMapping("/tipos-documento")
  @Operation(summary = "Tipos de documento de identidad admitidos por país, con su ayuda")
  public Map<String, List<TipoDocumentoRespuesta>> tiposDocumento() { return servicio.tiposDocumento(); }

  @GetMapping("/personas")
  @Operation(summary = "Persona ya registrada con ese documento (404 si no existe)")
  public PersonaRespuesta persona(@RequestParam String pais, @RequestParam String tipo, @RequestParam String numero) {
    return servicio.personaPorDocumento(pais, tipo, numero);
  }

  @GetMapping("/{id}")
  public CertificadoPersonalRespuesta obtener(@PathVariable UUID id) { return servicio.obtener(id); }

  @PostMapping
  @Operation(summary = "Registra un certificado personal (certificados:gestionar); crea o actualiza la persona")
  public ResponseEntity<CertificadoPersonalRespuesta> registrar(@Valid @RequestBody SolicitudRegistro solicitud) {
    CertificadoPersonalRespuesta r = servicio.registrar(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/certificados-personales/" + r.id())).body(r);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Corrige los datos del certificado con un motivo (queda en la bitácora)")
  public CertificadoPersonalRespuesta corregir(@PathVariable UUID id, @Valid @RequestBody SolicitudCorreccion solicitud) {
    return servicio.corregir(id, solicitud);
  }

  @PostMapping("/{id}/anulacion")
  @Operation(summary = "Anula el certificado con un motivo; deja de mostrarse en la consulta pública")
  public CertificadoPersonalRespuesta anular(@PathVariable UUID id, @Valid @RequestBody SolicitudAnulacion solicitud) {
    return servicio.anular(id, solicitud);
  }

  @PutMapping("/{id}/pdf")
  @Operation(summary = "Asigna un nuevo PDF (documento CERTIFICADO_PDF)")
  public CertificadoPersonalRespuesta pdf(@PathVariable UUID id, @Valid @RequestBody SolicitudPdfPersonal solicitud) {
    return servicio.adjuntarPdf(id, solicitud);
  }
}
