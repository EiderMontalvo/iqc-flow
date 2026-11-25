package pe.com.iqc.flow.identidad.infrastructure;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.organizaciones.ParametroCambiado;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;

/**
 *mantiene los permisos de los roles del sistema alineados con la configuración de MASTER:
 *<ul>
 *<li>MASTER tiene siempre todos los permisos, también los que agreguen migraciones futuras (se sincroniza al arrancar);</li>
 *<li>los parámetros ADMINISTRACION_AUTOCOMPLETADO y ADMINISTRACION_GENERACION_PDF quitan o devuelven la herramienta al
 *rol Administración y logística. Los permisos se recalculan en cada solicitud (ADR-011): el cambio es inmediato.</li>
 *</ul>
 */
@Component
class PermisosPorParametros implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(PermisosPorParametros.class);

  /**parámetro → permiso que controla en el rol Administración y logística*/
  private static final Map<ParametroOrganizacion, String> HERRAMIENTAS_ADMINISTRACION = Map.of(
      ParametroOrganizacion.ADMINISTRACION_AUTOCOMPLETADO, "certificados:autocompletar",
      ParametroOrganizacion.ADMINISTRACION_GENERACION_PDF, "certificados:generar-pdf");

  private final JdbcTemplate jdbc;

  PermisosPorParametros(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional
  public void run(ApplicationArguments argumentos) {
    int agregados = jdbc.update("""
        INSERT INTO roles_permisos (rol_id, permiso_codigo)
        SELECT r.id, p.codigo FROM roles r CROSS JOIN permisos p
        WHERE r.organizacion_id IS NULL AND r.codigo = ?
          AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = p.codigo)
        """, CodigosRol.MASTER);
    if (agregados > 0) log.info("MASTER recibió {} permisos nuevos", agregados);
  }

  @EventListener
  @Transactional
  public void alCambiar(ParametroCambiado evento) {
    String permiso = HERRAMIENTAS_ADMINISTRACION.get(evento.parametro());
    if (permiso == null) return;
    if ("SI".equals(evento.valor())) {
      jdbc.update("""
          INSERT INTO roles_permisos (rol_id, permiso_codigo)
          SELECT r.id, ? FROM roles r WHERE r.organizacion_id IS NULL AND r.codigo = ?
            AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = ?)
          """, permiso, CodigosRol.ADMINISTRACION_LOGISTICA, permiso);
    } else {
      jdbc.update("""
          DELETE FROM roles_permisos WHERE permiso_codigo = ?
            AND rol_id IN (SELECT id FROM roles WHERE organizacion_id IS NULL AND codigo = ?)
          """, permiso, CodigosRol.ADMINISTRACION_LOGISTICA);
    }
  }
}
