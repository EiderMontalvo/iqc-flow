package pe.com.iqc.flow.auditorias.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**anticipación con la que se crean las tareas del ciclo (iqc.expedientes.*), en días*/
@ConfigurationProperties("iqc.expedientes")
public record PropiedadesExpedientes(
    @DefaultValue("60") int diasAnticipacionVigilancia,
    @DefaultValue("120") int diasAnticipacionRecertificacion) {
}
