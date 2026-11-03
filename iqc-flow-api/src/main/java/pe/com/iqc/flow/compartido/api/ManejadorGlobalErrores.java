package pe.com.iqc.flow.compartido.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.bitacora.ResultadoOperacion;
import pe.com.iqc.flow.compartido.domain.ConflictoException;
import pe.com.iqc.flow.compartido.domain.ExcepcionNegocio;
import pe.com.iqc.flow.compartido.domain.LimiteExcedidoException;
import pe.com.iqc.flow.compartido.domain.OperacionNoPermitidaException;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.infrastructure.FiltroCorrelacion;

/**
 *convierte todos los errores en respuestas RFC 9457 (application/problem+json) con un "codigo"
 *estable y el "idCorrelacion" de la solicitud. También atiende los errores de Spring Security.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);
  private final Bitacora bitacora;

  public ManejadorGlobalErrores(Bitacora bitacora) { this.bitacora = bitacora; }

  @ExceptionHandler(RecursoNoEncontradoException.class)
  ResponseEntity<ProblemDetail> recursoNoEncontrado(RecursoNoEncontradoException ex) {
    return problema(HttpStatus.NOT_FOUND, ex);
  }

  @ExceptionHandler(ReglaNegocioException.class)
  ResponseEntity<ProblemDetail> reglaNegocio(ReglaNegocioException ex) {
    return problema(HttpStatus.UNPROCESSABLE_CONTENT, ex);
  }

  @ExceptionHandler(ConflictoException.class)
  ResponseEntity<ProblemDetail> conflicto(ConflictoException ex) { return problema(HttpStatus.CONFLICT, ex); }

  @ExceptionHandler(OperacionNoPermitidaException.class)
  ResponseEntity<ProblemDetail> operacionNoPermitida(OperacionNoPermitidaException ex) {
    return problema(HttpStatus.FORBIDDEN, ex);
  }

  @ExceptionHandler(LimiteExcedidoException.class)
  ResponseEntity<ProblemDetail> limiteExcedido(LimiteExcedidoException ex) {
    return problema(HttpStatus.TOO_MANY_REQUESTS, ex);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> versionDesactualizada(OptimisticLockingFailureException ex) {
    return problema(HttpStatus.CONFLICT, "CONFLICTO_VERSION",
        "El registro fue modificado por otro usuario. Recarga los datos y vuelve a intentarlo.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> integridadDatos(DataIntegrityViolationException ex) {
    log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
    return problema(HttpStatus.CONFLICT, "CONFLICTO_DATOS", "La operación entra en conflicto con datos existentes.");
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> noAutenticado(AuthenticationException ex) {
    if (ex instanceof InsufficientAuthenticationException || ex instanceof AuthenticationCredentialsNotFoundException) {
      return problema(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Se requiere iniciar sesión.");
    }
    return problema(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
        "Credenciales inválidas o cuenta bloqueada temporalmente.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> accesoDenegado(AccessDeniedException ex, HttpServletRequest solicitud) {
    if (ex instanceof CsrfException) {
      return problema(HttpStatus.FORBIDDEN, "CSRF_INVALIDO",
          "Token CSRF ausente o inválido. Obtén uno en GET /api/v1/autenticacion/csrf.");
    }
    try {
      bitacora.registrarAparte(EntradaBitacora.accion("ACCESO_DENEGADO")
          .entidad("ENDPOINT", solicitud.getMethod() + " " + solicitud.getRequestURI())
          .resultado(ResultadoOperacion.DENEGADO)
          .motivo(ex.getMessage()));
    } catch (RuntimeException errorBitacora) {
      log.error("No se pudo registrar el acceso denegado en la bitácora", errorBitacora);
    }
    return problema(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tienes permiso para realizar esta operación.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> errorNoControlado(Exception ex) {
    log.error("Error no controlado", ex);
    return problema(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
        "Ocurrió un error inesperado. Comunica el identificador de correlación al equipo técnico.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
      HttpHeaders cabeceras, HttpStatusCode estado, WebRequest solicitud) {
    ProblemDetail cuerpo = ex.getBody();
    cuerpo.setDetail("La solicitud contiene datos inválidos.");
    cuerpo.setProperty("errores", ex.getBindingResult().getFieldErrors().stream()
        .map(error -> Map.of("campo", error.getField(), "mensaje", Objects.toString(error.getDefaultMessage(), "inválido")))
        .toList());
    completar(cuerpo, "VALIDACION");
    return handleExceptionInternal(ex, cuerpo, cabeceras, estado, solicitud);
  }

  /**completa los errores que resuelve Spring MVC (400, 404, 405, 415…) con código y correlación*/
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object cuerpo, HttpHeaders cabeceras,
      HttpStatusCode estado, WebRequest solicitud) {
    if (cuerpo instanceof ProblemDetail problema
        && (problema.getProperties() == null || !problema.getProperties().containsKey("codigo"))) {
      completar(problema, codigoPorEstado(estado));
    }
    return super.handleExceptionInternal(ex, cuerpo, cabeceras, estado, solicitud);
  }

  private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, ExcepcionNegocio ex) {
    return problema(estado, ex.codigo(), ex.getMessage());
  }

  private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String codigo, String detalle) {
    ProblemDetail cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
    completar(cuerpo, codigo);
    return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
  }

  private static void completar(ProblemDetail cuerpo, String codigo) {
    cuerpo.setProperty("codigo", codigo);
    String correlacion = MDC.get(FiltroCorrelacion.CLAVE_MDC);
    if (correlacion != null) cuerpo.setProperty("idCorrelacion", correlacion);
  }

  private static String codigoPorEstado(HttpStatusCode estado) {
    return switch (estado.value()) {
      case 400 -> "SOLICITUD_INVALIDA";
      case 404 -> "RECURSO_NO_ENCONTRADO";
      case 405 -> "METODO_NO_PERMITIDO";
      case 406 -> "FORMATO_NO_ACEPTABLE";
      case 415 -> "TIPO_CONTENIDO_NO_SOPORTADO";
      default -> "ERROR_" + estado.value();
    };
  }
}
