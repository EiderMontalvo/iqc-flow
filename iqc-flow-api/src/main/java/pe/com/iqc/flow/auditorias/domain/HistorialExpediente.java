package pe.com.iqc.flow.auditorias.domain;

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

/**cambio de estado del expediente, visible en su ficha*/
@Entity
@Immutable
@Table(name = "historial_expedientes")
public class HistorialExpediente {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "expediente_id", nullable = false)
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado_desde", length = 30)
  private EstadoExpediente estadoDesde;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado_hacia", nullable = false, length = 30)
  private EstadoExpediente estadoHacia;

  @Column(length = 2000)
  private String motivo;

  @Column(name = "actor_usuario_id")
  private UUID actorUsuarioId;

  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  protected HistorialExpediente() {
  }

  public HistorialExpediente(UUID expedienteId, EstadoExpediente desde, EstadoExpediente hacia, String motivo,
      UUID actorUsuarioId, Instant ocurridoEn) {
    this.expedienteId = expedienteId;
    this.estadoDesde = desde;
    this.estadoHacia = hacia;
    this.motivo = motivo;
    this.actorUsuarioId = actorUsuarioId;
    this.ocurridoEn = ocurridoEn;
  }

  public UUID getExpedienteId() { return expedienteId; }
  public EstadoExpediente getEstadoDesde() { return estadoDesde; }
  public EstadoExpediente getEstadoHacia() { return estadoHacia; }
  public String getMotivo() { return motivo; }
  public UUID getActorUsuarioId() { return actorUsuarioId; }
  public Instant getOcurridoEn() { return ocurridoEn; }
}
