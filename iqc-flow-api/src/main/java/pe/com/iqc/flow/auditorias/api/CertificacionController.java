package pe.com.iqc.flow.auditorias.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.CertificadoRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.CondicionesEntrega;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DecisionRegistrada;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DecisionRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DeclaracionRespuesta;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DeclaracionesAuditoria;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.DecisorPosible;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudCertificadoExterno;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudCondicionesEntrega;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudCorregirCertificado;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudDecision;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudDeclaracion;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudEntrega;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudFirma;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudRevision;
import pe.com.iqc.flow.auditorias.application.DtosCertificacion.SolicitudTraduccion;
import pe.com.iqc.flow.auditorias.application.ServicioCertificados;
import pe.com.iqc.flow.auditorias.application.ServicioDecisiones;
import pe.com.iqc.flow.auditorias.application.ServicioImparcialidad;

@RestController
@RequestMapping("/api/v1/expedientes/{id}")
@Tag(name = "Imparcialidad, decisiones y certificados")
public class CertificacionController {
  private final ServicioImparcialidad imparcialidad;
  private final ServicioDecisiones decisiones;
  private final ServicioCertificados certificados;

  public CertificacionController(ServicioImparcialidad imparcialidad, ServicioDecisiones decisiones,
      ServicioCertificados certificados) {
    this.imparcialidad = imparcialidad;
    this.decisiones = decisiones;
    this.certificados = certificados;
  }

  @GetMapping("/auditorias/{auditoriaId}/declaraciones")
  @Operation(summary = "Declaraciones de conflicto de interés del equipo y miembros que aún no declaran")
  public DeclaracionesAuditoria declaraciones(@PathVariable UUID id, @PathVariable UUID auditoriaId) {
    return imparcialidad.declaraciones(id, auditoriaId);
  }

  @PutMapping("/auditorias/{auditoriaId}/declaraciones/{auditorId}")
  @Operation(summary = "Registra la declaración: el propio auditor (usuario vinculado) o el coordinador con la declaración firmada adjunta")
  public DeclaracionRespuesta declarar(@PathVariable UUID id, @PathVariable UUID auditoriaId,
      @PathVariable UUID auditorId, @Valid @RequestBody SolicitudDeclaracion solicitud) {
    return imparcialidad.declarar(id, auditoriaId, auditorId, solicitud);
  }

  @GetMapping("/decisores")
  @Operation(summary = "Usuarios autorizados a decidir y si están habilitados para este cliente (imparcialidad)")
  public List<DecisorPosible> decisores(@PathVariable UUID id) { return imparcialidad.decisores(id); }

  @GetMapping("/decisiones")
  public List<DecisionRespuesta> decisiones(@PathVariable UUID id) { return decisiones.listar(id); }

  @PostMapping("/decisiones")
  @Operation(summary = "Registra una decisión (DDC-FMR-008); una favorable genera los borradores de certificado")
  public ResponseEntity<DecisionRegistrada> decidir(@PathVariable UUID id, @Valid @RequestBody SolicitudDecision solicitud) {
    DecisionRegistrada registrada = decisiones.registrar(id, solicitud);
    return ResponseEntity.created(URI.create("/api/v1/expedientes/" + id + "/decisiones/" + registrada.decision().id()))
        .body(registrada);
  }

  @GetMapping("/certificados")
  public List<CertificadoRespuesta> certificados(@PathVariable UUID id) { return certificados.listar(id); }

  @PatchMapping("/certificados/{certificadoId}")
  @Operation(summary = "Corrige un borrador (revisión del alcance, ciclo, alcance); recalcula el código")
  public CertificadoRespuesta corregir(@PathVariable UUID id, @PathVariable UUID certificadoId,
      @Valid @RequestBody SolicitudCorregirCertificado solicitud) {
    return certificados.corregir(id, certificadoId, solicitud);
  }

  @PostMapping("/certificados/{certificadoId}/revision")
  @Operation(summary = "Confirma el checklist de emisión (DDC-MAN-001)")
  public CertificadoRespuesta revisar(@PathVariable UUID id, @PathVariable UUID certificadoId,
      @Valid @RequestBody SolicitudRevision solicitud) {
    return certificados.revisar(id, certificadoId, solicitud);
  }

  @PostMapping("/certificados/{certificadoId}/firma")
  @Operation(summary = "Firma como GERENCIA o JEFE_TECNICO; con las firmas que exige la política se emite")
  public CertificadoRespuesta firmar(@PathVariable UUID id, @PathVariable UUID certificadoId,
      @Valid @RequestBody SolicitudFirma solicitud) {
    return certificados.firmar(id, certificadoId, solicitud);
  }

  @PostMapping("/certificados/externos")
  @Operation(summary = "IQC Global: registra el certificado emitido por India con su código")
  public ResponseEntity<CertificadoRespuesta> registrarExterno(@PathVariable UUID id,
      @Valid @RequestBody SolicitudCertificadoExterno solicitud) {
    CertificadoRespuesta creado = certificados.registrarExterno(id, solicitud);
    return ResponseEntity.created(URI.create("/api/v1/expedientes/" + id + "/certificados/" + creado.id())).body(creado);
  }

  @PutMapping("/certificados/{certificadoId}/traduccion")
  public CertificadoRespuesta traduccion(@PathVariable UUID id, @PathVariable UUID certificadoId,
      @Valid @RequestBody SolicitudTraduccion solicitud) {
    return certificados.adjuntarTraduccion(id, certificadoId, solicitud);
  }

  @GetMapping("/condiciones-entrega")
  @Operation(summary = "Pagos, encuesta y certificados: qué falta para poder entregar")
  public CondicionesEntrega condiciones(@PathVariable UUID id) { return certificados.condiciones(id); }

  @PutMapping("/condiciones-entrega")
  @Operation(summary = "Registra la conformidad de pagos y la encuesta aplicada (entregas:gestionar)")
  public CondicionesEntrega definirCondiciones(@PathVariable UUID id,
      @Valid @RequestBody SolicitudCondicionesEntrega solicitud) {
    return certificados.definirCondiciones(id, solicitud);
  }

  @PostMapping("/entrega")
  @Operation(summary = "Entrega los certificados; bloqueada si los pagos no están conformes o falta la encuesta")
  public List<CertificadoRespuesta> entregar(@PathVariable UUID id, @Valid @RequestBody SolicitudEntrega solicitud) {
    return certificados.entregar(id, solicitud);
  }
}
