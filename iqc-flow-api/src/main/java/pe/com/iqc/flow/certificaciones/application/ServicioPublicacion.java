package pe.com.iqc.flow.certificaciones.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.certificaciones.application.PublicadorCertificados.ResultadoPublicacion;
import pe.com.iqc.flow.certificaciones.infrastructure.BasesConsultas;
import pe.com.iqc.flow.certificaciones.infrastructure.PublicacionCertificadoRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;

/**estado de la publicación a la consulta pública y ejecución manual desde el panel*/
@Service
public class ServicioPublicacion {
  private final PublicadorCertificados publicador;
  private final PublicacionCertificadoRepository publicaciones;
  private final BasesConsultas bases;
  private final Bitacora bitacora;

  public ServicioPublicacion(PublicadorCertificados publicador, PublicacionCertificadoRepository publicaciones,
      BasesConsultas bases, Bitacora bitacora) {
    this.publicador = publicador;
    this.publicaciones = publicaciones;
    this.bases = bases;
    this.bitacora = bitacora;
  }

  public record ErrorPublicacion(UUID certificadoId, int intentos, String error, Instant ultimoIntentoEn) {
  }

  /**paises: los que tienen base de consulta configurada; sus certificados son los únicos que se publican*/
  public record EstadoPublicacion(boolean habilitada, Set<String> paises, long publicados, List<ErrorPublicacion> errores,
      ResultadoPublicacion ultimaPasada) {
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyAuthority('expedientes:leer', 'certificados:leer')")
  public EstadoPublicacion estado() {
    List<ErrorPublicacion> errores = publicaciones.findByIntentosFallidosGreaterThanOrderByUltimoIntentoEnDesc(0).stream()
        .limit(50)
        .map(p -> new ErrorPublicacion(p.getCertificadoId(), p.getIntentosFallidos(), p.getUltimoError(), p.getUltimoIntentoEn()))
        .toList();
    return new EstadoPublicacion(bases.habilitada(), bases.paises(), publicaciones.countByHuellaIsNotNull(), errores,
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
