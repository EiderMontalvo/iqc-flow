package pe.com.iqc.flow.auditorias.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.auditores.RolAuditor;
import pe.com.iqc.flow.auditorias.domain.EstadoAuditoria;
import pe.com.iqc.flow.auditorias.domain.EstadoExpediente;
import pe.com.iqc.flow.auditorias.domain.EstadoPaquete;
import pe.com.iqc.flow.auditorias.domain.Modalidad;
import pe.com.iqc.flow.auditorias.domain.TipoAuditoria;
import pe.com.iqc.flow.auditorias.domain.TipoServicio;

/**solicitudes y respuestas de la API de expedientes y auditorías*/
public final class DtosExpedientes {
  private DtosExpedientes() {
  }

  /**norma del catálogo (ISO_9001, ISO_45001…) con su edición ("2015")*/
  public record NormaSolicitada(@NotBlank String norma, @NotBlank String edicion) {
  }

  /**sin responsable, el expediente queda a cargo de quien lo crea*/
  public record SolicitudCrearExpediente(
      @NotNull UUID clienteId,
      @NotBlank String esquema,
      @NotNull TipoServicio tipoServicio,
      @NotEmpty List<@Valid NormaSolicitada> normas,
      @Size(max = 4000) String alcance,
      Set<UUID> sedes,
      UUID aliadoId,
      @DecimalMin("0") @DecimalMax("100") BigDecimal comisionPorcentaje,
      @Size(max = 40) String numeroContrato,
      @Size(max = 60) String codigoExterno,
      UUID responsableId,
      @Size(max = 4000) String notas) {
  }

  /**los campos nulos no cambian; un texto vacío ("") lo borra. La versión es obligatoria*/
  public record SolicitudActualizarExpediente(
      @Size(max = 4000) String alcance,
      List<@Valid NormaSolicitada> normas,
      Set<UUID> sedes,
      UUID aliadoId,
      @DecimalMin("0") @DecimalMax("100") BigDecimal comisionPorcentaje,
      @Size(max = 40) String numeroContrato,
      @Size(max = 60) String codigoExterno,
      UUID responsableId,
      @Size(max = 4000) String notas,
      @NotNull Long version) {
  }

  /**para CANCELADO el motivo es obligatorio*/
  public record SolicitudTransicion(@NotNull EstadoExpediente estado, @Size(max = 2000) String motivo,
      @NotNull Long version) {
  }

  /**fecha efectiva de otorgamiento: genera las vigilancias del ciclo y sus tareas*/
  public record SolicitudCertificacion(@NotNull LocalDate fechaCertificacion, @NotNull Long version) {
  }

  public record SolicitudAuditoria(
      @NotNull TipoAuditoria tipo,
      LocalDate fechaLimite,
      LocalDate fechaInicioPlan,
      LocalDate fechaFinPlan,
      @DecimalMin("0.5") BigDecimal diasAuditor,
      Modalidad modalidad,
      @Size(max = 2000) String notas) {
  }

  /**
   *los campos de planificación nulos no cambian. Con fechaRealInicio la auditoría pasa a EJECUTADA;
   *con cancelar=true se cancela (motivo obligatorio)
   */
  public record SolicitudActualizarAuditoria(
      LocalDate fechaLimite,
      LocalDate fechaInicioPlan,
      LocalDate fechaFinPlan,
      @DecimalMin("0.5") BigDecimal diasAuditor,
      Modalidad modalidad,
      @Size(max = 2000) String notas,
      LocalDate fechaRealInicio,
      LocalDate fechaRealFin,
      EstadoPaquete estadoPaquete,
      Boolean cancelar,
      @Size(max = 2000) String motivoCancelacion,
      @NotNull Long version) {
  }

  public record MiembroSolicitado(@NotNull UUID auditorId, @NotNull RolAuditor rol) {
  }

  /**reemplaza todo el equipo de la auditoría*/
  public record SolicitudEquipo(@NotNull List<@Valid MiembroSolicitado> miembros, @NotNull Long version) {
  }

  public record Referencia(UUID id, String nombre) {
  }

  public record ClienteRef(UUID id, String codigo, String razonSocial) {
  }

  public record NormaRespuesta(String norma, String nombreCorto, String edicion, String designacion,
      String codigoSistema) {
  }

  public record SedeRespuesta(UUID direccionId, String tipo, String nombre, String direccion) {
  }

  public record AliadoRef(UUID id, String codigo, String nombre) {
  }

  public record MiembroRespuesta(UUID auditorId, String nombre, RolAuditor rol) {
  }

  public record AuditoriaRespuesta(
      UUID id,
      TipoAuditoria tipo,
      EstadoAuditoria estado,
      LocalDate fechaLimite,
      LocalDate fechaInicioPlan,
      LocalDate fechaFinPlan,
      LocalDate fechaRealInicio,
      LocalDate fechaRealFin,
      BigDecimal diasAuditor,
      Modalidad modalidad,
      EstadoPaquete estadoPaquete,
      String notas,
      List<MiembroRespuesta> equipo,
      /**avisos que no bloquean: sin líder, auditor sin competencia en una norma, fecha posterior al límite*/
      List<String> advertencias,
      long version) {
  }

  public record HistorialRespuesta(EstadoExpediente desde, EstadoExpediente hacia, String motivo, Referencia actor,
      Instant ocurridoEn) {
  }

  public record ExpedienteResumen(
      UUID id,
      String codigo,
      ClienteRef cliente,
      String esquema,
      TipoServicio tipoServicio,
      EstadoExpediente estado,
      List<String> normas,
      Referencia responsable,
      LocalDate fechaCertificacion,
      LocalDate fechaExpiracion,
      Instant estadoCambiadoEn,
      long version) {
  }

  public record ExpedienteDetalle(
      UUID id,
      String codigo,
      ClienteRef cliente,
      String esquema,
      String esquemaNombre,
      /**true en IQC Global: decide y emite India*/
      boolean decideIndia,
      TipoServicio tipoServicio,
      EstadoExpediente estado,
      Instant estadoCambiadoEn,
      List<NormaRespuesta> normas,
      List<SedeRespuesta> sedes,
      String alcance,
      AliadoRef aliado,
      BigDecimal comisionPorcentaje,
      String numeroContrato,
      String codigoExterno,
      Referencia responsable,
      LocalDate fechaCertificacion,
      LocalDate fechaExpiracion,
      String notas,
      List<AuditoriaRespuesta> auditorias,
      List<HistorialRespuesta> historial,
      /**estados a los que se puede pasar desde el actual*/
      Set<EstadoExpediente> transicionesPermitidas,
      Instant creadoEn,
      long version) {
  }

  public record VigilanciaPendiente(UUID auditoriaId, TipoAuditoria tipo, EstadoAuditoria estado,
      LocalDate fechaLimite, long diasRestantes, UUID expedienteId, String expedienteCodigo, ClienteRef cliente,
      String esquema) {
  }

  public record AuditoriaSinEquipo(UUID auditoriaId, TipoAuditoria tipo, EstadoAuditoria estado,
      LocalDate fechaLimite, LocalDate fechaInicioPlan, UUID expedienteId, String expedienteCodigo,
      ClienteRef cliente) {
  }

  public record ResumenExpedientes(Map<EstadoExpediente, Long> porEstado, long vigilanciasVencidas,
      long vigilanciasProximas30Dias, long auditoriasSinEquipo) {
  }
}
