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
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.auditores.RolAuditor;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**auditoría de un expediente (Etapa 1, Etapa 2, vigilancias, recertificación, extraordinaria) con su equipo*/
@Entity
@Table(name = "auditorias")
public class Auditoria extends EntidadBase {
  @Column(name = "expediente_id", nullable = false, updatable = false)
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private TipoAuditoria tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private EstadoAuditoria estado;

  @Column(name = "fecha_limite")
  private LocalDate fechaLimite;

  @Column(name = "fecha_inicio_plan")
  private LocalDate fechaInicioPlan;

  @Column(name = "fecha_fin_plan")
  private LocalDate fechaFinPlan;

  @Column(name = "fecha_real_inicio")
  private LocalDate fechaRealInicio;

  @Column(name = "fecha_real_fin")
  private LocalDate fechaRealFin;

  @Column(name = "dias_auditor", precision = 4, scale = 1)
  private BigDecimal diasAuditor;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private Modalidad modalidad;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado_paquete", length = 25)
  private EstadoPaquete estadoPaquete;

  @Column(length = 2000)
  private String notas;

  @ElementCollection
  @CollectionTable(name = "equipo_auditoria", joinColumns = @JoinColumn(name = "auditoria_id"))
  private Set<MiembroEquipo> equipo = new HashSet<>();

  protected Auditoria() {
  }

  public Auditoria(UUID expedienteId, TipoAuditoria tipo, LocalDate fechaLimite) {
    this.expedienteId = expedienteId;
    this.tipo = tipo;
    this.estado = EstadoAuditoria.POR_PROGRAMAR;
    this.fechaLimite = fechaLimite;
  }

  public void planificar(LocalDate fechaLimite, LocalDate inicio, LocalDate fin, BigDecimal diasAuditor,
      Modalidad modalidad, String notas) {
    exigirAbierta();
    if (inicio != null && fin != null && fin.isBefore(inicio)) {
      throw new ReglaNegocioException("FECHAS_INVALIDAS", "La fecha de fin no puede ser anterior a la de inicio.");
    }
    this.fechaLimite = fechaLimite;
    this.fechaInicioPlan = inicio;
    this.fechaFinPlan = fin;
    this.diasAuditor = diasAuditor;
    this.modalidad = modalidad;
    this.notas = notas;
    if (estado == EstadoAuditoria.POR_PROGRAMAR && inicio != null) estado = EstadoAuditoria.PROGRAMADA;
  }

  public void registrarEjecucion(LocalDate inicioReal, LocalDate finReal) {
    exigirAbierta();
    if (inicioReal == null) {
      throw new ReglaNegocioException("FECHA_REAL_REQUERIDA", "Indica la fecha real de inicio de la auditoría.");
    }
    if (finReal != null && finReal.isBefore(inicioReal)) {
      throw new ReglaNegocioException("FECHAS_INVALIDAS", "La fecha real de fin no puede ser anterior a la de inicio.");
    }
    this.fechaRealInicio = inicioReal;
    this.fechaRealFin = finReal;
    this.estado = EstadoAuditoria.EJECUTADA;
  }

  public void cancelar(String motivo) {
    exigirAbierta();
    this.estado = EstadoAuditoria.CANCELADA;
    this.notas = motivo;
  }

  public void cambiarEstadoPaquete(EstadoPaquete nuevo) { this.estadoPaquete = nuevo; }

  public void asignarEquipo(Collection<MiembroEquipo> miembros) {
    exigirAbierta();
    if (miembros.stream().filter(m -> m.rol() == RolAuditor.LIDER).count() > 1) {
      throw new ReglaNegocioException("LIDER_DUPLICADO", "Una auditoría tiene un solo auditor líder.");
    }
    equipo.clear();
    equipo.addAll(miembros);
  }

  public boolean tieneLider() { return equipo.stream().anyMatch(m -> m.rol() == RolAuditor.LIDER); }

  private void exigirAbierta() {
    if (estado == EstadoAuditoria.CANCELADA) {
      throw new ReglaNegocioException("AUDITORIA_CANCELADA", "La auditoría está cancelada.");
    }
  }

  public UUID getExpedienteId() { return expedienteId; }
  public TipoAuditoria getTipo() { return tipo; }
  public EstadoAuditoria getEstado() { return estado; }
  public LocalDate getFechaLimite() { return fechaLimite; }
  public LocalDate getFechaInicioPlan() { return fechaInicioPlan; }
  public LocalDate getFechaFinPlan() { return fechaFinPlan; }
  public LocalDate getFechaRealInicio() { return fechaRealInicio; }
  public LocalDate getFechaRealFin() { return fechaRealFin; }
  public BigDecimal getDiasAuditor() { return diasAuditor; }
  public Modalidad getModalidad() { return modalidad; }
  public EstadoPaquete getEstadoPaquete() { return estadoPaquete; }
  public String getNotas() { return notas; }
  public Set<MiembroEquipo> getEquipo() { return Set.copyOf(equipo); }
}
