package pe.com.iqc.flow.documentos.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**configuración del almacén de documentos (iqc.documentos.*)*/
@ConfigurationProperties("iqc.documentos")
public record PropiedadesDocumentos(
    /**carpeta raíz del almacén local; debe estar fuera del repositorio y con respaldo*/
    @DefaultValue("./datos/documentos") String directorio,
    @DefaultValue("15MB") DataSize tamanoMaximo) {
}
