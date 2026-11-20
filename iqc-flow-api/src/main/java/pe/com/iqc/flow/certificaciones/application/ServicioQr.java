package pe.com.iqc.flow.certificaciones.application;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.CertificadosPublicables;
import pe.com.iqc.flow.auditorias.CertificadosPublicables.CertificadoPublicable;
import pe.com.iqc.flow.certificaciones.MarcasPais;
import pe.com.iqc.flow.certificaciones.MarcasPais.MarcaVigente;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.documentos.ServicioDocumentos;

/**
 *QR de cada certificado: apunta a la dirección de consulta de su marca. Los certificados importados conservan el
 *identificador de su QR ya impreso; los nuevos usan su token de verificación (aleatorio, no adivinable)
 */
@Service
public class ServicioQr {
  private final CertificadosPublicables certificados;
  private final ConsultaClientes clientes;
  private final MarcasPais marcas;
  private final ServicioDocumentos documentos;
  private final ActorActual actorActual;

  public ServicioQr(CertificadosPublicables certificados, ConsultaClientes clientes, MarcasPais marcas,
      ServicioDocumentos documentos, ActorActual actorActual) {
    this.certificados = certificados;
    this.clientes = clientes;
    this.marcas = marcas;
    this.documentos = documentos;
    this.actorActual = actorActual;
  }

  public record EnlaceConsulta(UUID certificadoId, String codigo, String token, String url, String paisMarca,
      String marca) {
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public EnlaceConsulta enlace(UUID certificadoId) {
    UUID org = actorActual.requerido().organizacionId();
    CertificadoPublicable c = cargar(org, certificadoId);
    MarcaVigente marca = marca(org, c);
    return new EnlaceConsulta(c.id(), c.codigo(), c.tokenQr(), marca.urlVerificacion(c.tokenQr()), marca.codigoPais(),
        marca.nombre());
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('expedientes:leer')")
  public byte[] png(UUID certificadoId, int tamano, boolean conLogo) {
    UUID org = actorActual.requerido().organizacionId();
    CertificadoPublicable c = cargar(org, certificadoId);
    MarcaVigente marca = marca(org, c);
    byte[] logo = conLogo && marca.logoDocumentoId() != null ? documentos.leerContenido(org, marca.logoDocumentoId()) : null;
    return GeneradorQr.png(marca.urlVerificacion(c.tokenQr()), tamano, logo);
  }

  /**marca elegida a mano o, si no, la del país del cliente*/
  MarcaVigente marca(UUID org, CertificadoPublicable c) {
    String pais = c.paisMarca() != null ? c.paisMarca()
        : clientes.buscar(org, c.clienteId()).map(cli -> cli.codigoPais()).orElse(null);
    return marcas.marca(org, pais);
  }

  private CertificadoPublicable cargar(UUID org, UUID certificadoId) {
    return certificados.buscar(org, certificadoId).orElseThrow(() -> new ReglaNegocioException("CERTIFICADO_NO_EMITIDO",
        "El QR se genera cuando el certificado está emitido."));
  }
}
