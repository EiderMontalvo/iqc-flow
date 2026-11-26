package pe.com.iqc.flow.auditorias;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 *API pública (sin permisos: los aplica quien la usa) para imprimir el certificado de un expediente: sus datos de
 *ciclo y el registro del PDF generado como la versión vigente
 */
public interface ImpresionCertificados {
  /**subdivisión y clase del sector: «46», «4610» o varias separadas por coma («70, 59»); la misma regla en la base de datos*/
  String PATRON_SECTOR = "\\d{1,6}(\\s*,\\s*\\d{1,6})*";
  /**procesos no aplicables de la norma: «NA» o los numerales excluidos («8.3», «7.1.5, 8.3»)*/
  String PATRON_PROCESOS = "NA|\\d+(\\.\\d+)*(\\s*[,;]\\s*\\d+(\\.\\d+)*)*";

  Optional<DatosImpresion> datos(UUID organizacionId, UUID certificadoId);

  /**guarda el documento ya subido como nueva versión vigente del PDF (el anterior queda en el historial) y lo deja en la bitácora*/
  void registrarPdfGenerado(UUID organizacionId, UUID certificadoId, UUID documentoId, UUID actorId, String motivo);

  /**
   *estado: EMITIDO, SUSPENDIDO…; limitesVigilancia: las fechas antes de las que debe hacerse cada vigilancia del ciclo
   *(vacía si el certificado aún no tiene fecha de otorgamiento); paisMarca: marca elegida a mano o nula; subdivisión,
   *clase, procesos no aplicables y anexos solo los lleva el certificado de IQC Global (nulos si no se registraron)
   */
  record DatosImpresion(UUID id, UUID clienteId, String codigo, String esquema, String norma, String edicion, String alcance,
      String estado, int ciclo, int numeroVersion, LocalDate fechaOtorgamiento, LocalDate fechaEmision,
      LocalDate fechaExpiracion, List<LocalDate> limitesVigilancia, String paisMarca, String subdivision, String clase,
      String procesosNoAplicables, String anexos) {
  }
}
