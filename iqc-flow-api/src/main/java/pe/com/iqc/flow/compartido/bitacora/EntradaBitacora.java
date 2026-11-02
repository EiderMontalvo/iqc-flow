package pe.com.iqc.flow.compartido.bitacora;

import java.util.Map;
import java.util.UUID;

/**
 *datos de un registro de bitácora. El actor, la organización, el origen, la IP y la correlación
 *se completan automáticamente desde el contexto si no se indican.
 *
 *<pre>
 *bitacora.registrar(EntradaBitacora.accion("USUARIO_CREADO").entidad("USUARIO", id).estadoNuevo(datos));
 *</pre>
 */
public final class EntradaBitacora {
  private final String accion;
  private String entidadTipo;
  private String entidadId;
  private Map<String, ?> estadoAnterior;
  private Map<String, ?> estadoNuevo;
  private ResultadoOperacion resultado = ResultadoOperacion.EXITO;
  private String motivo;
  private UUID organizacionId;
  private UUID actorUsuarioId;
  private TipoActor tipoActor;
  private OrigenOperacion origen;

  private EntradaBitacora(String accion) { this.accion = accion; }

  public static EntradaBitacora accion(String accion) { return new EntradaBitacora(accion); }

  public EntradaBitacora entidad(String tipo, Object id) {
    this.entidadTipo = tipo;
    this.entidadId = id == null ? null : id.toString();
    return this;
  }

  public EntradaBitacora estadoAnterior(Map<String, ?> estado) { this.estadoAnterior = estado; return this; }
  public EntradaBitacora estadoNuevo(Map<String, ?> estado) { this.estadoNuevo = estado; return this; }
  public EntradaBitacora resultado(ResultadoOperacion resultado) { this.resultado = resultado; return this; }
  public EntradaBitacora motivo(String motivo) { this.motivo = motivo; return this; }
  public EntradaBitacora organizacion(UUID organizacionId) { this.organizacionId = organizacionId; return this; }
  public EntradaBitacora origen(OrigenOperacion origen) { this.origen = origen; return this; }

  public EntradaBitacora actor(UUID usuarioId, TipoActor tipo) {
    this.actorUsuarioId = usuarioId;
    this.tipoActor = tipo;
    return this;
  }

  String getAccion() { return accion; }
  String getEntidadTipo() { return entidadTipo; }
  String getEntidadId() { return entidadId; }
  Map<String, ?> getEstadoAnterior() { return estadoAnterior; }
  Map<String, ?> getEstadoNuevo() { return estadoNuevo; }
  ResultadoOperacion getResultado() { return resultado; }
  String getMotivo() { return motivo; }
  UUID getOrganizacionId() { return organizacionId; }
  UUID getActorUsuarioId() { return actorUsuarioId; }
  TipoActor getTipoActor() { return tipoActor; }
  OrigenOperacion getOrigen() { return origen; }
}
