package pe.com.iqc.flow.certificaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.certificaciones.application.ServicioLecturaCertificados;
import pe.com.iqc.flow.certificaciones.application.ServicioLecturaCertificados.Herramientas;
import pe.com.iqc.flow.certificaciones.application.ServicioLecturaCertificados.LecturaRespuesta;
import pe.com.iqc.flow.certificaciones.application.ServicioLecturaCertificados.SolicitudLectura;

@RestController
@Tag(name = "Registro de certificados: modos y lectura de PDF")
public class LecturaCertificadosController {
  private final ServicioLecturaCertificados servicio;

  public LecturaCertificadosController(ServicioLecturaCertificados servicio) { this.servicio = servicio; }

  @GetMapping("/api/v1/certificados/herramientas")
  @Operation(summary = "Modos de registro (manual, automatizado, plantilla) y herramientas habilitadas para el usuario")
  public Herramientas herramientas() { return servicio.herramientas(); }

  @PostMapping("/api/v1/certificados/lectura")
  @Operation(summary = "Lee un PDF subido (documento CERTIFICADO_PDF) y propone los campos del formulario (certificados:autocompletar)")
  public LecturaRespuesta leer(@Valid @RequestBody SolicitudLectura solicitud) { return servicio.leer(solicitud); }
}
