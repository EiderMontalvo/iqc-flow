package pe.com.iqc.flow.plantillas.infrastructure;

import java.time.LocalDate;
import java.util.Locale;

/**fechas como las imprimen los certificados de IQC (español, sin depender de la configuración regional del servidor)*/
final class FormatoFechas {
  private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
      "septiembre", "octubre", "noviembre", "diciembre"};

  private FormatoFechas() {
  }

  /**03 de Agosto del 2026 (certificado empresarial)*/
  static String larga(LocalDate f) {
    String mes = MESES[f.getMonthValue() - 1];
    return "%02d de %s del %d".formatted(f.getDayOfMonth(), mes.substring(0, 1).toUpperCase(Locale.ROOT) + mes.substring(1),
        f.getYear());
  }

  /**03/08/2026*/
  static String corta(LocalDate f) { return "%02d/%02d/%d".formatted(f.getDayOfMonth(), f.getMonthValue(), f.getYear()); }

  /**17.06.2026 (certificado de IQC Global)*/
  static String puntos(LocalDate f) { return "%02d.%02d.%d".formatted(f.getDayOfMonth(), f.getMonthValue(), f.getYear()); }

  /**19 de julio de 2026 (certificado personal)*/
  static String texto(LocalDate f) { return "%02d de %s de %d".formatted(f.getDayOfMonth(), MESES[f.getMonthValue() - 1], f.getYear()); }

  /**05 al 11 de julio de 2026; entre meses o años distintos, cada extremo con lo que lo distingue; nulo si no hay fechas*/
  static String rango(LocalDate inicio, LocalDate fin) {
    if (inicio == null && fin == null) return null;
    if (inicio == null || fin == null || inicio.equals(fin)) return texto(inicio != null ? inicio : fin);
    if (inicio.getYear() != fin.getYear()) return texto(inicio) + " al " + texto(fin);
    if (inicio.getMonth() != fin.getMonth()) {
      return "%02d de %s al %s".formatted(inicio.getDayOfMonth(), MESES[inicio.getMonthValue() - 1], texto(fin));
    }
    return "%02d al %s".formatted(inicio.getDayOfMonth(), texto(fin));
  }
}
