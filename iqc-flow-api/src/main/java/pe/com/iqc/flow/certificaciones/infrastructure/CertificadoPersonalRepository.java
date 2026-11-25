package pe.com.iqc.flow.certificaciones.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.certificaciones.domain.CertificadoPersonal;

public interface CertificadoPersonalRepository extends JpaRepository<CertificadoPersonal, UUID>,
    JpaSpecificationExecutor<CertificadoPersonal> {
  boolean existsByOrganizacionIdAndCodigoIgnoreCase(UUID organizacionId, String codigo);

  Optional<CertificadoPersonal> findByFuenteLegadoAndIdLegado(String fuenteLegado, String idLegado);

  /**conteo por país de la persona y estado, para el resumen del espacio de Certificados*/
  @Query(value = """
      SELECT p.codigo_pais AS pais, c.estado AS estado, count(*) AS total
      FROM certificados_personales c JOIN personas_certificadas p ON p.id = c.persona_id
      WHERE c.organizacion_id = :org GROUP BY p.codigo_pais, c.estado
      """, nativeQuery = true)
  List<Object[]> contarPorPaisYEstado(UUID org);
}
