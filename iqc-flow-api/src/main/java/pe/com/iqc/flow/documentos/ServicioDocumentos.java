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

  /**contenido del archivo, para procesos del sistema (publicación, QR). No aplica permisos: los aplica quien lo usa*/
  byte[] leerContenido(UUID organizacionId, UUID documentoId);

  /**
   *guarda un documento que genera el sistema (p. ej. el PDF de un certificado): valida tamaño y formato, calcula la
   *huella y lo deja en la bitácora, pero no exige documentos:subir; quien lo llama aplica sus propios permisos
   */
  DocumentoReferencia guardarGenerado(CategoriaDocumento categoria, String nombre, byte[] contenido);

  record DocumentoReferencia(UUID id, CategoriaDocumento categoria, String nombreOriginal, String tipoContenido,
      long tamanoBytes, String sha256, UUID subidoPor, Instant creadoEn) {
  }
}
