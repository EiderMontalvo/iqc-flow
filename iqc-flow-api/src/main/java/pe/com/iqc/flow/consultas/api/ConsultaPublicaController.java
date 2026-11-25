package pe.com.iqc.flow.consultas.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.compartido.domain.LimiteExcedidoException;
import pe.com.iqc.flow.consultas.application.LimitadorConsultas;
import pe.com.iqc.flow.consultas.application.ServicioConsultaPublica;
import pe.com.iqc.flow.consultas.application.ServicioConsultaPublica.Pdf;
import pe.com.iqc.flow.consultas.application.ServicioConsultaPublica.Resultado;

/**consulta pública sin sesión: portal PE (consultas.iqcperu.com) o LATAM (consultas.iqc-latam.com)*/
@RestController
@RequestMapping("/api/v1/publico/consultas/{portal}")
@Tag(name = "Consulta pública de certificados")
public class ConsultaPublicaController {
  private final ServicioConsultaPublica servicio;
  private final LimitadorConsultas limitador;

  public ConsultaPublicaController(ServicioConsultaPublica servicio, LimitadorConsultas limitador) {
    this.servicio = servicio;
    this.limitador = limitador;
  }

  @GetMapping("/qr/{token}")
  @Operation(summary = "Certificado al que apunta un QR, o la dirección del portal que le corresponde")
  public Resultado qr(@PathVariable String portal, @PathVariable String token, HttpServletRequest solicitud) {
    limitar(solicitud);
    return servicio.porQr(portal, token);
  }

  @GetMapping("/verificacion")
  @Operation(summary = "Verifica un certificado empresarial o personal con el documento del titular y el código")
  public Resultado verificar(@PathVariable String portal, @RequestParam String documento, @RequestParam String codigo,
      HttpServletRequest solicitud) {
    limitar(solicitud);
    return servicio.verificar(portal, documento, codigo);
  }

  @GetMapping("/certificados/{id}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable String portal, @PathVariable UUID id, HttpServletRequest solicitud) {
    limitar(solicitud);
    return respuesta(servicio.pdfEmpresarial(portal, id));
  }

  @GetMapping("/personal-pdf/{token}")
  @Operation(summary = "PDF de un certificado personal emitido (las URL impresas /personal-pdf/{token} llegan aquí)")
  public ResponseEntity<byte[]> pdfPersonal(@PathVariable String portal, @PathVariable UUID token, HttpServletRequest solicitud) {
    limitar(solicitud);
    return respuesta(servicio.pdfPersonal(portal, token));
  }

  private static ResponseEntity<byte[]> respuesta(Pdf pdf) {
    if (pdf.urlExterna() != null) return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(pdf.urlExterna())).build();
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.nombre()).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
        .body(pdf.contenido());
  }

  private void limitar(HttpServletRequest solicitud) {
    if (!limitador.permitir(solicitud.getRemoteAddr())) {
      throw new LimiteExcedidoException("DEMASIADAS_CONSULTAS", "Demasiadas consultas seguidas. Espera un minuto y vuelve a intentarlo.");
    }
  }
}
