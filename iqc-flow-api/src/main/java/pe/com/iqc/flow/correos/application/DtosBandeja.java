package pe.com.iqc.flow.correos.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.com.iqc.flow.correos.TipoCorreo;
import pe.com.iqc.flow.correos.domain.EstadoCorreo;
import pe.com.iqc.flow.correos.domain.OrigenTipo;

/**lo que entra y sale de la API de la bandeja; la validación de lo que entra se hace aquí, en el borde*/
public final class DtosBandeja {
  private DtosBandeja() {
  }

  /**una fila de la bandeja; `extracto` son las primeras palabras del texto en una sola línea*/
  public record CorreoResumen(UUID id, String cuenta, String remitenteCorreo, String remitenteNombre, String asunto, Instant fecha, String extracto,
      TipoCorreo tipo, OrigenTipo origenTipo, EstadoCorreo estado, UUID responsableId, UUID clienteId, int adjuntos, long version) {
  }

  /**`indice` es el que se pasa a la descarga del adjunto*/
  public record AdjuntoCorreo(int indice, String nombre, String tipo, long tamano) {
  }

  public record ClienteLigado(UUID id, String razonSocial) {
  }

  /**un mensaje del mismo hilo (responde a este o es respondido por este)*/
  public record CorreoEnHilo(UUID id, String asunto, String remitenteCorreo, Instant fecha) {
  }

  /**un correo enviado desde IQC Flow: respuesta a un recibido (`correoRecibidoId`) o correo nuevo (nulo)*/
  public record CorreoEnviado(UUID id, String cuenta, String para, String asunto, String texto, String messageId, UUID correoRecibidoId, UUID enviadoPor,
      Instant enviadoEn) {
  }

  /**`texto` es siempre texto plano, nunca HTML; `motivoTipo` explica por qué tiene ese tipo*/
  public record CorreoDetalle(UUID id, String cuenta, String remitenteCorreo, String remitenteNombre, String para, String cc, String asunto, Instant fecha,
      String texto, boolean textoTruncado, TipoCorreo tipo, OrigenTipo origenTipo, String motivoTipo, EstadoCorreo estado, UUID responsableId, ClienteLigado cliente,
      boolean tieneEml, List<AdjuntoCorreo> adjuntos, List<CorreoEnHilo> hilo, List<CorreoEnviado> respuestas, long version) {
  }

  /**todos opcionales; la PUBLICIDAD no sale a menos que se pida `tipo=PUBLICIDAD` o `incluirPublicidad=true`; `sinAtender` deja solo los NUEVO y EN_CURSO; `desde` y `hasta` incluyen el día entero (hora de Lima)*/
  public record FiltroBandeja(String cuenta, TipoCorreo tipo, EstadoCorreo estado, String texto, LocalDate desde, LocalDate hasta, Boolean sinAtender,
      UUID responsableId, Boolean incluirPublicidad) {
  }

  /**los campos ausentes no cambian; `quitarResponsable` y `quitarCliente` en verdadero los dejan vacíos. Cambiar el tipo lo fija como MANUAL*/
  public record SolicitudActualizacion(TipoCorreo tipo, EstadoCorreo estado, UUID responsableId, UUID clienteId, Boolean quitarResponsable, Boolean quitarCliente,
      @NotNull Long version) {
  }

  /**`cuenta` ausente = la del buzón donde llegó; `asunto` ausente = «Re: » más el del correo; `marcarAtendido` en verdadero pasa el correo a ATENDIDO*/
  public record SolicitudRespuesta(@Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]*") String cuenta, @Size(max = 900) String asunto,
      @NotBlank @Size(max = 20_000) String cuerpo, Boolean marcarAtendido) {
  }

  /**un correo nuevo, que no responde a nada*/
  public record SolicitudEnvio(@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+") String cuenta, @NotBlank @Email @Size(max = 254) String para,
      @NotBlank @Size(max = 900) String asunto, @NotBlank @Size(max = 20_000) String cuerpo) {
  }

  /**
   *los NUEVO sin atender, para el distintivo del menú y los filtros; `enCurso` son los que alguien ya tomó. Ninguno de los tres cuenta la
   *PUBLICIDAD: esa va aparte en `publicidad` (la que está sin atender: NUEVO o EN_CURSO)
   */
  public record Contadores(long nuevos, long enCurso, long publicidad, Map<String, Long> nuevosPorCuenta, Map<String, Long> nuevosPorTipo) {
  }

  /**cuántos correos se revisaron y a cuántos les cambió el tipo*/
  public record ResultadoReclasificacion(int revisados, int cambiaron) {
  }

  /**cuántos correos de PUBLICIDAD pasaron a DESCARTADO*/
  public record ResultadoDescarte(int descartados) {
  }

  /**cómo va la lectura del buzón de una cuenta; `error` es un texto corto sin datos de la cuenta*/
  public record EstadoCuenta(String cuenta, boolean sincronizable, Instant ultimaPasada, int nuevosUltimaPasada, String error) {
  }

  /**`enCurso` verdadero: otra revisión estaba corriendo y esta no hizo nada*/
  public record ResultadoSincronizacion(boolean enCurso, List<ResultadoCuenta> cuentas) {
  }

  public record ResultadoCuenta(String cuenta, int nuevos, String error) {
  }
}
