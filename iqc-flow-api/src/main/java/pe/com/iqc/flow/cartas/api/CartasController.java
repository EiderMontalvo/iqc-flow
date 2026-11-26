package pe.com.iqc.flow.cartas.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.com.iqc.flow.cartas.application.ServicioCartas;
import pe.com.iqc.flow.cartas.application.ServicioCartas.Archivo;
import pe.com.iqc.flow.cartas.application.ServicioCartas.CartaResumen;
import pe.com.iqc.flow.cartas.application.ServicioCartas.CertificadoCliente;
import pe.com.iqc.flow.cartas.application.ServicioCartas.Firmante;
import pe.com.iqc.flow.cartas.application.ServicioCartas.MiFirma;
import pe.com.iqc.flow.cartas.application.ServicioCartas.SolicitudCarta;
import pe.com.iqc.flow.cartas.application.ServicioCartas.TipoInfo;
import pe.com.iqc.flow.compartido.api.RespuestaPaginada;

@RestController
@RequestMapping("/api/v1/cartas")
@Tag(name = "Cartas a clientes")
@Validated
public class CartasController {
  private final ServicioCartas servicio;

  public CartasController(ServicioCartas servicio) { this.servicio = servicio; }

  @GetMapping("/tipos")
  public List<TipoInfo> tipos() { return servicio.tipos(); }

  @GetMapping
  public RespuestaPaginada<CartaResumen> listar(@RequestParam(required = false) UUID clienteId, @RequestParam(required = false) String texto,
      @RequestParam(defaultValue = "0") @Min(0) int pagina, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
    return servicio.listar(clienteId, texto, pagina, tamano);
  }

  @GetMapping("/clientes/{clienteId}/certificados")
  @Operation(summary = "Certificados del cliente que puede mencionar una carta (cartas:generar)")
  public List<CertificadoCliente> certificados(@PathVariable UUID clienteId) { return servicio.certificadosDelCliente(clienteId); }

  @GetMapping("/firmantes")
  @Operation(summary = "Personas que ya subieron su firma y pueden firmar cartas (cartas:generar)")
  public List<Firmante> firmantes() { return servicio.firmantes(); }

  @PostMapping("/vista-previa")
  @Operation(summary = "PDF de la carta sin guardarla ni numerarla (cartas:generar)")
  public ResponseEntity<byte[]> vistaPrevia(@Valid @RequestBody SolicitudCarta s) {
    return archivo(new Archivo("vista-previa.pdf", "application/pdf", servicio.vistaPrevia(s)), true);
  }

  @PostMapping
  @Operation(summary = "Emite la carta: le da número CAR-AAAA-0001 y guarda su PDF (cartas:generar)")
  public ResponseEntity<CartaResumen> emitir(@Valid @RequestBody SolicitudCarta s) {
    CartaResumen c = servicio.emitir(s);
    return ResponseEntity.created(URI.create("/api/v1/cartas/" + c.id() + "/pdf")).body(c);
  }

  @GetMapping("/{id}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable UUID id) { return archivo(servicio.pdf(id), true); }

  @GetMapping("/{id}/word")
  @Operation(summary = "Word de la carta para corregirla a mano (cartas:generar)")
  public ResponseEntity<byte[]> word(@PathVariable UUID id) { return archivo(servicio.word(id), false); }

  // ------------------------------------------------ firma de quien inició sesión

  @GetMapping("/mi-firma")
  public MiFirma miFirma() { return servicio.miFirma(); }

  @PutMapping(value = "/mi-firma", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Guarda la firma (PNG o JPEG, máx. 2 MB), el nombre con el que firma y su cargo")
  public MiFirma guardarMiFirma(@RequestPart(value = "archivo", required = false) MultipartFile archivo,
      @RequestParam(required = false) String nombreMostrado, @RequestParam String cargo) throws IOException {
    return servicio.guardarMiFirma(archivo == null || archivo.isEmpty() ? null : archivo.getBytes(), nombreMostrado, cargo);
  }

  @DeleteMapping("/mi-firma")
  public ResponseEntity<Void> borrarMiFirma() {
    servicio.borrarMiFirma();
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<byte[]> archivo(Archivo a, boolean enLinea) {
    ContentDisposition d = (enLinea ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(a.nombre()).build();
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.tipoContenido()))
        .header(HttpHeaders.CONTENT_DISPOSITION, d.toString()).body(a.contenido());
  }
}
