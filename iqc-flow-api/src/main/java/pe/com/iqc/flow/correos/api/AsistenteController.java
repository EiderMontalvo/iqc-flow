package pe.com.iqc.flow.correos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.correos.application.DtosAsistente.BorradorRespuesta;
import pe.com.iqc.flow.correos.application.DtosAsistente.EstadoAsistente;
import pe.com.iqc.flow.correos.application.DtosAsistente.RespuestaConsulta;
import pe.com.iqc.flow.correos.application.DtosAsistente.ResumenCorreo;
import pe.com.iqc.flow.correos.application.DtosAsistente.SolicitudBorrador;
import pe.com.iqc.flow.correos.application.DtosAsistente.SolicitudConsulta;
import pe.com.iqc.flow.correos.application.ServicioAsistenteCorreos;

@RestController
@RequestMapping("/api/v1/correos")
@Tag(name = "Asistente de IA de la bandeja")
public class AsistenteController {
  private final ServicioAsistenteCorreos servicio;

  public AsistenteController(ServicioAsistenteCorreos servicio) { this.servicio = servicio; }

  @GetMapping("/asistente/estado")
  @Operation(summary = "Si la IA puede usarse ahora (encendida en Configuración y con clave en el servidor) y, si no, por qué (correos:leer)")
  public EstadoAsistente estado() { return servicio.estado(); }

  @PostMapping("/bandeja/{id}/resumen")
  @Operation(summary = "Resumen del correo con IA: dos o tres frases y los puntos clave; se guarda para no repetir la llamada (correos:leer)")
  public ResumenCorreo resumen(@PathVariable UUID id) { return servicio.resumir(id); }

  @PostMapping("/bandeja/{id}/borrador-respuesta")
  @Operation(summary = "Borrador de respuesta al correo con IA. Es solo un borrador: la persona lo revisa y lo envía por /respuestas (correos:responder)")
  public BorradorRespuesta borrador(@PathVariable UUID id, @Valid @RequestBody(required = false) SolicitudBorrador solicitud) { return servicio.redactar(id, solicitud); }

  @PostMapping("/asistente/consulta")
  @Operation(summary = "Responde una pregunta sobre los correos de la bandeja (hasta 10, sin publicidad salvo que se pida) y cita cuáles usó (correos:leer)")
  public RespuestaConsulta consulta(@Valid @RequestBody SolicitudConsulta solicitud) { return servicio.consultar(solicitud); }
}
