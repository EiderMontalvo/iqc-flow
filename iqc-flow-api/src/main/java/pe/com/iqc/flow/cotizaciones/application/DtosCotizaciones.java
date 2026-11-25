package pe.com.iqc.flow.cotizaciones.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pe.com.iqc.flow.cotizaciones.domain.EstadoCotizacion;
import pe.com.iqc.flow.cotizaciones.domain.TipoServicioCotizacion;

public final class DtosCotizaciones {
  private DtosCotizaciones() {
  }

  public record SolicitudItem(@Size(max = 20) String normaCodigo, @Size(max = 10) String edicion,
      @NotBlank @Size(max = 500) String descripcion, @NotNull @DecimalMin("0") BigDecimal diasAuditor,
      @NotNull @DecimalMin("0") BigDecimal monto) {
  }

  /**crear o editar (en borrador). version: obligatoria al editar*/
  public record SolicitudCotizacion(@NotNull UUID clienteId, @Size(max = 200) String contactoNombre,
      @Email @Size(max = 320) String contactoCorreo, @Size(max = 20) String esquemaCodigo,
      @NotNull TipoServicioCotizacion tipoServicio, @Size(max = 4000) String alcance, @Min(1) @Max(999) Integer numeroSedes,
      @Size(max = 1000) String sedesDetalle, @DecimalMin("0") BigDecimal tarifaDia, @Min(1) @Max(365) Integer validezDias,
      @Size(max = 2000) String condicionesPago, @Size(max = 4000) String notas, UUID responsableId,
      @NotEmpty @Size(max = 20) List<@Valid SolicitudItem> items, Long version) {
  }

  public record SolicitudEnvio(@NotNull LocalDate fecha, @Size(max = 1000) String destinatarios, long version) {
  }

  public record SolicitudRespuesta(@NotNull Boolean aceptada, @NotNull LocalDate fecha, @Size(max = 1000) String motivo, long version) {
  }

  public record SolicitudAnulacion(@NotBlank @Size(max = 1000) String motivo, long version) {
  }

  public record Referencia(UUID id, String nombre) {
  }

  public record ClienteCotizacion(UUID id, String razonSocial, String tipoDocumento, String numeroDocumento, String codigoPais) {
  }

  public record ItemRespuesta(UUID id, String normaCodigo, String normaNombre, String edicion, String descripcion,
      BigDecimal diasAuditor, BigDecimal monto) {
  }

  /**diasSinRespuesta: solo en ENVIADA (días desde el envío); vence: fecha de fin de validez*/
  public record CotizacionResumen(UUID id, String numero, ClienteCotizacion cliente, TipoServicioCotizacion tipoServicio,
      List<String> normas, String moneda, BigDecimal total, EstadoCotizacion estado, LocalDate fechaEmision,
      LocalDate enviadaEn, LocalDate respondidaEn, Integer diasSinRespuesta, Referencia responsable, long version) {
  }

  public record CotizacionDetalle(UUID id, String numero, ClienteCotizacion cliente, String contactoNombre, String contactoCorreo,
      String esquemaCodigo, TipoServicioCotizacion tipoServicio, String alcance, int numeroSedes, String sedesDetalle,
      String moneda, String nombreImpuesto, BigDecimal tasaImpuesto, BigDecimal tarifaDia, BigDecimal subtotal,
      BigDecimal impuesto, BigDecimal total, BigDecimal diasAuditorTotal, int validezDias, LocalDate validaHasta,
      String condicionesPago, String notas, EstadoCotizacion estado, LocalDate fechaEmision, LocalDate enviadaEn,
      String destinatarios, LocalDate respondidaEn, String motivoRespuesta, Integer diasSinRespuesta,
      Referencia responsable, Referencia creadoPor, List<ItemRespuesta> items, long version) {
  }

  /**cifras de la cabecera de la pantalla; conversión = aceptadas / (aceptadas + rechazadas) de los últimos 12 meses*/
  public record ResumenCotizaciones(long borradores, long enviadas, long enviadasSinRespuesta, long aceptadasMes,
      long emitidasMes, Integer conversion12Meses, int diasSinRespuesta) {
  }
}
