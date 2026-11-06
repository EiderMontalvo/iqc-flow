package pe.com.iqc.flow.auditorias.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;
import pe.com.iqc.flow.auditores.RolAuditor;

/**auditor asignado a una auditoría con su rol en ella*/
@Embeddable
public record MiembroEquipo(
    @Column(name = "auditor_id") UUID auditorId,
    @Enumerated(EnumType.STRING) @Column(name = "rol", length = 20) RolAuditor rol) {
}
