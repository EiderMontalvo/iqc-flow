package pe.com.iqc.flow.certificaciones.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.certificaciones.domain.PersonaCertificada;

public interface PersonaCertificadaRepository extends JpaRepository<PersonaCertificada, UUID> {
  Optional<PersonaCertificada> findByOrganizacionIdAndCodigoPaisAndTipoDocumentoAndNumeroDocumento(UUID organizacionId,
      String codigoPais, String tipoDocumento, String numeroDocumento);

  Optional<PersonaCertificada> findByFuenteLegadoAndIdLegado(String fuenteLegado, String idLegado);

  List<PersonaCertificada> findByIdIn(Collection<UUID> ids);

  /**ids de las personas cuyo nombre o documento contienen el texto (sin tildes ni mayúsculas)*/
  @Query(value = """
      SELECT id FROM personas_certificadas
      WHERE organizacion_id = :org AND (numero_documento ILIKE '%' || :texto || '%'
        OR translate(lower(nombres), 'áéíóúüñ', 'aeiouun') LIKE '%' || translate(lower(:texto), 'áéíóúüñ', 'aeiouun') || '%')
      """, nativeQuery = true)
  List<UUID> buscarIds(UUID org, String texto);
}
