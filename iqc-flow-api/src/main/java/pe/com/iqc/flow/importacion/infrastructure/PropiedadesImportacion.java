package pe.com.iqc.flow.importacion.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 *iqc.importacion-legado.*: archivo del volcado, modo (INFORME o APLICAR), organización, correo del usuario que
 *quedará como responsable de lo importado, ruta del informe y si la aplicación termina al acabar
 */
@ConfigurationProperties("iqc.importacion-legado")
public record PropiedadesImportacion(
    String archivo,
    @DefaultValue("INFORME") Modo modo,
    @DefaultValue("IQC_PERU") String organizacion,
    String responsable,
    String informe,
    @DefaultValue("true") boolean salir) {

  public enum Modo { INFORME, APLICAR }
}
