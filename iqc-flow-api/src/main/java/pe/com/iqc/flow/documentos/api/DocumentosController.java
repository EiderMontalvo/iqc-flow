package pe.com.iqc.flow.documentos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos.DocumentoReferencia;
import pe.com.iqc.flow.documentos.application.GestorDocumentos;
import pe.com.iqc.flow.documentos.application.GestorDocumentos.ContenidoDocumento;

@RestController
@RequestMapping("/api/v1/documentos")
@Tag(name = "Documentos adjuntos")
public class DocumentosController {
  private final GestorDocumentos gestor;

  public DocumentosController(GestorDocumentos gestor) { this.gestor = gestor; }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Sube un PDF, PNG o JPEG (máx. 15 MB); devuelve el id para referenciarlo (documentos:subir)")
  public ResponseEntity<DocumentoReferencia> subir(@RequestParam CategoriaDocumento categoria,
      @RequestPart("archivo") MultipartFile archivo) throws IOException {
    DocumentoReferencia creado = gestor.subir(categoria, archivo.getOriginalFilename(), archivo.getBytes());
    return ResponseEntity.created(URI.create("/api/v1/documentos/" + creado.id())).body(creado);
  }

  @GetMapping("/{id}")
  public DocumentoReferencia obtener(@PathVariable UUID id) { return gestor.obtener(id); }

  @GetMapping("/{id}/contenido")
  @Operation(summary = "Descarga el archivo como adjunto")
  public ResponseEntity<InputStreamResource> descargar(@PathVariable UUID id) {
    ContenidoDocumento contenido = gestor.descargar(id);
    DocumentoReferencia d = contenido.documento();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(d.tipoContenido()))
        .contentLength(d.tamanoBytes())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(d.nombreOriginal(), java.nio.charset.StandardCharsets.UTF_8)
                .build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(new InputStreamResource(contenido.contenido()));
  }
}
