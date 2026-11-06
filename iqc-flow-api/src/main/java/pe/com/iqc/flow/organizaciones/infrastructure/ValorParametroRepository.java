package pe.com.iqc.flow.organizaciones.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.iqc.flow.organizaciones.domain.ValorParametro;

public interface ValorParametroRepository extends JpaRepository<ValorParametro, ValorParametro.Clave> {
}
