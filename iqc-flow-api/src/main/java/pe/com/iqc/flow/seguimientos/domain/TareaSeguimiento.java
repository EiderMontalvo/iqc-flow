package pe.com.iqc.flow.seguimientos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *tarea de seguimiento con responsable y fecha límite. Puede referirse a un cliente u otro registro
 *(sujeto). Las fechas se interpretan en la zona horaria de la organización (America/Lima)
 */
@Entity
@Table(name = "tareas_seguimiento")
public class TareaSeguimiento extends EntidadBase {
  public static final Set<EstadoTarea> ABIERTAS = EnumSet.of(EstadoTarea.PENDIENTE, EstadoTarea.EN_CURSO,
      EstadoTarea.EN_ESPERA);

  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Column(nullable = false, length = 200)
  private String titulo;

  @Column(length = 2000)
  private String descripcion;

  @Column(name = "tipo_codigo", nullable = false, length = 40)
  private String tipoCodigo;

  @Column(name = "sujeto_tipo", length = 40, updatable = false)
  private String sujetoTipo;

  @Column(name = "sujeto_id", updatable = false)
  private UUID sujetoId;

  @Column(name = "cliente_id", updatable = false)
  private UUID clienteId;

  @Column(name = "responsable_usuario_id", nullable = false)
  private UUID responsableUsuarioId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PrioridadTarea prioridad;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private EstadoTarea estado;

  @Column(name = "fecha_limite", nullable = false)
  private LocalDate fechaLimite;

  @Column(name = "hora_limite")
  private LocalTime horaLimite;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private ResultadoTarea resultado;

  @Column(name = "notas_resultado", length = 2000)
  private String notasResultado;

  @Column(name = "cerrada_en")
  private Instant cerradaEn;

  @Column(name = "cerrada_por")
  private UUID cerradaPor;

  @Column(nullable = false)
  private int reprogramaciones;

  @Column(name = "aviso_vencimiento_en")
  private Instant avisoVencimientoEn;

  @Column(name = "nivel_escalamiento", nullable = false)
  private int nivelEscalamiento;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15, updatable = false)
  private OrigenTarea origen;

  @Column(name = "clave_deduplicacion", length = 200, updatable = false)
  private String claveDeduplicacion;

  @Column(name = "creado_por", updatable = false)
  private UUID creadoPor;

  protected TareaSeguimiento() {
  }

  public static TareaSeguimiento nueva(UUID organizacionId, String titulo, String descripcion, String tipoCodigo,
      String sujetoTipo, UUID sujetoId, UUID clienteId, UUID responsable, PrioridadTarea prioridad,
      LocalDate fechaLimite, LocalTime horaLimite, OrigenTarea origen, String claveDeduplicacion, UUID creadoPor) {
    TareaSeguimiento tarea = new TareaSeguimiento();
    tarea.organizacionId = organizacionId;
    tarea.titulo = titulo.trim();
    tarea.descripcion = descripcion;
    tarea.tipoCodigo = tipoCodigo;
    tarea.sujetoTipo = sujetoTipo;
    tarea.sujetoId = sujetoId;
    tarea.clienteId = clienteId;
    tarea.responsableUsuarioId = responsable;
    tarea.prioridad = prioridad;
    tarea.estado = EstadoTarea.PENDIENTE;
    tarea.fechaLimite = fechaLimite;
    tarea.horaLimite = horaLimite;
    tarea.origen = origen;
    tarea.claveDeduplicacion = claveDeduplicacion;
    tarea.creadoPor = creadoPor;
    return tarea;
  }

  public boolean estaAbierta() { return ABIERTAS.contains(estado); }

  /**vencida si la fecha ya pasó o, siendo hoy, ya pasó la hora indicada*/
  public boolean estaVencida(LocalDate hoy, LocalTime ahora) {
    if (!estaAbierta()) return false;
    return fechaLimite.isBefore(hoy) || (fechaLimite.equals(hoy) && horaLimite != null && horaLimite.isBefore(ahora));
  }

  public void editar(String titulo, String descripcion, PrioridadTarea prioridad) {
    exigirAbierta();
    this.titulo = titulo.trim();
    this.descripcion = descripcion;
    this.prioridad = prioridad;
  }

  /**solo entre estados abiertos; para cerrar se usa completar o cancelar*/
  public void cambiarEstado(EstadoTarea nuevo) {
    exigirAbierta();
    if (!ABIERTAS.contains(nuevo)) {
      throw new ReglaNegocioException("ESTADO_INVALIDO", "Usa completar o cancelar para cerrar la tarea.");
    }
    this.estado = nuevo;
  }

  public void completar(ResultadoTarea resultado, String notas, Instant ahora, UUID actor) {
    exigirAbierta();
    this.estado = EstadoTarea.COMPLETADA;
    this.resultado = resultado;
    this.notasResultado = notas;
    this.cerradaEn = ahora;
    this.cerradaPor = actor;
  }

  public void cancelar(String motivo, Instant ahora, UUID actor) {
    exigirAbierta();
    this.estado = EstadoTarea.CANCELADA;
    this.notasResultado = motivo;
    this.cerradaEn = ahora;
    this.cerradaPor = actor;
  }

  /**una nueva fecha reinicia el aviso de vencimiento: si vuelve a vencer, se avisa otra vez*/
  public void reprogramar(LocalDate nuevaFecha, LocalTime nuevaHora) {
    exigirAbierta();
    this.fechaLimite = nuevaFecha;
    this.horaLimite = nuevaHora;
    this.reprogramaciones++;
    this.avisoVencimientoEn = null;
  }

  public void reasignar(UUID nuevoResponsable) {
    exigirAbierta();
    this.responsableUsuarioId = nuevoResponsable;
  }

  public void marcarAvisoVencimiento(Instant ahora) { this.avisoVencimientoEn = ahora; }

  private void exigirAbierta() {
    if (!estaAbierta()) {
      throw new ReglaNegocioException("TAREA_CERRADA", "La tarea ya está " + estado.name().toLowerCase() + ".");
    }
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getTitulo() { return titulo; }
  public String getDescripcion() { return descripcion; }
  public String getTipoCodigo() { return tipoCodigo; }
  public String getSujetoTipo() { return sujetoTipo; }
  public UUID getSujetoId() { return sujetoId; }
  public UUID getClienteId() { return clienteId; }
  public UUID getResponsableUsuarioId() { return responsableUsuarioId; }
  public PrioridadTarea getPrioridad() { return prioridad; }
  public EstadoTarea getEstado() { return estado; }
  public LocalDate getFechaLimite() { return fechaLimite; }
  public LocalTime getHoraLimite() { return horaLimite; }
  public ResultadoTarea getResultado() { return resultado; }
  public String getNotasResultado() { return notasResultado; }
  public Instant getCerradaEn() { return cerradaEn; }
  public UUID getCerradaPor() { return cerradaPor; }
  public int getReprogramaciones() { return reprogramaciones; }
  public Instant getAvisoVencimientoEn() { return avisoVencimientoEn; }
  public int getNivelEscalamiento() { return nivelEscalamiento; }
  public OrigenTarea getOrigen() { return origen; }
  public String getClaveDeduplicacion() { return claveDeduplicacion; }
  public UUID getCreadoPor() { return creadoPor; }
}
