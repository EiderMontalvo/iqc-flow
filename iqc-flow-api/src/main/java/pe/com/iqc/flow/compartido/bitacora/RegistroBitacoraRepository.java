package pe.com.iqc.flow.compartido.bitacora;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface RegistroBitacoraRepository
    extends JpaRepository<RegistroBitacora, UUID>, JpaSpecificationExecutor<RegistroBitacora> {
}
