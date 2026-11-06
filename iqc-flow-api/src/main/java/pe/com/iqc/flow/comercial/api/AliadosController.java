package pe.com.iqc.flow.comercial.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.iqc.flow.comercial.ServicioAliados;
import pe.com.iqc.flow.comercial.ServicioAliados.AliadoRespuesta;
import pe.com.iqc.flow.comercial.ServicioAliados.SolicitudAliado;

@RestController
@RequestMapping("/api/v1/aliados-comerciales")
@Tag(name = "Aliados comerciales")
public class AliadosController {
  private final ServicioAliados servicio;

  public AliadosController(ServicioAliados servicio) { this.servicio = servicio; }

  @GetMapping
  @Operation(summary = "Aliados comerciales (SPONSOR) con su comisión por defecto")
  public List<AliadoRespuesta> listar() { return servicio.listar(); }

  @PostMapping
  public ResponseEntity<AliadoRespuesta> crear(@Valid @RequestBody SolicitudAliado solicitud) {
    AliadoRespuesta creado = servicio.crear(solicitud);
    return ResponseEntity.created(URI.create("/api/v1/aliados-comerciales/" + creado.id())).body(creado);
  }

  @PutMapping("/{id}")
  public AliadoRespuesta actualizar(@PathVariable UUID id, @Valid @RequestBody SolicitudAliado solicitud) {
    return servicio.actualizar(id, solicitud);
  }
}
