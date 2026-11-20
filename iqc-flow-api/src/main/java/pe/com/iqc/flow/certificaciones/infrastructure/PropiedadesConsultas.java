package pe.com.iqc.flow.certificaciones.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 *base pública de consulta (iqc.consultas.*). Sin url la publicación queda deshabilitada. En producción es otra base
 *de datos (de preferencia otro servidor); el usuario de esta conexión es el único que escribe en ella
 */
@ConfigurationProperties("iqc.consultas")
public record PropiedadesConsultas(
    String url,
    String usuario,
    String clave,
    /**esquema de las tablas públicas: "public" en una base propia; otro nombre si comparte la base (solo desarrollo)*/
    @DefaultValue("public") String esquema,
    /**cada cuánto se buscan cambios para publicar*/
    @DefaultValue("PT1M") Duration intervalo) {

  public boolean habilitada() { return url != null && !url.isBlank(); }
}
