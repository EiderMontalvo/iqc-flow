package pe.com.iqc.flow.seguimientos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**cambio en una tarea, visible para quienes ven la tarea (la bitácora completa requiere bitacora:leer)*/
@Entity
@Immutable
@Table(name = "historial_tareas")
public class HistorialTarea {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "tarea_id", nullable = false)
  private UUID tareaId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventoHistorial evento;

  @Column(length = 300)
  private String desde;

  @Column(length = 300)
  private String hacia;

  @Column(length = 2000)
  private String motivo;

  @Column(name = "actor_usuario_id")
  private UUID actorUsuarioId;

  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  protected HistorialTarea() {
  }

  public HistorialTarea(UUID tareaId, EventoHistorial evento, String desde, String hacia, String motivo,
      UUID actorUsuarioId, Instant ocurridoEn) {
    this.tareaId = tareaId;
    this.evento = evento;
    this.desde = desde;
    this.hacia = hacia;
    this.motivo = motivo;
    this.actorUsuarioId = actorUsuarioId;
    this.ocurridoEn = ocurridoEn;
  }

  public UUID getId() { return id; }
  public UUID getTareaId() { return tareaId; }
  public EventoHistorial getEvento() { return evento; }
  public String getDesde() { return desde; }
  public String getHacia() { return hacia; }
  public String getMotivo() { return motivo; }
  public UUID getActorUsuarioId() { return actorUsuarioId; }
  public Instant getOcurridoEn() { return ocurridoEn; }
}
