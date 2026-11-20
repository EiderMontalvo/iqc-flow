package pe.com.iqc.flow.certificaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.certificaciones.application.PublicadorCertificados.ResultadoPublicacion;
import pe.com.iqc.flow.certificaciones.application.ServicioMarcas;
import pe.com.iqc.flow.certificaciones.application.ServicioMarcas.MarcaRespuesta;
import pe.com.iqc.flow.certificaciones.application.ServicioMarcas.SolicitudMarcaPais;
import pe.com.iqc.flow.certificaciones.application.ServicioPublicacion;
import pe.com.iqc.flow.certificaciones.application.ServicioPublicacion.EstadoPublicacion;
import pe.com.iqc.flow.certificaciones.application.ServicioQr;
import pe.com.iqc.flow.certificaciones.application.ServicioQr.EnlaceConsulta;

@RestController
@Tag(name = "Marcas, QR y consulta pública")
public class CertificacionesController {
  private final ServicioMarcas marcas;
  private final ServicioQr qr;
  private final ServicioPublicacion publicacion;

  public CertificacionesController(ServicioMarcas marcas, ServicioQr qr, ServicioPublicacion publicacion) {
    this.marcas = marcas;
    this.qr = qr;
    this.publicacion = publicacion;
  }

  @GetMapping("/api/v1/marcas")
  @Operation(summary = "Marcas por país (IQC Perú, IQC Ecuador...) con su dirección de consulta; las no configuradas salen predeterminadas")
  public List<MarcaRespuesta> marcas() { return marcas.listar(); }

  @PutMapping("/api/v1/marcas/{pais}")
  @Operation(summary = "Configura la marca de un país (organizacion:configurar); version nula si aún es la predeterminada")
  public MarcaRespuesta definirMarca(@PathVariable String pais, @Valid @RequestBody SolicitudMarcaPais solicitud) {
    return marcas.definir(pais, solicitud);
  }

  @GetMapping("/api/v1/certificados/{id}/consulta")
  @Operation(summary = "Dirección de consulta pública que lleva el QR del certificado")
  public EnlaceConsulta enlace(@PathVariable UUID id) { return qr.enlace(id); }

  @GetMapping(value = "/api/v1/certificados/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
  @Operation(summary = "QR del certificado en PNG (tamano en píxeles, 160 a 2000), con el logo de su marca si está configurado")
  public ResponseEntity<byte[]> qr(@PathVariable UUID id, @RequestParam(defaultValue = "600") int tamano,
      @RequestParam(defaultValue = "true") boolean logo) {
    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff").body(qr.png(id, tamano, logo));
  }

  @GetMapping("/api/v1/publicacion")
  @Operation(summary = "Estado de la publicación a la base pública de consulta y certificados con error")
  public EstadoPublicacion estadoPublicacion() { return publicacion.estado(); }

  @PostMapping("/api/v1/publicacion")
  @Operation(summary = "Publica ahora los cambios (certificados:gestionar); forzar vuelve a escribir todo")
  public ResultadoPublicacion publicar(@RequestParam(defaultValue = "false") boolean forzar) {
    return publicacion.ejecutar(forzar);
  }
}
