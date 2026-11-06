package pe.com.iqc.flow.documentos;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 *API pública de documentos adjuntos para otros módulos. Un módulo guarda la referencia (id) del documento
 *en su propia tabla y valida con {@link #exigir} que pertenezca a la organización y a la categoría esperada
 */
public interface ServicioDocumentos {
  Optional<DocumentoReferencia> buscar(UUID organizacionId, UUID documentoId);

  /**devuelve el documento o lanza un error de negocio si no existe, es de otra organización u otra categoría*/
  DocumentoReferencia exigir(UUID organizacionId, UUID documentoId, CategoriaDocumento categoria);

  record DocumentoReferencia(UUID id, CategoriaDocumento categoria, String nombreOriginal, String tipoContenido,
      long tamanoBytes, String sha256, UUID subidoPor, Instant creadoEn) {
  }
}
