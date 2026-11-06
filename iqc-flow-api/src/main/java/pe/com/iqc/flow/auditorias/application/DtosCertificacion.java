package pe.com.iqc.flow.auditorias.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.auditores.RolAuditor;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.Referencia;
import pe.com.iqc.flow.auditorias.domain.Certificado;
import pe.com.iqc.flow.auditorias.domain.DecisionCertificacion.Clasificacion;
import pe.com.iqc.flow.auditorias.domain.DecisionCertificacion.Via;
import pe.com.iqc.flow.auditorias.domain.DeclaracionConflicto;
import pe.com.iqc.flow.auditorias.domain.EstadoExpediente;
import pe.com.iqc.flow.auditorias.domain.TipoDecision;

/**solicitudes y respuestas de imparcialidad, decisiones, certificados y entrega*/
public final class DtosCertificacion {
  private DtosCertificacion() {
  }

  /**
   *si quien registra es el auditor (usuario vinculado), es su firma; si no, se exige el documento firmado.
   *La versión es la de la declaración anterior (omítela si es la primera)
   */
  public record SolicitudDeclaracion(@NotNull Boolean tieneConflicto, @Size(max = 2000) String detalle,
      UUID documentoId, Long version) {
  }

  public record DeclaracionRespuesta(UUID id, UUID auditorId, String auditor, RolAuditor rol, boolean tieneConflicto,
      String detalle, DeclaracionConflicto.Modo modo, UUID documentoId, Referencia registradoPor,
      Instant declaradoEn, long version) {
  }

  /**miembro del equipo sin declaración registrada*/
  public record DeclaracionPendiente(UUID auditorId, String auditor, RolAuditor rol) {
  }

  public record DeclaracionesAuditoria(List<DeclaracionRespuesta> declaraciones, List<DeclaracionPendiente> pendientes) {
  }

  /**
   *vía DECISOR: decisorId (por defecto, quien registra). Vía COMITE_IMPARCIALIDAD: acta obligatoria.
   *Sin normas, la decisión aplica a todas las del expediente. La versión es la del expediente
   */
  public record SolicitudDecision(
      @NotNull TipoDecision tipo,
      @NotNull Clasificacion clasificacion,
      @NotNull Via via,
      UUID decisorId,
      UUID actaDocumentoId,
      @Size(max = 200) String expertoImparcialidad,
      @Size(max = 4000) String recomendacionExperto,
      UUID checklistDocumentoId,
      @NotNull LocalDate fechaDecision,
      @NotBlank @Size(max = 4000) String fundamento,
      Set<String> normas,
      @Size(max = 4000) String nuevoAlcance,
      @NotNull Long version) {
  }

  public record DecisionRespuesta(UUID id, TipoDecision tipo, Clasificacion clasificacion, Via via, Referencia decisor,
      UUID actaDocumentoId, String expertoImparcialidad, String recomendacionExperto, UUID checklistDocumentoId,
      LocalDate fechaDecision, String fundamento, String nuevoAlcance, Set<String> normas, Referencia registradoPor,
      Instant creadoEn) {
  }

  /**resultado de registrar la decisión: el expediente actualizado y los certificados que generó o cambió*/
  public record DecisionRegistrada(DecisionRespuesta decision, EstadoExpediente estadoExpediente,
      long versionExpediente, List<CertificadoRespuesta> certificados) {
  }

  /**usuario con permiso de decidir y si está habilitado para este expediente (control de imparcialidad)*/
  public record DecisorPosible(UUID id, String nombre, boolean habilitado, String motivo) {
  }

  /**corrección de un borrador: recalcula el código*/
  public record SolicitudCorregirCertificado(@Min(1) @Max(9) Integer revisionAlcance, @Min(1) @Max(9) Integer ciclo,
      @Size(max = 4000) String alcance, @NotNull Long version) {
  }

  /**confirmación del checklist de emisión (DDC-MAN-001)*/
  public record SolicitudRevision(@NotNull Boolean checklistConforme, @NotNull Long version) {
  }

  public record SolicitudFirma(@NotNull Certificado.TipoFirma tipo, @NotNull Long version) {
  }

  /**certificado enviado por India (IQC Global)*/
  public record SolicitudCertificadoExterno(
      @NotBlank String norma,
      @NotBlank @Size(max = 60) String codigo,
      @NotNull LocalDate fechaEmision,
      LocalDate fechaOtorgamiento,
      LocalDate fechaExpiracion,
      UUID traduccionDocumentoId) {
  }

  public record SolicitudTraduccion(@NotNull UUID documentoId, @NotNull Long version) {
  }

  public record SolicitudCondicionesEntrega(@NotNull Boolean pagosConformes, @NotNull Boolean encuestaAplicada,
      @NotNull Long version) {
  }

  public record SolicitudEntrega(@NotNull Certificado.MedioEntrega medio, @NotNull Long version) {
  }

  public record CondicionesEntrega(boolean pagosConformes, boolean encuestaAplicada, Referencia actualizadoPor,
      Instant actualizadoEn, boolean puedeEntregarse, List<String> pendientes) {
  }

  public record CertificadoRespuesta(
      UUID id,
      String codigo,
      String norma,
      String designacion,
      String esquema,
      boolean emitidoPorIndia,
      int revisionAlcance,
      int ciclo,
      String alcance,
      Certificado.Estado estado,
      UUID decisionId,
      UUID reemplazaId,
      Referencia revisadoPor,
      Referencia firmaGerencia,
      Referencia firmaJefeTecnico,
      /**firmas que faltan según la política de la organización*/
      List<Certificado.TipoFirma> firmasPendientes,
      LocalDate fechaOtorgamiento,
      LocalDate fechaEmision,
      LocalDate fechaExpiracion,
      String versionDocumento,
      UUID tokenVerificacion,
      UUID traduccionDocumentoId,
      Instant entregadoEn,
      Certificado.MedioEntrega medioEntrega,
      long version) {
  }
}
