package pe.com.iqc.flow.auditores.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import pe.com.iqc.flow.auditores.RolAuditor;
import pe.com.iqc.flow.compartido.domain.EntidadBase;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**auditor (interno o externo). Puede vincularse a un usuario de IQC Flow para el control de imparcialidad*/
@Entity
@Table(name = "auditores")
public class Auditor extends EntidadBase {
  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Column(name = "nombre_completo", nullable = false, length = 200)
  private String nombreCompleto;

  @Column(length = 320)
  private String correo;

  @Column(length = 40)
  private String telefono;

  @Column(name = "codigo_pais", nullable = false, length = 2)
  private String codigoPais;

  @Convert(converter = ConvertidorRolesAuditor.class)
  @Column(nullable = false, length = 200)
  private Set<RolAuditor> roles = EnumSet.noneOf(RolAuditor.class);

  @ElementCollection
  @CollectionTable(name = "competencias_auditor", joinColumns = @JoinColumn(name = "auditor_id"))
  @Column(name = "norma_codigo", length = 20)
  private Set<String> normas = new HashSet<>();

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(nullable = false)
  private boolean activo = true;

  @Column(length = 1000)
  private String notas;

  protected Auditor() {
  }

  public Auditor(UUID organizacionId) { this.organizacionId = organizacionId; }

  public void actualizar(String nombreCompleto, String correo, String telefono, String codigoPais,
      Set<RolAuditor> roles, Set<String> normas, UUID usuarioId, boolean activo, String notas) {
    if (roles == null || roles.isEmpty()) {
      throw new ReglaNegocioException("AUDITOR_SIN_ROLES", "Indica al menos un rol que pueda cumplir el auditor.");
    }
    this.nombreCompleto = nombreCompleto.trim();
    this.correo = correo == null ? null : correo.trim().toLowerCase(java.util.Locale.ROOT);
    this.telefono = telefono;
    this.codigoPais = codigoPais;
    this.roles = EnumSet.copyOf(roles);
    this.normas.clear();
    if (normas != null) this.normas.addAll(normas);
    this.usuarioId = usuarioId;
    this.activo = activo;
    this.notas = notas;
  }

  public boolean puedeCumplir(RolAuditor rol) { return roles.contains(rol); }

  public boolean esCompetenteEn(String normaCodigo) { return normas.contains(normaCodigo); }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getNombreCompleto() { return nombreCompleto; }
  public String getCorreo() { return correo; }
  public String getTelefono() { return telefono; }
  public String getCodigoPais() { return codigoPais; }
  public Set<RolAuditor> getRoles() { return roles.isEmpty() ? Set.of() : EnumSet.copyOf(roles); }
  public Set<String> getNormas() { return new TreeSet<>(normas); }
  public UUID getUsuarioId() { return usuarioId; }
  public boolean isActivo() { return activo; }
  public String getNotas() { return notas; }
}
