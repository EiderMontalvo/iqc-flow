package pe.com.iqc.flow.identidad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import pe.com.iqc.flow.compartido.domain.EntidadBase;

/**usuario interno de IQC Flow*/
@Entity
@Table(name = "usuarios")
public class Usuario extends EntidadBase {
  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Column(nullable = false, unique = true, length = 320)
  private String correo;

  @Column(nullable = false, length = 200)
  private String nombre;

  @Column(name = "hash_contrasena", nullable = false)
  private String hashContrasena;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoUsuario estado;

  @Column(name = "debe_cambiar_contrasena", nullable = false)
  private boolean debeCambiarContrasena;

  @Column(name = "mfa_habilitado", nullable = false)
  private boolean mfaHabilitado;

  @Column(name = "intentos_fallidos", nullable = false)
  private int intentosFallidos;

  @Column(name = "bloqueado_hasta")
  private Instant bloqueadoHasta;

  @Column(name = "ultimo_acceso_en")
  private Instant ultimoAccesoEn;

  @Column(name = "contrasena_cambiada_en")
  private Instant contrasenaCambiadaEn;

  @ManyToMany
  @JoinTable(name = "usuarios_roles",
      joinColumns = @JoinColumn(name = "usuario_id"),
      inverseJoinColumns = @JoinColumn(name = "rol_id"))
  private Set<Rol> roles = new HashSet<>();

  protected Usuario() {
  }

  /**crea un usuario activo que deberá cambiar su contraseña en el primer acceso*/
  public static Usuario nuevo(UUID organizacionId, String correo, String nombre, String hashContrasena,
      Set<Rol> roles, Instant ahora) {
    Usuario usuario = new Usuario();
    usuario.organizacionId = organizacionId;
    usuario.correo = normalizarCorreo(correo);
    usuario.nombre = nombre.trim();
    usuario.hashContrasena = hashContrasena;
    usuario.estado = EstadoUsuario.ACTIVO;
    usuario.debeCambiarContrasena = true;
    usuario.contrasenaCambiadaEn = ahora;
    usuario.roles = new HashSet<>(roles);
    return usuario;
  }

  public static String normalizarCorreo(String correo) {
    return correo == null ? "" : correo.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public boolean estaActivo() { return estado == EstadoUsuario.ACTIVO; }

  public boolean estaBloqueado(Instant ahora) { return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora); }

  public boolean tieneRol(String codigo) { return roles.stream().anyMatch(rol -> rol.getCodigo().equals(codigo)); }

  public Set<String> codigosRoles() {
    return roles.stream().map(Rol::getCodigo).collect(Collectors.toCollection(TreeSet::new));
  }

  public Set<String> permisos() {
    return roles.stream().flatMap(rol -> rol.getPermisos().stream()).map(Permiso::getCodigo)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  public void cambiarContrasena(String nuevoHash, Instant ahora) {
    this.hashContrasena = nuevoHash;
    this.debeCambiarContrasena = false;
    this.contrasenaCambiadaEn = ahora;
  }

  public void renombrar(String nombre) { this.nombre = nombre.trim(); }

  public void cambiarEstado(EstadoUsuario estado) { this.estado = estado; }

  public void reemplazarRoles(Set<Rol> nuevosRoles) {
    roles.clear();
    roles.addAll(nuevosRoles);
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getCorreo() { return correo; }
  public String getNombre() { return nombre; }
  public String getHashContrasena() { return hashContrasena; }
  public EstadoUsuario getEstado() { return estado; }
  public boolean isDebeCambiarContrasena() { return debeCambiarContrasena; }
  public boolean isMfaHabilitado() { return mfaHabilitado; }
  public int getIntentosFallidos() { return intentosFallidos; }
  public Instant getBloqueadoHasta() { return bloqueadoHasta; }
  public Instant getUltimoAccesoEn() { return ultimoAccesoEn; }
  public Instant getContrasenaCambiadaEn() { return contrasenaCambiadaEn; }
}
