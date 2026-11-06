package pe.com.iqc.flow.importacion.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**resultado del análisis del volcado: qué se importará, qué se descarta y por qué*/
public record PlanImportacion(
    List<ClientePlan> clientes,
    List<GrupoPlan> grupos,
    List<Descarte> descartes,
    List<String> advertencias,
    List<List<ClientePlan>> posiblesDuplicados,
    int filasCertificaciones,
    int filasClientes,
    int filasQr) {

  public int certificados() { return grupos.stream().mapToInt(g -> g.certificados().size()).sum(); }

  public int qrs() { return grupos.stream().flatMap(g -> g.certificados().stream()).mapToInt(c -> c.qrs().size()).sum(); }

  /**estado: ACTIVO (con un certificado vigente), INACTIVO (solo históricos) o PROSPECTO (sin certificados)*/
  public record ClientePlan(String idLegado, List<String> idsFusionados, String razonSocial, String codigoPais,
      String ruc, String direccion, String notas, String estado) {
  }

  public record GrupoPlan(String clave, String idClienteLegado, String esquema, String tipoServicio, String alcance,
      LocalDate fechaCertificacion, LocalDate fechaExpiracion, LocalDate limiteVigilancia1, LocalDate limiteVigilancia2,
      boolean vigente, List<CertificadoPlan> certificados) {
  }

  public record CertificadoPlan(String idLegado, String norma, String edicion, String codigo, String estado,
      int revisionAlcance, int ciclo, Integer anioDecision, LocalDate fechaOtorgamiento, LocalDate fechaEmision,
      LocalDate fechaExpiracion, String url, boolean emitidoPorIndia, String alcance, List<QrPlan> qrs) {
  }

  public record QrPlan(String idQr, String encriptacionId, boolean activo, Instant registradoEn) {
  }

  /**registro que no se importa; referencia: código del certificado, razón social o identificador del QR*/
  public record Descarte(String tabla, String idLegado, String referencia, String motivo) {
  }
}
