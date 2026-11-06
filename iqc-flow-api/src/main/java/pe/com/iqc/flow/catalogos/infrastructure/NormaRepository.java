package pe.com.iqc.flow.catalogos.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.catalogos.domain.Norma;

public interface NormaRepository extends JpaRepository<Norma, String> {
  @EntityGraph(attributePaths = "ediciones")
  List<Norma> findByActivoTrueOrderByOrdenAsc();

  @EntityGraph(attributePaths = "ediciones")
  Optional<Norma> findByCodigoOperaciones(String codigoOperaciones);
}
