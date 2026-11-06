package pe.com.iqc.flow.auditores;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**API pública de auditores para otros módulos*/
public interface ConsultaAuditores {
  /**auditores de la organización con esos ids (activos o no), indexados por id*/
  Map<UUID, AuditorReferencia> buscarPorIds(UUID organizacionId, Collection<UUID> ids);

  record AuditorReferencia(UUID id, String nombreCompleto, Set<RolAuditor> roles, Set<String> normas,
      UUID usuarioId, boolean activo) {
  }
}
