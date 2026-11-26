package pe.com.iqc.flow.correos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.correos.TipoCorreo;
import pe.com.iqc.flow.correos.application.DtosBandeja.Contadores;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoDetalle;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoEnviado;
import pe.com.iqc.flow.correos.application.DtosBandeja.CorreoResumen;
import pe.com.iqc.flow.correos.application.DtosBandeja.EstadoCuenta;
import pe.com.iqc.flow.correos.application.DtosBandeja.FiltroBandeja;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoDescarte;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoReclasificacion;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoSincronizacion;
import pe.com.iqc.flow.correos.application.DtosBandeja.SolicitudActualizacion;
import pe.com.iqc.flow.correos.application.DtosBandeja.SolicitudEnvio;
import pe.com.iqc.flow.correos.application.DtosBandeja.SolicitudRespuesta;
import pe.com.iqc.flow.correos.application.ServicioBandeja;
import pe.com.iqc.flow.correos.application.ServicioBandeja.Descarga;
import pe.com.iqc.flow.correos.domain.EstadoCorreo;

@RestController
@RequestMapping("/api/v1/correos")
@Tag(name = "Bandeja de correo")
public class BandejaController {
  private final ServicioBandeja servicio;

  public BandejaController(ServicioBandeja servicio) { this.servicio = servicio; }

  @GetMapping("/bandeja")
  @Operation(summary = "Correos recibidos, del más nuevo al más antiguo; filtros por cuenta, tipo, estado, texto, fechas, sin atender y responsable. La PUBLICIDAD no sale salvo tipo=PUBLICIDAD o incluirPublicidad=true (correos:leer)")
  public RespuestaPaginada<CorreoResumen> listar(@RequestParam(required = false) String cuenta, @RequestParam(required = false) TipoCorreo tipo,
      @RequestParam(required = false) EstadoCorreo estado, @RequestParam(required = false) String texto,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, @RequestParam(required = false) Boolean sinAtender,
      @RequestParam(required = false) UUID responsableId, @RequestParam(required = false) Boolean incluirPublicidad, @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int tamano) {
    return servicio.listar(new FiltroBandeja(cuenta, tipo, estado, texto, desde, hasta, sinAtender, responsableId, incluirPublicidad), pagina, tamano);
  }

  @GetMapping("/bandeja/contadores")
  @Operation(summary = "Cuántos correos NUEVO hay por cuenta y por tipo y cuántos EN_CURSO, sin contar la PUBLICIDAD, que va aparte en `publicidad` (correos:leer)")
  public Contadores contadores() { return servicio.contadores(); }

  @GetMapping("/bandeja/sincronizacion")
  @Operation(summary = "Cómo va la lectura del buzón de cada cuenta: última pasada, nuevos y error (correos:leer)")
  public List<EstadoCuenta> estadoSincronizacion() { return servicio.estadoSincronizacion(); }

  @PostMapping("/bandeja/sincronizacion")
  @Operation(summary = "«Revisar ahora»: lee los buzones ya; `enCurso` verdadero si otra revisión estaba corriendo (correos:responder)")
  public ResultadoSincronizacion sincronizar() { return servicio.sincronizarAhora(); }

  @PostMapping("/bandeja/reclasificacion")
  @Operation(summary = "Vuelve a correr las reglas sobre los correos clasificados por regla (nunca los fijados a mano o por la IA); devuelve cuántos cambiaron de tipo (correos:responder)")
  public ResultadoReclasificacion reclasificar() { return servicio.reclasificar(); }

  @PostMapping("/bandeja/descartar-publicidad")
  @Operation(summary = "Pasa a DESCARTADO toda la PUBLICIDAD en estado NUEVO; devuelve cuántas (correos:responder)")
  public ResultadoDescarte descartarPublicidad() { return servicio.descartarPublicidad(); }

  @GetMapping("/bandeja/{id}")
  @Operation(summary = "Un correo con su texto, adjuntos, cliente, hilo y respuestas enviadas; el texto es siempre texto plano (correos:leer)")
  public CorreoDetalle detalle(@PathVariable UUID id) { return servicio.detalle(id); }

  @PatchMapping("/bandeja/{id}")
  @Operation(summary = "Cambia tipo, estado, responsable o cliente de un correo; exige la versión que se vio (correos:responder)")
  public CorreoDetalle actualizar(@PathVariable UUID id, @Valid @RequestBody SolicitudActualizacion solicitud) { return servicio.actualizar(id, solicitud); }

  @GetMapping("/bandeja/{id}/adjuntos/{indice}")
  @Operation(summary = "Descarga un adjunto (siempre como descarga, nunca se muestra en el navegador) (correos:leer)")
  public ResponseEntity<byte[]> adjunto(@PathVariable UUID id, @PathVariable @Min(0) int indice) { return descarga(servicio.adjunto(id, indice)); }

  @GetMapping("/bandeja/{id}/eml")
  @Operation(summary = "Descarga el correo original completo (.eml) (correos:leer)")
  public ResponseEntity<byte[]> eml(@PathVariable UUID id) { return descarga(servicio.eml(id)); }

  @PostMapping("/bandeja/{id}/respuestas")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Responde un correo conservando el hilo; con `marcarAtendido` lo pasa a ATENDIDO (correos:responder)")
  public CorreoEnviado responder(@PathVariable UUID id, @Valid @RequestBody SolicitudRespuesta solicitud) { return servicio.responder(id, solicitud); }

  @PostMapping("/enviados")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Envía un correo nuevo desde una cuenta (correos:responder)")
  public CorreoEnviado enviar(@Valid @RequestBody SolicitudEnvio solicitud) { return servicio.enviarNuevo(solicitud); }

  /**descarga segura: siempre `attachment` y `nosniff`, para que el navegador nunca interprete el contenido (HTML, SVG, scripts) como página*/
  private static ResponseEntity<byte[]> descarga(Descarga d) {
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.tipo())).contentLength(d.contenido().length)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.nombre(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(d.contenido());
  }
}
