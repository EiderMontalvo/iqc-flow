package pe.com.iqc.flow.auditorias.domain;

import java.util.Map;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *código único del certificado según DDC-PRO-003: A país del organismo · B acreditación · C tres primeros dígitos
 *del contrato · D año de la decisión · E sistema (01–05) · F revisión del alcance · G ciclo.
 *Ejemplo: PE-INA-00120210111 = PE · INA · 001 · 2021 · 01 · 1 · 1
 */
public final class CodigoCertificado {
  private static final Map<String, String> ACREDITACION = Map.of("INACAL", "INA", "SAE", "SAE");

  private CodigoCertificado() {
  }

  public static String generar(String paisOrganismo, String esquema, String numeroContrato, int anioDecision,
      String codigoSistema, int revisionAlcance, int ciclo) {
    String acreditacion = ACREDITACION.get(esquema);
    if (acreditacion == null) {
      throw new ReglaNegocioException("ESQUEMA_SIN_CODIGO",
          "El esquema " + esquema + " no genera códigos en IQC Flow (en IQC Global el código lo envía India).");
    }
    String digitos = numeroContrato == null ? "" : numeroContrato.replaceAll("\\D", "");
    if (digitos.length() < 3) {
      throw new ReglaNegocioException("CONTRATO_REQUERIDO",
          "Registra el número de contrato del expediente (al menos 3 dígitos): forma parte del código del certificado.");
    }
    if (codigoSistema == null || !codigoSistema.matches("\\d{2}")) {
      throw new ReglaNegocioException("NORMA_SIN_SISTEMA", "La norma no tiene código de sistema (01–05) en el catálogo.");
    }
    if (revisionAlcance < 1 || revisionAlcance > 9 || ciclo < 1 || ciclo > 9) {
      throw new ReglaNegocioException("REVISION_O_CICLO_INVALIDO", "La revisión del alcance y el ciclo van de 1 a 9.");
    }
    return paisOrganismo + "-" + acreditacion + "-" + digitos.substring(0, 3) + anioDecision + codigoSistema
        + revisionAlcance + ciclo;
  }
}
