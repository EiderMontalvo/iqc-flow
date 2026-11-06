package pe.com.iqc.flow.auditorias.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *expediente de certificación: un servicio (CI, RC o AMP) para un cliente, bajo un esquema de acreditación,
 *con una o más normas auditadas juntas. Equivale a una fila del cuadro de control de operaciones
 */
@Entity
@Table(name = "expedientes")
public class Expediente extends EntidadBase {
  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Generated(event = EventType.INSERT)
  @Column(insertable = false, updatable = false, length = 20)
  private String codigo;

  @Column(name = "cliente_id", nullable = false, updatable = false)
  private UUID clienteId;

  @Column(name = "esquema_codigo", nullable = false, updatable = false, length = 20)
  private String esquemaCodigo;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_servicio", nullable = false, updatable = false, length = 30)
  private TipoServicio tipoServicio;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EstadoExpediente estado;

  @Column(name = "estado_cambiado_en", nullable = false)
  private Instant estadoCambiadoEn;

  @Column(length = 4000)
  private String alcance;

  @Column(name = "aliado_id")
  private UUID aliadoId;

  @Column(name = "comision_porcentaje", precision = 5, scale = 2)
  private BigDecimal comisionPorcentaje;

  @Column(name = "numero_contrato", length = 40)
  private String numeroContrato;

  @Column(name = "codigo_externo", length = 60)
  private String codigoExterno;

  @Column(name = "responsable_usuario_id", nullable = false)
  private UUID responsableUsuarioId;

  @Column(name = "fecha_certificacion")
  private LocalDate fechaCertificacion;

  @Column(name = "fecha_expiracion")
  private LocalDate fechaExpiracion;

  @Column(length = 4000)
  private String notas;

  @Column(name = "creado_por", updatable = false)
  private UUID creadoPor;

  @Column(name = "pagos_conformes", nullable = false)
  private boolean pagosConformes;

  @Column(name = "encuesta_aplicada", nullable = false)
  private boolean encuestaAplicada;

  @Column(name = "condiciones_por")
  private UUID condicionesPor;

  @Column(name = "condiciones_en")
  private Instant condicionesEn;

  @Column(name = "fuente_legado", updatable = false, length = 40)
  private String fuenteLegado;

  @Column(name = "id_legado", updatable = false, length = 120)
  private String idLegado;

  @ElementCollection
  @CollectionTable(name = "expedientes_normas", joinColumns = @JoinColumn(name = "expediente_id"))
  private Set<NormaExpediente> normas = new HashSet<>();

  @ElementCollection
  @CollectionTable(name = "expedientes_sedes", joinColumns = @JoinColumn(name = "expediente_id"))
  @Column(name = "direccion_id")
  private Set<UUID> sedes = new HashSet<>();

  protected Expediente() {
  }

  public static Expediente nuevo(UUID organizacionId, UUID clienteId, String esquemaCodigo, TipoServicio tipoServicio,
      EstadoExpediente estadoInicial, UUID responsable, UUID creadoPor, Instant ahora) {
    Expediente expediente = new Expediente();
    expediente.organizacionId = organizacionId;
    expediente.clienteId = clienteId;
    expediente.esquemaCodigo = esquemaCodigo;
    expediente.tipoServicio = tipoServicio;
    expediente.estado = estadoInicial;
    expediente.estadoCambiadoEn = ahora;
    expediente.responsableUsuarioId = responsable;
    expediente.creadoPor = creadoPor;
    return expediente;
  }

  /**
   *expediente histórico importado de la plataforma anterior: ya entregado, con su ciclo tal como se registró
   *allá (sin recalcularlo) y con las normas de sus certificados
   */
  public static Expediente historico(UUID organizacionId, UUID clienteId, String esquemaCodigo, TipoServicio tipoServicio,
      Collection<NormaExpediente> normas, String alcance, LocalDate fechaCertificacion, LocalDate fechaExpiracion,
      UUID responsable, String fuenteLegado, String idLegado, Instant ahora) {
    Expediente e = nuevo(organizacionId, clienteId, esquemaCodigo, tipoServicio, EstadoExpediente.ENTREGADO, responsable,
        null, ahora);
    e.normas.addAll(normas);
    e.alcance = alcance;
    e.fechaCertificacion = fechaCertificacion;
    e.fechaExpiracion = fechaExpiracion;
    e.fuenteLegado = fuenteLegado;
    e.idLegado = idLegado;
    e.notas = "Importado de la plataforma anterior de certificaciones.";
    return e;
  }

  public void definirNormas(Collection<NormaExpediente> nuevas) {
    if (fechaCertificacion != null) {
      throw new ReglaNegocioException("EXPEDIENTE_CERTIFICADO",
          "Las normas no pueden cambiarse después de registrar la certificación; usa un expediente de ampliación.");
    }
    if (nuevas.isEmpty()) throw new ReglaNegocioException("SIN_NORMAS", "El expediente debe incluir al menos una norma.");
    normas.clear();
    normas.addAll(nuevas);
  }

  public void definirSedes(Collection<UUID> nuevas) {
    sedes.clear();
    sedes.addAll(nuevas);
  }

  public void actualizarDatos(String alcance, UUID aliadoId, BigDecimal comisionPorcentaje, String numeroContrato,
      String codigoExterno, UUID responsable, String notas) {
    exigirAbierto();
    this.alcance = alcance;
    this.aliadoId = aliadoId;
    this.comisionPorcentaje = comisionPorcentaje;
    this.numeroContrato = numeroContrato;
    this.codigoExterno = codigoExterno;
    this.responsableUsuarioId = responsable;
    this.notas = notas;
  }

  /**transición manual (POST /transicion): no permite los estados que asigna el sistema*/
  public void cambiarEstado(EstadoExpediente nuevo, boolean decideIndia, Instant ahora) {
    FlujoExpediente.validarManual(estado, nuevo, decideIndia, tipoServicio);
    this.estado = nuevo;
    this.estadoCambiadoEn = ahora;
  }

  /**transición que hace el sistema como efecto de una decisión, una firma o la entrega*/
  public void avanzarPorSistema(EstadoExpediente nuevo, boolean decideIndia, Instant ahora) {
    FlujoExpediente.validar(estado, nuevo, decideIndia, tipoServicio);
    this.estado = nuevo;
    this.estadoCambiadoEn = ahora;
  }

  /**conformidad de pagos y encuesta de satisfacción: ambas son requisito para entregar (DDC-PRO-003)*/
  public void definirCondicionesEntrega(boolean pagosConformes, boolean encuestaAplicada, UUID actor, Instant ahora) {
    exigirAbierto();
    this.pagosConformes = pagosConformes;
    this.encuestaAplicada = encuestaAplicada;
    this.condicionesPor = actor;
    this.condicionesEn = ahora;
  }

  public boolean puedeEntregarse() { return pagosConformes && encuestaAplicada; }

  /**una decisión de reducción cambia el alcance certificado*/
  public void reducirAlcance(String nuevoAlcance) { this.alcance = nuevoAlcance; }

  public void registrarCertificacion(CicloCertificacion ciclo) {
    if (!FlujoExpediente.CON_CERTIFICADO.contains(estado)) {
      throw new ReglaNegocioException("SIN_CERTIFICADO",
          "Solo se registra la fecha de certificación cuando el certificado fue emitido o recibido.");
    }
    if (fechaCertificacion != null) {
      throw new ReglaNegocioException("CERTIFICACION_YA_REGISTRADA", "La fecha de certificación ya fue registrada.");
    }
    this.fechaCertificacion = ciclo.fechaCertificacion();
    this.fechaExpiracion = ciclo.expiracion();
  }

  public boolean estaCerrado() { return estado == EstadoExpediente.CANCELADO || estado == EstadoExpediente.RECHAZADO; }

  public void exigirAbierto() {
    if (estaCerrado()) throw new ReglaNegocioException("EXPEDIENTE_CERRADO", "El expediente está " + (estado == EstadoExpediente.RECHAZADO ? "rechazado." : "cancelado."));
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getCodigo() { return codigo; }
  public UUID getClienteId() { return clienteId; }
  public String getEsquemaCodigo() { return esquemaCodigo; }
  public TipoServicio getTipoServicio() { return tipoServicio; }
  public EstadoExpediente getEstado() { return estado; }
  public Instant getEstadoCambiadoEn() { return estadoCambiadoEn; }
  public String getAlcance() { return alcance; }
  public UUID getAliadoId() { return aliadoId; }
  public BigDecimal getComisionPorcentaje() { return comisionPorcentaje; }
  public String getNumeroContrato() { return numeroContrato; }
  public String getCodigoExterno() { return codigoExterno; }
  public UUID getResponsableUsuarioId() { return responsableUsuarioId; }
  public LocalDate getFechaCertificacion() { return fechaCertificacion; }
  public LocalDate getFechaExpiracion() { return fechaExpiracion; }
  public String getNotas() { return notas; }
  public UUID getCreadoPor() { return creadoPor; }
  public boolean isPagosConformes() { return pagosConformes; }
  public boolean isEncuestaAplicada() { return encuestaAplicada; }
  public UUID getCondicionesPor() { return condicionesPor; }
  public Instant getCondicionesEn() { return condicionesEn; }
  public String getFuenteLegado() { return fuenteLegado; }
  public String getIdLegado() { return idLegado; }
  public Set<NormaExpediente> getNormas() { return Set.copyOf(normas); }
  public Set<UUID> getSedes() { return Set.copyOf(sedes); }
}
