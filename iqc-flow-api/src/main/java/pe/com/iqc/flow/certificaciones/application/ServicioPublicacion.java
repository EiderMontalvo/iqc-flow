package pe.com.iqc.flow.certificaciones.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.certificaciones.application.PublicadorCertificados.ResultadoPublicacion;
import pe.com.iqc.flow.certificaciones.infrastructure.BaseConsultas;
import pe.com.iqc.flow.certificaciones.infrastructure.PublicacionCertificadoRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;

/**estado de la publicación a la consulta pública y ejecución manual desde el panel*/
@Service
public class ServicioPublicacion {
  private final PublicadorCertificados publicador;
  private final PublicacionCertificadoRepository publicaciones;
  private final BaseConsultas base;
  private final Bitacora bitacora;

  public ServicioPublicacion(PublicadorCertificados publicador, PublicacionCertificadoRepository publicaciones,
      BaseConsultas base, Bitacora bitacora) {
    this.publicador = publicador;
    this.publicaciones = publicaciones;
    this.base = base;
    this.bitacora = bitacora;
  }

  public record ErrorPublicacion(UUID certificadoId, int intentos, String error, Instant ultimoIntentoEn) {
  }

  public record EstadoPublicacion(boolean habilitada, long publicados, List<ErrorPublicacion> errores,
      ResultadoPublicacion ultimaPasada) {
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public EstadoPublicacion estado() {
    List<ErrorPublicacion> errores = publicaciones.findByIntentosFallidosGreaterThanOrderByUltimoIntentoEnDesc(0).stream()
        .limit(50)
        .map(p -> new ErrorPublicacion(p.getCertificadoId(), p.getIntentosFallidos(), p.getUltimoError(), p.getUltimoIntentoEn()))
        .toList();
    return new EstadoPublicacion(base.habilitada(), publicaciones.countByHuellaIsNotNull(), errores,
        publicador.ultima().orElse(null));
  }

  @PreAuthorize("hasAuthority('certificados:gestionar')")
  public ResultadoPublicacion ejecutar(boolean forzar) {
    ResultadoPublicacion r = publicador.publicar(forzar);
    bitacora.registrar(EntradaBitacora.accion("PUBLICACION_EJECUTADA").entidad("PUBLICACION", "consultas")
        .estadoNuevo(Map.of("forzar", forzar, "publicados", r.publicados(), "fallidos", r.fallidos(),
            "ejecutada", r.ejecutada())));
    return r;
  }
}
