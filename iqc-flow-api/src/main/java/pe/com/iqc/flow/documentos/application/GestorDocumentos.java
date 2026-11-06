package pe.com.iqc.flow.documentos.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.documentos.CategoriaDocumento;
import pe.com.iqc.flow.documentos.ServicioDocumentos;
import pe.com.iqc.flow.documentos.domain.Documento;
import pe.com.iqc.flow.documentos.domain.TipoArchivo;
import pe.com.iqc.flow.documentos.infrastructure.AlmacenArchivos;
import pe.com.iqc.flow.documentos.infrastructure.DocumentoRepository;
import pe.com.iqc.flow.documentos.infrastructure.PropiedadesDocumentos;

/**subida y descarga de documentos adjuntos, y la API pública para que otros módulos los referencien*/
@Service
public class GestorDocumentos implements ServicioDocumentos {
  private static final Logger log = LoggerFactory.getLogger(GestorDocumentos.class);
  private static final int LARGO_MAXIMO_NOMBRE = 255;

  private final DocumentoRepository documentos;
  private final AlmacenArchivos almacen;
  private final PropiedadesDocumentos propiedades;
  private final ActorActual actorActual;
  private final Bitacora bitacora;
  private final Clock reloj;

  public GestorDocumentos(DocumentoRepository documentos, AlmacenArchivos almacen, PropiedadesDocumentos propiedades,
      ActorActual actorActual, Bitacora bitacora, Clock reloj) {
    this.documentos = documentos;
    this.almacen = almacen;
    this.propiedades = propiedades;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  public record ContenidoDocumento(DocumentoReferencia documento, InputStream contenido) {
  }

  /**valida tamaño y formato real (PDF, PNG o JPEG), calcula la huella SHA-256 y guarda el archivo*/
  @Transactional
  @PreAuthorize("hasAuthority('documentos:subir')")
  public DocumentoReferencia subir(CategoriaDocumento categoria, String nombreOriginal, byte[] contenido) {
    UsuarioAutenticado actor = actorActual.requerido();
    if (contenido == null || contenido.length == 0) {
      throw new ReglaNegocioException("ARCHIVO_VACIO", "Adjunta un archivo con contenido.");
    }
    if (contenido.length > propiedades.tamanoMaximo().toBytes()) {
      throw new ReglaNegocioException("ARCHIVO_MUY_GRANDE",
          "El archivo supera el máximo de " + propiedades.tamanoMaximo().toMegabytes() + " MB.");
    }
    TipoArchivo tipo = TipoArchivo.detectar(contenido)
        .orElseThrow(() -> new ReglaNegocioException("FORMATO_NO_ADMITIDO", "Solo se admiten archivos PDF, PNG o JPEG."));
    Instant ahora = Instant.now(reloj);
    ZonedDateTime fecha = ahora.atZone(ZoneOffset.UTC);
    String ruta = "%s/%d/%02d/%s.%s".formatted(actor.organizacionId(), fecha.getYear(), fecha.getMonthValue(),
        UUID.randomUUID(), tipo.extension());
    try {
      almacen.guardar(ruta, contenido);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo guardar el documento", e);
    }
    eliminarSiSeRevierte(ruta);
    Documento documento = documentos.saveAndFlush(new Documento(actor.organizacionId(), categoria,
        nombre(nombreOriginal, tipo), tipo.tipoContenido(), contenido.length, sha256(contenido), ruta, actor.id(), ahora));
    bitacora.registrar(EntradaBitacora.accion("DOCUMENTO_SUBIDO").entidad("DOCUMENTO", documento.getId())
        .estadoNuevo(Map.of("categoria", categoria.name(), "nombre", documento.getNombreOriginal(),
            "tamanoBytes", documento.getTamanoBytes(), "sha256", documento.getSha256())));
    return referencia(documento);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public DocumentoReferencia obtener(UUID id) { return referencia(cargar(id)); }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public ContenidoDocumento descargar(UUID id) {
    Documento documento = cargar(id);
    try {
      return new ContenidoDocumento(referencia(documento), almacen.abrir(documento.getRutaAlmacen()));
    } catch (IOException e) {
      throw new UncheckedIOException("No se encontró el contenido del documento " + id, e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentoReferencia> buscar(UUID organizacionId, UUID documentoId) {
    return documentos.findByIdAndOrganizacionId(documentoId, organizacionId).map(GestorDocumentos::referencia);
  }

  @Override
  @Transactional(readOnly = true)
  public DocumentoReferencia exigir(UUID organizacionId, UUID documentoId, CategoriaDocumento categoria) {
    DocumentoReferencia documento = buscar(organizacionId, documentoId)
        .orElseThrow(() -> new ReglaNegocioException("DOCUMENTO_INVALIDO", "El documento adjunto no existe."));
    if (documento.categoria() != categoria) {
      throw new ReglaNegocioException("CATEGORIA_DOCUMENTO",
          "El documento adjunto debe ser de la categoría " + categoria + ".");
    }
    return documento;
  }

  private Documento cargar(UUID id) {
    return documentos.findByIdAndOrganizacionId(id, actorActual.requerido().organizacionId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Documento", id));
  }

  /**si la transacción se revierte, el archivo ya escrito no debe quedar huérfano en el almacén*/
  private void eliminarSiSeRevierte(String ruta) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int estado) {
        if (estado == STATUS_COMMITTED) return;
        try {
          almacen.eliminar(ruta);
        } catch (IOException e) {
          log.warn("No se pudo eliminar el archivo huérfano {}", ruta, e);
        }
      }
    });
  }

  private static String nombre(String original, TipoArchivo tipo) {
    String limpio = original == null ? "" : original.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    if (limpio.isEmpty()) limpio = "documento." + tipo.extension();
    return limpio.length() > LARGO_MAXIMO_NOMBRE ? limpio.substring(limpio.length() - LARGO_MAXIMO_NOMBRE) : limpio;
  }

  private static String sha256(byte[] contenido) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static DocumentoReferencia referencia(Documento d) {
    return new DocumentoReferencia(d.getId(), d.getCategoria(), d.getNombreOriginal(), d.getTipoContenido(),
        d.getTamanoBytes(), d.getSha256(), d.getSubidoPor(), d.getCreadoEn());
  }
}
