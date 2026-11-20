package pe.com.iqc.flow.auditorias.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.CertificadosPublicables;
import pe.com.iqc.flow.auditorias.domain.Certificado;
import pe.com.iqc.flow.auditorias.domain.Certificado.Estado;
import pe.com.iqc.flow.auditorias.domain.QrLegado;
import pe.com.iqc.flow.auditorias.infrastructure.CertificadoRepository;
import pe.com.iqc.flow.auditorias.infrastructure.QrLegadoRepository;

@Service
class CertificadosPublicablesJpa implements CertificadosPublicables {
  /**los que se emitieron alguna vez; los borradores y revisados aún no existen para el público*/
  static final Set<Estado> PUBLICABLES =
      EnumSet.of(Estado.EMITIDO, Estado.SUSPENDIDO, Estado.RETIRADO, Estado.CANCELADO, Estado.REEMPLAZADO);

  private final CertificadoRepository certificados;
  private final QrLegadoRepository qrs;

  CertificadosPublicablesJpa(CertificadoRepository certificados, QrLegadoRepository qrs) {
    this.certificados = certificados;
    this.qrs = qrs;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CertificadoPublicable> listar() { return convertir(certificados.findByEstadoIn(PUBLICABLES)); }

  @Override
  @Transactional(readOnly = true)
  public Optional<CertificadoPublicable> buscar(UUID organizacionId, UUID certificadoId) {
    return certificados.findByIdAndOrganizacionId(certificadoId, organizacionId)
        .filter(c -> PUBLICABLES.contains(c.getEstado()))
        .map(c -> convertir(List.of(c)).get(0));
  }

  private List<CertificadoPublicable> convertir(Collection<Certificado> lista) {
    Map<UUID, List<String>> legados = lista.isEmpty() ? Map.of()
        : qrs.findByCertificadoIdInAndActivoTrue(lista.stream().map(Certificado::getId).toList()).stream()
            // el más reciente primero: es el que se vuelve a imprimir
            .sorted(Comparator.comparing(QrLegado::getRegistradoEn, Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
                .thenComparing(QrLegado::getEncriptacionId))
            .collect(Collectors.groupingBy(QrLegado::getCertificadoId,
                Collectors.mapping(QrLegado::getEncriptacionId, Collectors.toList())));
    return lista.stream().map(c -> new CertificadoPublicable(c.getId(), c.getOrganizacionId(), c.getClienteId(),
        c.getCodigo(), c.getEsquemaCodigo(), c.getNormaCodigo(), c.getEdicion(), c.getAlcance(), c.getEstado().name(),
        c.getFechaOtorgamiento(), c.getFechaEmision(), c.getFechaExpiracion(), c.getPaisMarca(),
        c.getTokenVerificacion(), legados.getOrDefault(c.getId(), List.of()), c.getDocumentoPdfId(),
        c.getUrlDocumentoLegado())).toList();
  }
}
