package pe.com.iqc.flow.compartido.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**identificador UUID v7, marcas de tiempo y versión para bloqueo optimista*/
@MappedSuperclass
public abstract class EntidadBase {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Version
  private long version;

  @CreationTimestamp
  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @UpdateTimestamp
  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  public UUID getId() { return id; }
  public long getVersion() { return version; }
  public Instant getCreadoEn() { return creadoEn; }
  public Instant getActualizadoEn() { return actualizadoEn; }

  @Override
  public boolean equals(Object otro) {
    if (this == otro) return true;
    if (otro == null || getClass() != otro.getClass()) return false;
    return id != null && Objects.equals(id, ((EntidadBase) otro).id);
  }

  @Override
  public int hashCode() { return getClass().hashCode(); }
}
