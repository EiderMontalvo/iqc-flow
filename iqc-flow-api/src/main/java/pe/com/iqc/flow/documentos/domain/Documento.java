package pe.com.iqc.flow.documentos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import pe.com.iqc.flow.documentos.CategoriaDocumento;

/**metadatos de un archivo adjunto. Es inmutable: una corrección se sube como un documento nuevo*/
@Entity
@Table(name = "documentos")
public class Documento {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 40)
  private CategoriaDocumento categoria;

  @Column(name = "nombre_original", nullable = false, updatable = false)
  private String nombreOriginal;

  @Column(name = "tipo_contenido", nullable = false, updatable = false, length = 100)
  private String tipoContenido;

  @Column(name = "tamano_bytes", nullable = false, updatable = false)
  private long tamanoBytes;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, updatable = false, length = 64)
  private String sha256;

  @Column(name = "ruta_almacen", nullable = false, updatable = false, length = 500)
  private String rutaAlmacen;

  @Column(name = "subido_por", nullable = false, updatable = false)
  private UUID subidoPor;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected Documento() {
  }

  public Documento(UUID organizacionId, CategoriaDocumento categoria, String nombreOriginal, String tipoContenido,
      long tamanoBytes, String sha256, String rutaAlmacen, UUID subidoPor, Instant creadoEn) {
    this.organizacionId = organizacionId;
    this.categoria = categoria;
    this.nombreOriginal = nombreOriginal;
    this.tipoContenido = tipoContenido;
    this.tamanoBytes = tamanoBytes;
    this.sha256 = sha256;
    this.rutaAlmacen = rutaAlmacen;
    this.subidoPor = subidoPor;
    this.creadoEn = creadoEn;
  }

  public UUID getId() { return id; }
  public UUID getOrganizacionId() { return organizacionId; }
  public CategoriaDocumento getCategoria() { return categoria; }
  public String getNombreOriginal() { return nombreOriginal; }
  public String getTipoContenido() { return tipoContenido; }
  public long getTamanoBytes() { return tamanoBytes; }
  public String getSha256() { return sha256; }
  public String getRutaAlmacen() { return rutaAlmacen; }
  public UUID getSubidoPor() { return subidoPor; }
  public Instant getCreadoEn() { return creadoEn; }
}
