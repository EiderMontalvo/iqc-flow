package pe.com.iqc.flow.auditorias.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *certificado de una norma. Ciclo de vida propio (INACAL/SAE): borrador → revisado (checklist de emisión) →
 *firmado y emitido. En IQC Global se registra ya emitido con el código que envía India. El estado
 *administrativo (suspendido, retirado…) es independiente de la vigencia (fecha de expiración)
 */
@Entity
@Table(name = "certificados")
public class Certificado extends EntidadBase {
  public enum Estado { BORRADOR, REVISADO, EMITIDO, SUSPENDIDO, RETIRADO, CANCELADO, REEMPLAZADO }

  public enum TipoFirma { GERENCIA, JEFE_TECNICO }

  public enum MedioEntrega { DIGITAL, FISICA }

  /**estados con un certificado emitido que sigue existiendo para el cliente*/
  public static final Set<Estado> VIGENTES = EnumSet.of(Estado.EMITIDO, Estado.SUSPENDIDO);

  private static final DateTimeFormatter FORMATO_VERSION = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Column(name = "expediente_id", nullable = false, updatable = false)
  private UUID expedienteId;

  @Column(name = "cliente_id", nullable = false, updatable = false)
  private UUID clienteId;

  @Column(name = "esquema_codigo", nullable = false, updatable = false, length = 20)
  private String esquemaCodigo;

  @Column(name = "norma_codigo", nullable = false, updatable = false, length = 20)
  private String normaCodigo;

  @Column(nullable = false, updatable = false, length = 20)
  private String edicion;

  @Column(nullable = false, length = 60)
  private String codigo;

  @Column(name = "anio_decision", updatable = false)
  private Integer anioDecision;

  @Column(name = "emitido_por_india", nullable = false, updatable = false)
  private boolean emitidoPorIndia;

  @Column(name = "revision_alcance", nullable = false)
  private int revisionAlcance;

  @Column(nullable = false)
  private int ciclo;

  @Column(length = 4000)
  private String alcance;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private Estado estado;

  @Column(name = "decision_id", updatable = false)
  private UUID decisionId;

  @Column(name = "reemplaza_id", updatable = false)
  private UUID reemplazaId;

  @Column(name = "revisado_por")
  private UUID revisadoPor;

  @Column(name = "revisado_en")
  private Instant revisadoEn;

  @Column(name = "firma_gerencia_por")
  private UUID firmaGerenciaPor;

  @Column(name = "firma_gerencia_en")
  private Instant firmaGerenciaEn;

  @Column(name = "firma_jefe_tecnico_por")
  private UUID firmaJefeTecnicoPor;

  @Column(name = "firma_jefe_tecnico_en")
  private Instant firmaJefeTecnicoEn;

  @Column(name = "fecha_otorgamiento")
  private LocalDate fechaOtorgamiento;

  @Column(name = "fecha_emision")
  private LocalDate fechaEmision;

  @Column(name = "fecha_expiracion")
  private LocalDate fechaExpiracion;

  @Column(name = "version_documento", length = 30)
  private String versionDocumento;

  @Column(name = "numero_version", nullable = false)
  private int numeroVersion = 1;

  @Column(name = "token_verificacion", nullable = false, updatable = false)
  private UUID tokenVerificacion;

  @Column(name = "traduccion_documento_id")
  private UUID traduccionDocumentoId;

  @Column(name = "entregado_en")
  private Instant entregadoEn;

  @Enumerated(EnumType.STRING)
  @Column(name = "medio_entrega", length = 10)
  private MedioEntrega medioEntrega;

  @Column(name = "fuente_legado", updatable = false, length = 40)
  private String fuenteLegado;

  @Column(name = "id_legado", updatable = false, length = 60)
  private String idLegado;

  @Column(name = "url_documento_legado", updatable = false, length = 2048)
  private String urlDocumentoLegado;

  protected Certificado() {
  }

  private Certificado(Expediente expediente, NormaExpediente norma, String codigo) {
    this.organizacionId = expediente.getOrganizacionId();
    this.expedienteId = expediente.getId();
    this.clienteId = expediente.getClienteId();
    this.esquemaCodigo = expediente.getEsquemaCodigo();
    this.normaCodigo = norma.normaCodigo();
    this.edicion = norma.edicion();
    this.codigo = codigo;
    this.tokenVerificacion = UUID.randomUUID();
  }

  /**borrador generado por una decisión favorable (INACAL/SAE)*/
  public static Certificado borrador(Expediente expediente, NormaExpediente norma, String codigo, int anioDecision,
      int revisionAlcance, int ciclo, String alcance, UUID decisionId, Certificado anterior) {
    Certificado c = new Certificado(expediente, norma, codigo);
    c.anioDecision = anioDecision;
    c.revisionAlcance = revisionAlcance;
    c.ciclo = ciclo;
    c.alcance = alcance;
    c.estado = Estado.BORRADOR;
    c.decisionId = decisionId;
    if (anterior != null) {
      c.reemplazaId = anterior.getId();
      // Una ampliación o reducción conserva las fechas del ciclo vigente
      if (anterior.ciclo == ciclo) {
        c.fechaOtorgamiento = anterior.fechaOtorgamiento;
        c.fechaExpiracion = anterior.fechaExpiracion;
      }
    }
    return c;
  }

  /**certificado emitido por India (IQC Global): se registra ya emitido con su código*/
  public static Certificado externo(Expediente expediente, NormaExpediente norma, String codigo, LocalDate fechaEmision,
      LocalDate fechaOtorgamiento, LocalDate fechaExpiracion, UUID traduccionDocumentoId) {
    Certificado c = new Certificado(expediente, norma, codigo);
    c.emitidoPorIndia = true;
    c.revisionAlcance = 1;
    c.ciclo = 1;
    c.alcance = expediente.getAlcance();
    c.estado = Estado.EMITIDO;
    c.fechaEmision = fechaEmision;
    c.fechaOtorgamiento = fechaOtorgamiento;
    c.fechaExpiracion = fechaExpiracion;
    c.versionDocumento = "V.01 - " + fechaEmision.format(FORMATO_VERSION);
    c.traduccionDocumentoId = traduccionDocumentoId;
    return c;
  }

  /**
   *certificado histórico importado de la plataforma anterior, con su código original (sin regenerarlo), su estado
   *administrativo y la URL de su PDF, que sigue en el almacén anterior (ADR-005)
   */
  public static Certificado historico(Expediente expediente, NormaExpediente norma, String codigo, Estado estado,
      int revisionAlcance, int ciclo, Integer anioDecision, boolean emitidoPorIndia, String alcance,
      LocalDate fechaOtorgamiento, LocalDate fechaEmision, LocalDate fechaExpiracion, String urlDocumento,
      String fuenteLegado, String idLegado) {
    if (!VIGENTES.contains(estado) && estado != Estado.RETIRADO) {
      throw new ReglaNegocioException("ESTADO_CERTIFICADO", "Un certificado histórico debe estar emitido, suspendido o retirado.");
    }
    Certificado c = new Certificado(expediente, norma, codigo);
    c.estado = estado;
    c.revisionAlcance = revisionAlcance;
    c.ciclo = ciclo;
    c.anioDecision = anioDecision;
    c.emitidoPorIndia = emitidoPorIndia;
    c.alcance = alcance;
    c.fechaOtorgamiento = fechaOtorgamiento;
    c.fechaEmision = fechaEmision;
    c.fechaExpiracion = fechaExpiracion;
    c.urlDocumentoLegado = urlDocumento;
    c.fuenteLegado = fuenteLegado;
    c.idLegado = idLegado;
    return c;
  }

  /**en borrador se pueden corregir la revisión, el ciclo (p. ej. clientes con historia previa) y el alcance*/
  public void corregirBorrador(int revisionAlcance, int ciclo, String alcance, String nuevoCodigo) {
    exigir(Estado.BORRADOR, "Solo se corrige un certificado en borrador.");
    this.revisionAlcance = revisionAlcance;
    this.ciclo = ciclo;
    this.alcance = alcance;
    this.codigo = nuevoCodigo;
  }

  /**checklist de emisión (DDC-MAN-001) conforme*/
  public void revisar(UUID actor, Instant ahora) {
    exigir(Estado.BORRADOR, "Solo se revisa un certificado en borrador.");
    this.estado = Estado.REVISADO;
    this.revisadoPor = actor;
    this.revisadoEn = ahora;
  }

  public void firmar(TipoFirma tipo, UUID actor, Instant ahora) {
    exigir(Estado.REVISADO, "Solo se firma un certificado revisado con el checklist de emisión.");
    UUID previa = tipo == TipoFirma.GERENCIA ? firmaGerenciaPor : firmaJefeTecnicoPor;
    if (previa != null) throw new ReglaNegocioException("FIRMA_REGISTRADA", "El certificado ya tiene la firma de " + tipo + ".");
    UUID otra = tipo == TipoFirma.GERENCIA ? firmaJefeTecnicoPor : firmaGerenciaPor;
    if (actor.equals(otra)) {
      throw new ReglaNegocioException("FIRMANTE_REPETIDO", "Las dos firmas deben ser de personas distintas.");
    }
    if (tipo == TipoFirma.GERENCIA) {
      firmaGerenciaPor = actor;
      firmaGerenciaEn = ahora;
    } else {
      firmaJefeTecnicoPor = actor;
      firmaJefeTecnicoEn = ahora;
    }
  }

  /**FIRMA_GERENCIA: basta Gerencia; FIRMA_GERENCIA_Y_JEFE_TECNICO: ambas*/
  public boolean firmasCompletas(String politica) {
    boolean gerencia = firmaGerenciaPor != null;
    return "FIRMA_GERENCIA_Y_JEFE_TECNICO".equals(politica) ? gerencia && firmaJefeTecnicoPor != null : gerencia;
  }

  public void emitir(LocalDate fechaEmision, LocalDate otorgamiento, LocalDate expiracion) {
    exigir(Estado.REVISADO, "Solo se emite un certificado revisado y firmado.");
    if (fechaOtorgamiento == null) fechaOtorgamiento = otorgamiento;
    if (fechaExpiracion == null) fechaExpiracion = expiracion;
    if (fechaOtorgamiento == null || fechaExpiracion == null) {
      throw new ReglaNegocioException("FECHAS_CICLO_REQUERIDAS",
          "Registra la fecha de certificación del expediente: el certificado debe mostrar el otorgamiento, las vigilancias y la expiración.");
    }
    this.fechaEmision = fechaEmision;
    this.estado = Estado.EMITIDO;
    this.versionDocumento = "V.%02d - %s".formatted(numeroVersion, fechaEmision.format(FORMATO_VERSION));
  }

  public void suspender() {
    exigir(Estado.EMITIDO, "Solo se suspende un certificado emitido y no suspendido.");
    estado = Estado.SUSPENDIDO;
  }

  public void restaurar() {
    exigir(Estado.SUSPENDIDO, "Solo se restaura un certificado suspendido.");
    estado = Estado.EMITIDO;
  }

  public void retirar(boolean voluntario) {
    if (!VIGENTES.contains(estado)) {
      throw new ReglaNegocioException("ESTADO_CERTIFICADO", "Solo se retira o cancela un certificado emitido o suspendido.");
    }
    estado = voluntario ? Estado.CANCELADO : Estado.RETIRADO;
  }

  /**otro certificado de la misma norma (renovación, ampliación o reducción) lo sustituye al emitirse*/
  public void reemplazar() {
    if (VIGENTES.contains(estado)) estado = Estado.REEMPLAZADO;
  }

  public void entregar(MedioEntrega medio, Instant ahora) {
    if (!VIGENTES.contains(estado)) {
      throw new ReglaNegocioException("ESTADO_CERTIFICADO", "Solo se entrega un certificado emitido.");
    }
    this.medioEntrega = medio;
    this.entregadoEn = ahora;
  }

  public void adjuntarTraduccion(UUID documentoId) {
    if (!emitidoPorIndia) {
      throw new ReglaNegocioException("TRADUCCION_NO_APLICA", "La traducción solo aplica a certificados emitidos por India.");
    }
    this.traduccionDocumentoId = documentoId;
  }

  private void exigir(Estado esperado, String mensaje) {
    if (estado != esperado) throw new ReglaNegocioException("ESTADO_CERTIFICADO", mensaje);
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public UUID getExpedienteId() { return expedienteId; }
  public UUID getClienteId() { return clienteId; }
  public String getEsquemaCodigo() { return esquemaCodigo; }
  public String getNormaCodigo() { return normaCodigo; }
  public String getEdicion() { return edicion; }
  public String getCodigo() { return codigo; }
  public Integer getAnioDecision() { return anioDecision; }
  public boolean isEmitidoPorIndia() { return emitidoPorIndia; }
  public int getRevisionAlcance() { return revisionAlcance; }
  public int getCiclo() { return ciclo; }
  public String getAlcance() { return alcance; }
  public Estado getEstado() { return estado; }
  public UUID getDecisionId() { return decisionId; }
  public UUID getReemplazaId() { return reemplazaId; }
  public UUID getRevisadoPor() { return revisadoPor; }
  public Instant getRevisadoEn() { return revisadoEn; }
  public UUID getFirmaGerenciaPor() { return firmaGerenciaPor; }
  public Instant getFirmaGerenciaEn() { return firmaGerenciaEn; }
  public UUID getFirmaJefeTecnicoPor() { return firmaJefeTecnicoPor; }
  public Instant getFirmaJefeTecnicoEn() { return firmaJefeTecnicoEn; }
  public LocalDate getFechaOtorgamiento() { return fechaOtorgamiento; }
  public LocalDate getFechaEmision() { return fechaEmision; }
  public LocalDate getFechaExpiracion() { return fechaExpiracion; }
  public String getVersionDocumento() { return versionDocumento; }
  public UUID getTokenVerificacion() { return tokenVerificacion; }
  public UUID getTraduccionDocumentoId() { return traduccionDocumentoId; }
  public Instant getEntregadoEn() { return entregadoEn; }
  public MedioEntrega getMedioEntrega() { return medioEntrega; }
  public String getFuenteLegado() { return fuenteLegado; }
  public String getIdLegado() { return idLegado; }
  public String getUrlDocumentoLegado() { return urlDocumentoLegado; }
}
