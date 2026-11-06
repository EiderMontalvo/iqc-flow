package pe.com.iqc.flow.auditorias.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 *fechas del ciclo a partir de la fecha efectiva de otorgamiento (confirmado por IQC: igual en IQC Global,
 *INACAL y SAE): cada vigilancia antes del aniversario correspondiente y expiración al cumplirse el ciclo.
 *Ejemplo DDC-PRO-003: 26/08/2024 → vigilancias antes de 26/08/2025 y 26/08/2026; expiración 26/08/2027
 */
public record CicloCertificacion(LocalDate fechaCertificacion, List<LocalDate> limitesVigilancia, LocalDate expiracion) {
  public static CicloCertificacion calcular(LocalDate fechaCertificacion, int aniosCiclo, int vigilancias) {
    List<LocalDate> limites = new ArrayList<>();
    for (int i = 1; i <= vigilancias; i++) limites.add(fechaCertificacion.plusYears(i));
    return new CicloCertificacion(fechaCertificacion, List.copyOf(limites), fechaCertificacion.plusYears(aniosCiclo));
  }

  /**tipo de auditoría de la vigilancia número (1 o 2)*/
  public static TipoAuditoria tipoVigilancia(int numero) {
    return switch (numero) {
      case 1 -> TipoAuditoria.VIGILANCIA_1;
      case 2 -> TipoAuditoria.VIGILANCIA_2;
      default -> throw new IllegalArgumentException("Solo se contemplan dos vigilancias por ciclo: " + numero);
    };
  }
}
