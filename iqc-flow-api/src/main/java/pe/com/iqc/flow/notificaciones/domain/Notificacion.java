package pe.com.iqc.flow.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;

/**aviso interno para un usuario. El correo electrónico llegará con el módulo de integraciones*/
@Entity
@Table(name = "notificaciones")
public class Notificacion extends EntidadBase {
  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Column(name = "destinatario_usuario_id", nullable = false, updatable = false)
  private UUID destinatarioUsuarioId;

  @Column(nullable = false, length = 40, updatable = false)
  private String tipo;

  @Column(nullable = false, length = 200, updatable = false)
  private String titulo;

  @Column(length = 1000, updatable = false)
  private String cuerpo;

  @Column(name = "sujeto_tipo", length = 40, updatable = false)
  private String sujetoTipo;

  @Column(name = "sujeto_id", updatable = false)
  private UUID sujetoId;

  @Column(name = "clave_deduplicacion", length = 200, updatable = false)
  private String claveDeduplicacion;

  @Column(name = "leida_en")
  private Instant leidaEn;

  protected Notificacion() {
  }

  public Notificacion(UUID organizacionId, UUID destinatarioUsuarioId, String tipo, String titulo, String cuerpo,
      String sujetoTipo, UUID sujetoId, String claveDeduplicacion) {
    this.organizacionId = organizacionId;
    this.destinatarioUsuarioId = destinatarioUsuarioId;
    this.tipo = tipo;
    this.titulo = titulo;
    this.cuerpo = cuerpo;
    this.sujetoTipo = sujetoTipo;
    this.sujetoId = sujetoId;
    this.claveDeduplicacion = claveDeduplicacion;
  }

  public void marcarLeida(Instant ahora) {
    if (leidaEn == null) leidaEn = ahora;
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public UUID getDestinatarioUsuarioId() { return destinatarioUsuarioId; }
  public String getTipo() { return tipo; }
  public String getTitulo() { return titulo; }
  public String getCuerpo() { return cuerpo; }
  public String getSujetoTipo() { return sujetoTipo; }
  public UUID getSujetoId() { return sujetoId; }
  public String getClaveDeduplicacion() { return claveDeduplicacion; }
  public Instant getLeidaEn() { return leidaEn; }
}
