package pe.com.iqc.flow.certificaciones.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import pe.com.iqc.flow.certificaciones.MarcasPais.MarcaVigente;

/**
 *marcas de fábrica: IQC Perú consulta en consultas.iqcperu.com (fija: está impresa en los PDF y QR ya emitidos) y
 *los países de IQC LATAM en consultas.iqc-latam.com. MASTER puede cambiarlas desde el panel
 */
public final class MarcasPredeterminadas {
  public static final String CONSULTA_PERU = "https://consultas.iqcperu.com/";
  public static final String CONSULTA_LATAM = "https://consultas.iqc-latam.com/";

  private static final Map<String, MarcaVigente> MARCAS = new LinkedHashMap<>();

  static {
    MARCAS.put("PE", marca("PE", "IQC Perú", CONSULTA_PERU, "https://iqcperu.com"));
    MARCAS.put("EC", marca("EC", "IQC Ecuador", CONSULTA_LATAM, "https://iqc-latam.com"));
    MARCAS.put("CO", marca("CO", "IQC Colombia", CONSULTA_LATAM, "https://colombia.iqc-latam.com"));
    MARCAS.put("PA", marca("PA", "IQC Panamá", CONSULTA_LATAM, "https://iqc-latam.com"));
  }

  private MarcasPredeterminadas() {
  }

  public static Optional<MarcaVigente> de(String codigoPais) { return Optional.ofNullable(MARCAS.get(codigoPais)); }

  public static Map<String, MarcaVigente> todas() { return Map.copyOf(MARCAS); }

  private static MarcaVigente marca(String pais, String nombre, String consulta, String sitio) {
    return new MarcaVigente(pais, nombre, consulta, sitio, null, null, null, null, true);
  }
}
