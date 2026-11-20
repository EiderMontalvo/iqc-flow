package pe.com.iqc.flow.auditorias.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pe.com.iqc.flow.auditorias.application.DtosExpedientes.Referencia;
import pe.com.iqc.flow.auditorias.domain.Certificado;
import pe.com.iqc.flow.auditorias.domain.TipoServicio;

/**DTOs del panel de certificados (búsqueda general, registro directo, PDF y marca)*/
public final class DtosPanelCertificados {
  private DtosPanelCertificados() {
  }

  /**VIGENTE: emitido y dentro de su fecha; POR_VENCER: vence en los próximos días indicados; VENCIDO: ya expiró*/
  public enum Vigencia { VIGENTE, POR_VENCER, VENCIDO, SIN_FECHA }

  public record FiltroCertificados(String texto, String esquema, String norma, String pais, Certificado.Estado estado,
      Vigencia vigencia, Integer diasPorVencer) {
  }

  public record TitularResumen(UUID id, String razonSocial, String tipoDocumento, String numeroDocumento,
      String codigoPais) {
  }

  public record CertificadoPanel(
      UUID id,
      UUID expedienteId,
      String codigo,
      String norma,
      String designacion,
      String esquema,
      boolean emitidoPorIndia,
      Certificado.Estado estado,
      Vigencia vigencia,
      TitularResumen cliente,
      /**país de la marca con que se muestra (el elegido a mano o el del cliente)*/
      String paisMarca,
      boolean marcaManual,
      String alcance,
      LocalDate fechaOtorgamiento,
      LocalDate fechaEmision,
      LocalDate fechaExpiracion,
      boolean tienePdf,
      UUID documentoPdfId,
      String urlDocumentoLegado,
      long version) {
  }

  /**
   *registro directo, como en el panel anterior: uno o más certificados (uno por norma) que se emitieron juntos.
   *Crea un expediente ya entregado. Los límites de vigilancia son opcionales y, si son futuros, generan tareas
   */
  public record SolicitudRegistroDirecto(
      @NotNull UUID clienteId,
      @NotBlank String esquema,
      TipoServicio tipoServicio,
      @Size(max = 4000) String alcance,
      @NotNull LocalDate fechaOtorgamiento,
      @NotNull LocalDate fechaEmision,
      @NotNull LocalDate fechaExpiracion,
      LocalDate limiteVigilancia1,
      LocalDate limiteVigilancia2,
      Boolean emitidoPorIndia,
      @NotBlank @Size(max = 500) String motivo,
      @NotEmpty @Valid List<CertificadoDirecto> certificados) {
  }

  public record CertificadoDirecto(
      @NotBlank String norma,
      @NotBlank String edicion,
      @NotBlank @Size(max = 60) String codigo,
      @Min(1) @Max(9) Integer revisionAlcance,
      @Min(1) @Max(9) Integer ciclo,
      UUID documentoPdfId) {
  }

  public record RegistroDirectoRespuesta(UUID expedienteId, String expedienteCodigo, List<CertificadoPanel> certificados,
      int vigilancias, int tareas) {
  }

  public record SolicitudPdf(@NotNull UUID documentoId, @Size(max = 500) String motivo, @NotNull Long version) {
  }

  public record VersionPdf(int numero, UUID documentoId, String nombre, long tamanoBytes, String sha256, String motivo,
      Referencia subidoPor, Instant subidoEn, boolean vigente) {
  }

  /**pais nulo vuelve a la marca del país del cliente*/
  public record SolicitudMarca(@Size(min = 2, max = 2) String pais, @NotBlank @Size(max = 500) String motivo,
      @NotNull Long version) {
  }
}
