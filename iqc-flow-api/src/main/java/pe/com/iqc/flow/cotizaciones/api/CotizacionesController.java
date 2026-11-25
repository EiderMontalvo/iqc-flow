package pe.com.iqc.flow.cotizaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.CotizacionDetalle;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.CotizacionResumen;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.ResumenCotizaciones;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.SolicitudAnulacion;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.SolicitudCotizacion;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.SolicitudEnvio;
import pe.com.iqc.flow.cotizaciones.application.DtosCotizaciones.SolicitudRespuesta;
import pe.com.iqc.flow.cotizaciones.application.ServicioAnalisisSolicitud;
import pe.com.iqc.flow.cotizaciones.application.ServicioAnalisisSolicitud.AnalisisSolicitud;
import pe.com.iqc.flow.cotizaciones.application.ServicioCotizaciones;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import pe.com.iqc.flow.cotizaciones.domain.EstadoCotizacion;

@RestController
@RequestMapping("/api/v1/cotizaciones")
@Tag(name = "Cotizaciones")
public class CotizacionesController {
  private final ServicioCotizaciones servicio;
  private final ServicioAnalisisSolicitud analisis;

  public CotizacionesController(ServicioCotizaciones servicio, ServicioAnalisisSolicitud analisis) {
    this.servicio = servicio;
    this.analisis = analisis;
  }

  @PostMapping(value = "/analisis-solicitud", consumes = "multipart/form-data")
  @Operation(summary = "Analiza la solicitud de un cliente (PDF, Word o texto) y propone los datos de la cotización; no guarda nada")
  public AnalisisSolicitud analizarSolicitud(@RequestParam(required = false) MultipartFile archivo,
      @RequestParam(required = false) String texto) throws IOException {
    if ((archivo == null || archivo.isEmpty()) && (texto == null || texto.isBlank())) {
      throw new pe.com.iqc.flow.compartido.domain.ReglaNegocioException("SOLICITUD_VACIA", "Sube el archivo de la solicitud o pega su texto.");
    }
    if (archivo != null && archivo.getSize() > 15L * 1024 * 1024) {
      throw new pe.com.iqc.flow.compartido.domain.ReglaNegocioException("SOLICITUD_GRANDE", "El archivo supera los 15 MB.");
    }
    return analisis.analizar(archivo == null ? null : archivo.getBytes(), texto);
  }

  @GetMapping
  @Operation(summary = "Cotizaciones; filtros por estado, sin respuesta, cliente o texto (número, razón social o RUC)")
  public RespuestaPaginada<CotizacionResumen> listar(@RequestParam(required = false) EstadoCotizacion estado,
      @RequestParam(defaultValue = "false") boolean sinRespuesta, @RequestParam(required = false) String texto,
      @RequestParam(required = false) UUID clienteId, @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return RespuestaPaginada.de(servicio.listar(estado, sinRespuesta, texto, clienteId, pagina, tamano));
  }

  @GetMapping("/resumen")
  @Operation(summary = "Cifras de la cabecera: borradores, enviadas, sin respuesta, aceptadas del mes y conversión")
  public ResumenCotizaciones resumen() { return servicio.resumen(); }

  @GetMapping("/{id}")
  public CotizacionDetalle obtener(@PathVariable UUID id) { return servicio.obtener(id); }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Crea una cotización en borrador con número correlativo COT-AAAA-0001")
  public CotizacionDetalle crear(@Valid @RequestBody SolicitudCotizacion s) { return servicio.crear(s); }

  @PutMapping("/{id}")
  @Operation(summary = "Edita una cotización en borrador")
  public CotizacionDetalle editar(@PathVariable UUID id, @Valid @RequestBody SolicitudCotizacion s) { return servicio.editar(id, s); }

  @PostMapping("/{id}/envio")
  @Operation(summary = "Marca la cotización como enviada (fecha y destinatarios)")
  public CotizacionDetalle enviar(@PathVariable UUID id, @Valid @RequestBody SolicitudEnvio s) { return servicio.enviar(id, s); }

  @PostMapping("/{id}/respuesta")
  @Operation(summary = "Registra la respuesta del cliente: aceptada o rechazada (con motivo)")
  public CotizacionDetalle responder(@PathVariable UUID id, @Valid @RequestBody SolicitudRespuesta s) { return servicio.responder(id, s); }

  @PostMapping("/{id}/anulacion")
  public CotizacionDetalle anular(@PathVariable UUID id, @Valid @RequestBody SolicitudAnulacion s) { return servicio.anular(id, s); }

  @PostMapping("/{id}/duplicado")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Copia editable con número nuevo")
  public CotizacionDetalle duplicar(@PathVariable UUID id) { return servicio.duplicar(id); }
}
