package pe.com.iqc.flow.organizaciones;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.organizaciones.domain.Organizacion;
import pe.com.iqc.flow.organizaciones.infrastructure.OrganizacionRepository;

/**API pública del módulo de organizaciones*/
@Service
public class ServicioOrganizaciones {
  private static final String PAIS_POR_DEFECTO = "PE";
  private static final String ZONA_POR_DEFECTO = "America/Lima";

  private final OrganizacionRepository repositorio;
  private final Bitacora bitacora;

  public ServicioOrganizaciones(OrganizacionRepository repositorio, Bitacora bitacora) {
    this.repositorio = repositorio;
    this.bitacora = bitacora;
  }

  @Transactional(readOnly = true)
  public OrganizacionResumen obtener(UUID id) {
    return repositorio.findById(id).map(OrganizacionResumen::de)
        .orElseThrow(() -> new RecursoNoEncontradoException("Organización", id));
  }

  /**devuelve la organización con ese código, creándola si todavía no existe*/
  @Transactional
  public OrganizacionResumen asegurarExistencia(String codigo, String nombre) {
    return repositorio.findByCodigo(codigo).map(OrganizacionResumen::de).orElseGet(() -> {
      Organizacion nueva = repositorio.save(new Organizacion(codigo, nombre, PAIS_POR_DEFECTO, ZONA_POR_DEFECTO));
      bitacora.registrar(EntradaBitacora.accion("ORGANIZACION_CREADA")
          .entidad("ORGANIZACION", nueva.getId())
          .organizacion(nueva.getId())
          .estadoNuevo(Map.of("codigo", codigo, "nombre", nombre)));
      return OrganizacionResumen.de(nueva);
    });
  }
}
