package pe.com.iqc.flow.clientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**empresa cliente o prospecto. El código legible (CLI-000001) lo asigna la base de datos. */
@Entity
@Table(name = "clientes")
public class Cliente extends EntidadBase {
  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Generated(event = EventType.INSERT)
  @Column(insertable = false, updatable = false, length = 20)
  private String codigo;

  @Column(name = "razon_social", nullable = false, length = 300)
  private String razonSocial;

  @Column(name = "nombre_comercial", length = 300)
  private String nombreComercial;

  @Column(name = "codigo_pais", nullable = false, length = 2)
  private String codigoPais;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_documento", length = 10)
  private TipoDocumento tipoDocumento;

  @Column(name = "numero_documento", length = 30)
  private String numeroDocumento;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoCliente estado;

  @Column(name = "responsable_usuario_id", nullable = false)
  private UUID responsableUsuarioId;

  @Column(length = 120)
  private String sector;

  @Column(name = "sitio_web", length = 300)
  private String sitioWeb;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private OrigenCliente origen;

  @Column(length = 4000)
  private String notas;

  @Column(name = "texto_busqueda", nullable = false, length = 1000)
  private String textoBusqueda;

  @Column(name = "fuente_legado", length = 40)
  private String fuenteLegado;

  @Column(name = "id_legado", length = 60)
  private String idLegado;

  @Column(name = "creado_por", updatable = false)
  private UUID creadoPor;

  @Column(name = "archivado_en")
  private Instant archivadoEn;

  protected Cliente() {
  }

  public static Cliente nuevo(UUID organizacionId, String razonSocial, String codigoPais, UUID responsableUsuarioId,
      UUID creadoPor) {
    Cliente cliente = new Cliente();
    cliente.organizacionId = organizacionId;
    cliente.razonSocial = razonSocial.trim();
    cliente.codigoPais = ValidadorDocumento.normalizarPais(codigoPais);
    cliente.estado = EstadoCliente.PROSPECTO;
    cliente.responsableUsuarioId = responsableUsuarioId;
    cliente.creadoPor = creadoPor;
    cliente.recalcularBusqueda();
    return cliente;
  }

  public void actualizarDatos(String razonSocial, String nombreComercial, String sector, String sitioWeb,
      OrigenCliente origen, String notas) {
    this.razonSocial = razonSocial.trim();
    this.nombreComercial = nombreComercial;
    this.sector = sector;
    this.sitioWeb = sitioWeb;
    this.origen = origen;
    this.notas = notas;
    recalcularBusqueda();
  }

  /**valida y asigna el país y el documento fiscal. */
  public void asignarDocumento(String codigoPais, TipoDocumento tipo, String numero) {
    this.numeroDocumento = ValidadorDocumento.normalizar(codigoPais, tipo, numero);
    this.codigoPais = ValidadorDocumento.normalizarPais(codigoPais);
    this.tipoDocumento = tipo;
    recalcularBusqueda();
  }

  /**si el cliente ya tiene documento, se revalida contra el nuevo país. */
  public void cambiarPais(String codigoPais) {
    if (tieneDocumento()) {
      asignarDocumento(codigoPais, tipoDocumento, numeroDocumento);
    } else {
      this.codigoPais = ValidadorDocumento.normalizarPais(codigoPais);
    }
  }

  public void cambiarEstado(EstadoCliente nuevoEstado) {
    if (nuevoEstado == EstadoCliente.ACTIVO && numeroDocumento == null) {
      throw new ReglaNegocioException("DOCUMENTO_REQUERIDO",
          "Para pasar un cliente a ACTIVO registra primero su documento fiscal (RUC u otro).");
    }
    this.estado = nuevoEstado;
  }

  /**registra de qué sistema anterior proviene el cliente (importación)*/
  public void marcarOrigenLegado(String fuente, String idLegado) {
    this.fuenteLegado = fuente;
    this.idLegado = idLegado;
  }

  public void reasignar(UUID nuevoResponsable) { this.responsableUsuarioId = nuevoResponsable; }

  public void archivar(Instant ahora) {
    if (estaArchivado()) throw new ReglaNegocioException("CLIENTE_ARCHIVADO", "El cliente ya está archivado.");
    this.archivadoEn = ahora;
  }

  public void restaurar() {
    if (!estaArchivado()) throw new ReglaNegocioException("CLIENTE_NO_ARCHIVADO", "El cliente no está archivado.");
    this.archivadoEn = null;
  }

  public boolean estaArchivado() { return archivadoEn != null; }

  public boolean tieneDocumento() { return numeroDocumento != null; }

  private void recalcularBusqueda() {
    textoBusqueda = NormalizadorTexto.normalizar(String.join(" ", razonSocial,
        nombreComercial == null ? "" : nombreComercial, numeroDocumento == null ? "" : numeroDocumento));
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getCodigo() { return codigo; }
  public String getRazonSocial() { return razonSocial; }
  public String getNombreComercial() { return nombreComercial; }
  public String getCodigoPais() { return codigoPais; }
  public TipoDocumento getTipoDocumento() { return tipoDocumento; }
  public String getNumeroDocumento() { return numeroDocumento; }
  public EstadoCliente getEstado() { return estado; }
  public UUID getResponsableUsuarioId() { return responsableUsuarioId; }
  public String getSector() { return sector; }
  public String getSitioWeb() { return sitioWeb; }
  public OrigenCliente getOrigen() { return origen; }
  public String getNotas() { return notas; }
  public String getFuenteLegado() { return fuenteLegado; }
  public String getIdLegado() { return idLegado; }
  public UUID getCreadoPor() { return creadoPor; }
  public Instant getArchivadoEn() { return archivadoEn; }
}
