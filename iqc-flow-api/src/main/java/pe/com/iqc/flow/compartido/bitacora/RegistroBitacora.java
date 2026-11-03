package pe.com.iqc.flow.compartido.bitacora;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**registro inmutable de la bitácora. La base de datos rechaza UPDATE y DELETE sobre esta tabla*/
@Entity
@Immutable
@Table(name = "bitacora")
public class RegistroBitacora {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "organizacion_id")
  private UUID organizacionId;

  @Column(name = "ocurrido_en", nullable = false)
  private Instant ocurridoEn;

  @Column(name = "actor_usuario_id")
  private UUID actorUsuarioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_actor", nullable = false, length = 20)
  private TipoActor tipoActor;

  @Enumerated(EnumType.STRING)
  @Column(name = "origen", nullable = false, length = 20)
  private OrigenOperacion origen;

  @Column(name = "accion", nullable = false, length = 80)
  private String accion;

  @Column(name = "entidad_tipo", length = 80)
  private String entidadTipo;

  @Column(name = "entidad_id", length = 200)
  private String entidadId;

  @ColumnTransformer(write = "?::jsonb")
  @Column(name = "estado_anterior", columnDefinition = "jsonb")
  private String estadoAnterior;

  @ColumnTransformer(write = "?::jsonb")
  @Column(name = "estado_nuevo", columnDefinition = "jsonb")
  private String estadoNuevo;

  @Enumerated(EnumType.STRING)
  @Column(name = "resultado", nullable = false, length = 20)
  private ResultadoOperacion resultado;

  @Column(name = "motivo", length = 500)
  private String motivo;

  @Column(name = "id_correlacion", length = 64)
  private String idCorrelacion;

  @Column(name = "interaccion_ia_id")
  private UUID interaccionIaId;

  @Column(name = "confirmado_por_usuario_id")
  private UUID confirmadoPorUsuarioId;

  @Column(name = "direccion_ip", length = 45)
  private String direccionIp;

  @Column(name = "agente_usuario", length = 500)
  private String agenteUsuario;

  protected RegistroBitacora() {
  }

  RegistroBitacora(UUID organizacionId, Instant ocurridoEn, UUID actorUsuarioId, TipoActor tipoActor,
      OrigenOperacion origen, String accion, String entidadTipo, String entidadId, String estadoAnterior,
      String estadoNuevo, ResultadoOperacion resultado, String motivo, String idCorrelacion,
      String direccionIp, String agenteUsuario) {
    this.organizacionId = organizacionId;
    this.ocurridoEn = ocurridoEn;
    this.actorUsuarioId = actorUsuarioId;
    this.tipoActor = tipoActor;
    this.origen = origen;
    this.accion = accion;
    this.entidadTipo = entidadTipo;
    this.entidadId = entidadId;
    this.estadoAnterior = estadoAnterior;
    this.estadoNuevo = estadoNuevo;
    this.resultado = resultado;
    this.motivo = motivo;
    this.idCorrelacion = idCorrelacion;
    this.direccionIp = direccionIp;
    this.agenteUsuario = agenteUsuario;
  }

  public UUID getId() { return id; }
  public UUID getOrganizacionId() { return organizacionId; }
  public Instant getOcurridoEn() { return ocurridoEn; }
  public UUID getActorUsuarioId() { return actorUsuarioId; }
  public TipoActor getTipoActor() { return tipoActor; }
  public OrigenOperacion getOrigen() { return origen; }
  public String getAccion() { return accion; }
  public String getEntidadTipo() { return entidadTipo; }
  public String getEntidadId() { return entidadId; }
  public String getEstadoAnterior() { return estadoAnterior; }
  public String getEstadoNuevo() { return estadoNuevo; }
  public ResultadoOperacion getResultado() { return resultado; }
  public String getMotivo() { return motivo; }
  public String getIdCorrelacion() { return idCorrelacion; }
  public UUID getInteraccionIaId() { return interaccionIaId; }
  public UUID getConfirmadoPorUsuarioId() { return confirmadoPorUsuarioId; }
  public String getDireccionIp() { return direccionIp; }
  public String getAgenteUsuario() { return agenteUsuario; }
}
