package pe.com.iqc.flow.seguimientos.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import pe.com.iqc.flow.seguimientos.domain.DireccionActividad;
import pe.com.iqc.flow.seguimientos.domain.EstadoTarea;
import pe.com.iqc.flow.seguimientos.domain.EventoHistorial;
import pe.com.iqc.flow.seguimientos.domain.OrigenTarea;
import pe.com.iqc.flow.seguimientos.domain.PrioridadTarea;
import pe.com.iqc.flow.seguimientos.domain.ResultadoTarea;
import pe.com.iqc.flow.seguimientos.domain.TipoActividad;

/**solicitudes y respuestas de la API de seguimientos*/
public final class DtosSeguimientos {
  private DtosSeguimientos() {
  }

  /**vistas del Centro de seguimientos*/
  public enum VistaTareas {
    /**pendientes, en curso y en espera*/
    ABIERTAS,
    /**abiertas que vencen hoy*/
    HOY,
    /**abiertas ya vencidas*/
    ATRASADAS,
    /**abiertas que vencen de mañana a N días*/
    PROXIMAS,
    /**esperando respuesta de terceros*/
    EN_ESPERA,
    /**completadas o canceladas*/
    CERRADAS,
    TODAS
  }

  /**sin responsable se asigna a quien la crea; asignarla a otra persona requiere seguimientos:asignar*/
  public record SolicitudCrearTarea(
      @NotBlank @Size(max = 200) String titulo,
      @Size(max = 2000) String descripcion,
      @NotBlank @Size(max = 40) String tipo,
      UUID clienteId,
      UUID responsableId,
      PrioridadTarea prioridad,
      @NotNull LocalDate fechaLimite,
      LocalTime horaLimite) {
  }

  /**los campos nulos no cambian; una descripción vacía ("") la borra. Estados permitidos: PENDIENTE, EN_CURSO, EN_ESPERA*/
  public record SolicitudEditarTarea(
      @Size(min = 1, max = 200) String titulo,
      @Size(max = 2000) String descripcion,
      PrioridadTarea prioridad,
      EstadoTarea estado,
      @NotNull Long version) {
  }

  /**al completar se puede crear la siguiente tarea del mismo cliente en el mismo paso*/
  public record SolicitudCompletarTarea(
      @NotNull ResultadoTarea resultado,
      @Size(max = 2000) String notas,
      @NotNull Long version,
      @Valid SiguienteTarea siguiente) {
  }

  public record SiguienteTarea(
      @NotBlank @Size(max = 200) String titulo,
      @NotBlank @Size(max = 40) String tipo,
      @NotNull LocalDate fechaLimite,
      LocalTime horaLimite,
      PrioridadTarea prioridad,
      @Size(max = 2000) String descripcion) {
  }

  public record SolicitudReprogramarTarea(
      @NotNull LocalDate fechaLimite,
      LocalTime horaLimite,
      @NotBlank @Size(max = 1000) String motivo,
      @NotNull Long version) {
  }

  public record SolicitudReasignarTarea(@NotNull UUID responsableId, @NotNull Long version) {
  }

  public record SolicitudCancelarTarea(@NotBlank @Size(max = 1000) String motivo, @NotNull Long version) {
  }

  public record SolicitudActividad(
      @NotNull UUID clienteId,
      @NotNull TipoActividad tipo,
      DireccionActividad direccion,
      /**si se omite, se usa la fecha y hora actuales*/
      Instant ocurridaEn,
      @NotBlank @Size(max = 2000) String resumen,
      @Size(max = 200) String resultado,
      UUID contactoId,
      UUID tareaId) {
  }

  public record Referencia(UUID id, String nombre) {
  }

  public record ClienteDeTarea(UUID id, String codigo, String razonSocial) {
  }

  public record TipoTareaRespuesta(String codigo, String nombre, PrioridadTarea prioridadPorDefecto) {
  }

  public record TareaResumen(
      UUID id,
      String titulo,
      String tipo,
      String tipoNombre,
      PrioridadTarea prioridad,
      EstadoTarea estado,
      LocalDate fechaLimite,
      LocalTime horaLimite,
      boolean vencida,
      ClienteDeTarea cliente,
      Referencia responsable,
      int reprogramaciones,
      OrigenTarea origen,
      long version) {
  }

  public record TareaDetalle(
      UUID id,
      String titulo,
      String descripcion,
      String tipo,
      String tipoNombre,
      PrioridadTarea prioridad,
      EstadoTarea estado,
      LocalDate fechaLimite,
      LocalTime horaLimite,
      boolean vencida,
      ClienteDeTarea cliente,
      Referencia responsable,
      ResultadoTarea resultado,
      String notasResultado,
      Instant cerradaEn,
      Referencia cerradaPor,
      int reprogramaciones,
      OrigenTarea origen,
      Referencia creadaPor,
      Instant creadoEn,
      List<EventoRespuesta> historial,
      long version,
      /**si el usuario actual puede modificar la tarea*/
      boolean puedeGestionar) {
  }

  public record EventoRespuesta(EventoHistorial evento, String desde, String hacia, String motivo, Referencia actor,
      Instant ocurridoEn) {
  }

  /**contadores de las tareas propias para el panel principal*/
  public record ResumenCentro(long hoy, long atrasadas, long proximas, long enEspera) {
  }

  public record ClienteSinMovimiento(
      UUID clienteId,
      String codigo,
      String razonSocial,
      Referencia responsable,
      Instant ultimaActividadEn,
      long diasSinMovimiento) {
  }

  public record ActividadRespuesta(
      UUID id,
      UUID clienteId,
      TipoActividad tipo,
      DireccionActividad direccion,
      Instant ocurridaEn,
      String resumen,
      String resultado,
      UUID contactoId,
      UUID tareaId,
      OrigenTarea origen,
      Referencia registradaPor,
      Instant creadoEn) {
  }
}
