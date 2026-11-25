package pe.com.iqc.flow.cotizaciones.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *cotización a un cliente. Se edita solo en BORRADOR; al enviarla queda fija y espera la respuesta. Los importes se
 *calculan aquí (subtotal = suma de las líneas; impuesto con la tasa del país del cliente al cotizar)
 */
@Entity
@Table(name = "cotizaciones")
public class Cotizacion extends EntidadBase {
  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;
  @Column(nullable = false, updatable = false, length = 20)
  private String numero;
  @Column(name = "cliente_id", nullable = false)
  private UUID clienteId;
  @Column(name = "contacto_nombre", length = 200)
  private String contactoNombre;
  @Column(name = "contacto_correo", length = 320)
  private String contactoCorreo;
  @Column(name = "esquema_codigo", length = 20)
  private String esquemaCodigo;
  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_servicio", nullable = false, length = 30)
  private TipoServicioCotizacion tipoServicio;
  @Column(length = 4000)
  private String alcance;
  @Column(name = "numero_sedes", nullable = false)
  private int numeroSedes = 1;
  @Column(name = "sedes_detalle", length = 1000)
  private String sedesDetalle;
  @Column(nullable = false, length = 3)
  private String moneda;
  @Column(name = "nombre_impuesto", nullable = false, length = 20)
  private String nombreImpuesto;
  @Column(name = "tasa_impuesto", nullable = false, precision = 5, scale = 2)
  private BigDecimal tasaImpuesto;
  @Column(name = "tarifa_dia", precision = 12, scale = 2)
  private BigDecimal tarifaDia;
  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal subtotal = BigDecimal.ZERO;
  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal impuesto = BigDecimal.ZERO;
  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal total = BigDecimal.ZERO;
  @Column(name = "validez_dias", nullable = false)
  private int validezDias = 30;
  @Column(name = "condiciones_pago", length = 2000)
  private String condicionesPago;
  @Column(length = 4000)
  private String notas;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoCotizacion estado = EstadoCotizacion.BORRADOR;
  @Column(name = "fecha_emision", nullable = false)
  private LocalDate fechaEmision;
  @Column(name = "enviada_en")
  private LocalDate enviadaEn;
  @Column(length = 1000)
  private String destinatarios;
  @Column(name = "respondida_en")
  private LocalDate respondidaEn;
  @Column(name = "motivo_respuesta", length = 1000)
  private String motivoRespuesta;
  @Column(name = "recordatorio_en")
  private Instant recordatorioEn;
  @Column(name = "responsable_id", nullable = false)
  private UUID responsableId;
  @Column(name = "creado_por", updatable = false)
  private UUID creadoPor;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "cotizacion_id", nullable = false)
  @OrderBy("orden")
  private List<ItemCotizacion> items = new ArrayList<>();

  protected Cotizacion() {
  }

  public Cotizacion(UUID organizacionId, String numero, UUID creadoPor, LocalDate fechaEmision) {
    this.organizacionId = organizacionId;
    this.numero = numero;
    this.creadoPor = creadoPor;
    this.fechaEmision = fechaEmision;
  }

  /**datos editables; solo en BORRADOR. Recalcula los importes*/
  public void actualizar(UUID clienteId, String contactoNombre, String contactoCorreo, String esquemaCodigo,
      TipoServicioCotizacion tipoServicio, String alcance, int numeroSedes, String sedesDetalle, String moneda,
      String nombreImpuesto, BigDecimal tasaImpuesto, BigDecimal tarifaDia, int validezDias, String condicionesPago,
      String notas, UUID responsableId, List<ItemCotizacion> lineas) {
    exigir(EstadoCotizacion.BORRADOR, "Solo se edita una cotización en borrador. Para cambiar una enviada, duplícala.");
    if (lineas.isEmpty()) throw new ReglaNegocioException("COTIZACION_SIN_LINEAS", "Agrega al menos una norma o servicio.");
    this.clienteId = clienteId;
    this.contactoNombre = contactoNombre;
    this.contactoCorreo = contactoCorreo;
    this.esquemaCodigo = esquemaCodigo;
    this.tipoServicio = tipoServicio;
    this.alcance = alcance;
    this.numeroSedes = numeroSedes;
    this.sedesDetalle = sedesDetalle;
    this.moneda = moneda;
    this.nombreImpuesto = nombreImpuesto;
    this.tasaImpuesto = tasaImpuesto;
    this.tarifaDia = tarifaDia;
    this.validezDias = validezDias;
    this.condicionesPago = condicionesPago;
    this.notas = notas;
    this.responsableId = responsableId;
    this.items.clear();
    this.items.addAll(lineas);
    this.subtotal = lineas.stream().map(ItemCotizacion::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    this.impuesto = subtotal.multiply(tasaImpuesto).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    this.total = subtotal.add(impuesto);
  }

  public void enviar(LocalDate fecha, String destinatarios) {
    exigir(EstadoCotizacion.BORRADOR, "Solo se envía una cotización en borrador.");
    if (total.signum() <= 0) throw new ReglaNegocioException("COTIZACION_SIN_MONTO", "La cotización no tiene monto: revisa las líneas antes de enviarla.");
    this.estado = EstadoCotizacion.ENVIADA;
    this.enviadaEn = fecha;
    this.destinatarios = destinatarios;
    this.recordatorioEn = null;
  }

  public void responder(boolean aceptada, LocalDate fecha, String motivo) {
    exigir(EstadoCotizacion.ENVIADA, "Solo se registra la respuesta de una cotización enviada.");
    if (enviadaEn != null && fecha.isBefore(enviadaEn)) {
      throw new ReglaNegocioException("FECHA_RESPUESTA_INVALIDA", "La respuesta no puede ser anterior al envío.");
    }
    this.estado = aceptada ? EstadoCotizacion.ACEPTADA : EstadoCotizacion.RECHAZADA;
    this.respondidaEn = fecha;
    this.motivoRespuesta = motivo;
  }

  public void anular(String motivo, LocalDate fecha) {
    if (estado != EstadoCotizacion.BORRADOR && estado != EstadoCotizacion.ENVIADA) {
      throw new ReglaNegocioException("COTIZACION_NO_ANULABLE", "Una cotización ya respondida no se anula.");
    }
    this.estado = EstadoCotizacion.ANULADA;
    this.motivoRespuesta = motivo;
    this.respondidaEn = fecha;
  }

  public void marcarRecordatorio(Instant ahora) { this.recordatorioEn = ahora; }

  private void exigir(EstadoCotizacion esperado, String mensaje) {
    if (estado != esperado) throw new ReglaNegocioException("ESTADO_COTIZACION_INVALIDO", mensaje);
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getNumero() { return numero; }
  public UUID getClienteId() { return clienteId; }
  public String getContactoNombre() { return contactoNombre; }
  public String getContactoCorreo() { return contactoCorreo; }
  public String getEsquemaCodigo() { return esquemaCodigo; }
  public TipoServicioCotizacion getTipoServicio() { return tipoServicio; }
  public String getAlcance() { return alcance; }
  public int getNumeroSedes() { return numeroSedes; }
  public String getSedesDetalle() { return sedesDetalle; }
  public String getMoneda() { return moneda; }
  public String getNombreImpuesto() { return nombreImpuesto; }
  public BigDecimal getTasaImpuesto() { return tasaImpuesto; }
  public BigDecimal getTarifaDia() { return tarifaDia; }
  public BigDecimal getSubtotal() { return subtotal; }
  public BigDecimal getImpuesto() { return impuesto; }
  public BigDecimal getTotal() { return total; }
  public int getValidezDias() { return validezDias; }
  public String getCondicionesPago() { return condicionesPago; }
  public String getNotas() { return notas; }
  public EstadoCotizacion getEstado() { return estado; }
  public LocalDate getFechaEmision() { return fechaEmision; }
  public LocalDate getEnviadaEn() { return enviadaEn; }
  public String getDestinatarios() { return destinatarios; }
  public LocalDate getRespondidaEn() { return respondidaEn; }
  public String getMotivoRespuesta() { return motivoRespuesta; }
  public Instant getRecordatorioEn() { return recordatorioEn; }
  public UUID getResponsableId() { return responsableId; }
  public UUID getCreadoPor() { return creadoPor; }
  public List<ItemCotizacion> getItems() { return items; }
}
