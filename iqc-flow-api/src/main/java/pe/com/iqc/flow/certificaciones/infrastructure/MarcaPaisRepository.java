package pe.com.iqc.flow.certificaciones.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.com.iqc.flow.certificaciones.domain.MarcaPais;

public interface MarcaPaisRepository extends JpaRepository<MarcaPais, MarcaPais.Clave> {
  @Query("select m from MarcaPais m where m.clave.organizacionId = :organizacionId")
  List<MarcaPais> deOrganizacion(UUID organizacionId);
}
