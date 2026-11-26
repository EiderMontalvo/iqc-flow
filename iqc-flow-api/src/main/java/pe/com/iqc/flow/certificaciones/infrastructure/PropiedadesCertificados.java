package pe.com.iqc.flow.certificaciones.infrastructure;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *datos fijos que se imprimen en los certificados generados desde plantilla (iqc.certificados.*). La imagen de la firma
 *(resources/certificados/imagenes/firma-gerencia.png) es la de quien figura como firmante: si cambia, cambian las dos
 */
@ConfigurationProperties("iqc.certificados")
public record PropiedadesCertificados(Firmante firmante, Map<String, Map<String, String>> acreditaciones) {
  public PropiedadesCertificados {
    Objects.requireNonNull(firmante, "Configura iqc.certificados.firmante");
    // Las claves llegan como se escriben en el YAML o, desde variables de entorno, en minúsculas
    Map<String, Map<String, String>> normalizadas = new TreeMap<>();
    if (acreditaciones != null) {
      acreditaciones.forEach((esquema, porNorma) -> {
        Map<String, String> normas = new TreeMap<>();
        porNorma.forEach((norma, numero) -> normas.put(norma.toUpperCase(Locale.ROOT), numero));
        normalizadas.put(esquema.toUpperCase(Locale.ROOT), Map.copyOf(normas));
      });
    }
    acreditaciones = Map.copyOf(normalizadas);
  }

  /**titulo: tratamiento opcional («Lic.») que solo lleva el certificado personal*/
  public record Firmante(String titulo, String nombre, String cargo, String empresa) {
    public Firmante {
      Objects.requireNonNull(nombre, "iqc.certificados.firmante.nombre");
      Objects.requireNonNull(cargo, "iqc.certificados.firmante.cargo");
      Objects.requireNonNull(empresa, "iqc.certificados.firmante.empresa");
    }
  }

  /**número con el que el organismo acredita a IQC para esa norma («SAE CSGAS 24-001»); vacío si no está configurado*/
  public Optional<String> acreditacion(String esquema, String norma) {
    return Optional.ofNullable(acreditaciones.get(esquema.toUpperCase(Locale.ROOT))).map(m -> m.get(norma.toUpperCase(Locale.ROOT)));
  }
}
