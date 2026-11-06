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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**interacción registrada con un cliente (llamada, correo, reunión…). Forma su historial de contacto*/
@Entity
@Immutable
@Table(name = "actividades")
public class Actividad {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "organizacion_id", nullable = false)
  private UUID organizacionId;

  @Column(name = "cliente_id", nullable = false)
  private UUID clienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private TipoActividad tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private DireccionActividad direccion;

  @Column(name = "ocurrida_en", nullable = false)
  private Instant ocurridaEn;

  @Column(nullable = false, length = 2000)
  private String resumen;

  @Column(length = 200)
  private String resultado;

  @Column(name = "contacto_id")
  private UUID contactoId;

  @Column(name = "tarea_id")
  private UUID tareaId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private OrigenTarea origen;

  @Column(name = "creado_por")
  private UUID creadoPor;

  @CreationTimestamp
  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected Actividad() {
  }

  public Actividad(UUID organizacionId, UUID clienteId, TipoActividad tipo, DireccionActividad direccion,
      Instant ocurridaEn, String resumen, String resultado, UUID contactoId, UUID tareaId, OrigenTarea origen,
      UUID creadoPor) {
    this.organizacionId = organizacionId;
    this.clienteId = clienteId;
    this.tipo = tipo;
    this.direccion = direccion;
    this.ocurridaEn = ocurridaEn;
    this.resumen = resumen.trim();
    this.resultado = resultado;
    this.contactoId = contactoId;
    this.tareaId = tareaId;
    this.origen = origen;
    this.creadoPor = creadoPor;
  }

  public UUID getId() { return id; }
  public UUID getOrganizacionId() { return organizacionId; }
  public UUID getClienteId() { return clienteId; }
  public TipoActividad getTipo() { return tipo; }
  public DireccionActividad getDireccion() { return direccion; }
  public Instant getOcurridaEn() { return ocurridaEn; }
  public String getResumen() { return resumen; }
  public String getResultado() { return resultado; }
  public UUID getContactoId() { return contactoId; }
  public UUID getTareaId() { return tareaId; }
  public OrigenTarea getOrigen() { return origen; }
  public UUID getCreadoPor() { return creadoPor; }
  public Instant getCreadoEn() { return creadoEn; }
}
