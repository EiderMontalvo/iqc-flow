package pe.com.iqc.flow.organizaciones;

import java.util.UUID;
import pe.com.iqc.flow.organizaciones.domain.Organizacion;

/**datos públicos de una organización para otros módulos. El país es el del organismo (parte A del código del certificado)*/
public record OrganizacionResumen(UUID id, String codigo, String nombre, String codigoPais, String zonaHoraria) {
  static OrganizacionResumen de(Organizacion organizacion) {
    return new OrganizacionResumen(organizacion.getId(), organizacion.getCodigo(), organizacion.getNombre(),
        organizacion.getCodigoPais(), organizacion.getZonaHoraria());
  }
}
