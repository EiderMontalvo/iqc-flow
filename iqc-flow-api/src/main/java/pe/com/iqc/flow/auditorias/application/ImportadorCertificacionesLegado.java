package pe.com.iqc.flow.auditorias.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.auditorias.ImportacionCertificaciones;
import pe.com.iqc.flow.auditorias.domain.Auditoria;
import pe.com.iqc.flow.auditorias.domain.Certificado;
import pe.com.iqc.flow.auditorias.domain.Expediente;
import pe.com.iqc.flow.auditorias.domain.HistorialExpediente;
import pe.com.iqc.flow.auditorias.domain.NormaExpediente;
import pe.com.iqc.flow.auditorias.domain.QrLegado;
import pe.com.iqc.flow.auditorias.domain.TipoAuditoria;
import pe.com.iqc.flow.auditorias.domain.TipoServicio;
import pe.com.iqc.flow.auditorias.infrastructure.AuditoriaRepository;
import pe.com.iqc.flow.auditorias.infrastructure.CertificadoRepository;
import pe.com.iqc.flow.auditorias.infrastructure.ExpedienteRepository;
import pe.com.iqc.flow.auditorias.infrastructure.HistorialExpedienteRepository;
import pe.com.iqc.flow.auditorias.infrastructure.PropiedadesExpedientes;
import pe.com.iqc.flow.auditorias.infrastructure.QrLegadoRepository;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.bitacora.OrigenOperacion;
import pe.com.iqc.flow.compartido.bitacora.TipoActor;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas;
import pe.com.iqc.flow.seguimientos.ProgramadorTareas.NuevaTareaAutomatica;

/**crea expedientes históricos con sus certificados, QR y vigilancias a partir de datos de un sistema anterior*/
@Service
class ImportadorCertificacionesLegado implements ImportacionCertificaciones {
  private final ExpedienteRepository expedientes;
  private final CertificadoRepository certificados;
  private final AuditoriaRepository auditorias;
  private final HistorialExpedienteRepository historial;
  private final QrLegadoRepository qrs;
  private final ServicioCatalogos catalogos;
  private final ProgramadorTareas programador;
  private final PropiedadesExpedientes propiedades;
  private final Bitacora bitacora;
  private final Clock reloj;

  ImportadorCertificacionesLegado(ExpedienteRepository expedientes, CertificadoRepository certificados,
      AuditoriaRepository auditorias, HistorialExpedienteRepository historial, QrLegadoRepository qrs,
      ServicioCatalogos catalogos, ProgramadorTareas programador, PropiedadesExpedientes propiedades,
      Bitacora bitacora, Clock reloj) {
    this.expedientes = expedientes;
    this.certificados = certificados;
    this.auditorias = auditorias;
    this.historial = historial;
    this.qrs = qrs;
    this.catalogos = catalogos;
    this.programador = programador;
    this.propiedades = propiedades;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Override
  @Transactional
  public ResultadoExpediente importar(ExpedienteLegado x) {
    Optional<Expediente> existente = expedientes.findByOrganizacionIdAndFuenteLegadoAndIdLegado(x.organizacionId(),
        x.fuente(), x.idLegado());
    if (existente.isPresent()) return new ResultadoExpediente(existente.get().getId(), false, 0, 0, 0, 0);
    if (catalogos.esquema(x.esquema()).isEmpty()) {
      throw new ReglaNegocioException("ESQUEMA_INVALIDO", "Esquema desconocido: " + x.esquema());
    }
    Map<String, NormaExpediente> normas = new LinkedHashMap<>();
    for (CertificadoLegado c : x.certificados()) {
      if (catalogos.buscarEdicion(c.norma(), c.edicion()).isEmpty()) {
        throw new ReglaNegocioException("NORMA_INVALIDA", "Norma fuera del catálogo: " + c.norma() + ":" + c.edicion());
      }
      normas.putIfAbsent(c.norma(), new NormaExpediente(c.norma(), c.edicion()));
    }
    Instant ahora = Instant.now(reloj);
    Expediente e = expedientes.saveAndFlush(Expediente.historico(x.organizacionId(), x.clienteId(), x.esquema(),
        TipoServicio.valueOf(x.tipoServicio()), normas.values(), recortar(x.alcance(), 4000), x.fechaCertificacion(),
        x.fechaExpiracion(), x.responsableId(), x.fuente(), x.idLegado(), ahora));
    historial.save(new HistorialExpediente(e.getId(), null, e.getEstado(),
        "Importado de la plataforma anterior de certificaciones", null, ahora));
    int totalQr = 0;
    for (CertificadoLegado c : x.certificados()) {
      Certificado certificado = certificados.save(Certificado.historico(e, normas.get(c.norma()).edicion().equals(c.edicion())
              ? normas.get(c.norma()) : new NormaExpediente(c.norma(), c.edicion()),
          c.codigo(), Certificado.Estado.valueOf(c.estado()), c.revisionAlcance(), c.ciclo(), c.anioDecision(),
          c.emitidoPorIndia(), recortar(c.alcance(), 4000), c.fechaOtorgamiento(), c.fechaEmision(), c.fechaExpiracion(),
          c.urlDocumento(), x.fuente(), c.idLegado()));
      for (QrLegado qr : c.qrs() == null ? List.<QrLegado>of() : c.qrs()) {
        if (qrs.existsById(qr.encriptacionId())) continue;
        qrs.save(new pe.com.iqc.flow.auditorias.domain.QrLegado(qr.encriptacionId(), qr.idQr(), x.organizacionId(),
            certificado.getId(), qr.activo(), qr.registradoEn(), ahora));
        totalQr++;
      }
    }
    int vigilancias = 0;
    int tareas = 0;
    LocalDate hoy = LocalDate.now(reloj);
    LocalDate[] limites = {x.limiteVigilancia1(), x.limiteVigilancia2()};
    for (int i = 0; i < limites.length; i++) {
      if (limites[i] == null) continue;
      auditorias.save(new Auditoria(e.getId(), i == 0 ? TipoAuditoria.VIGILANCIA_1 : TipoAuditoria.VIGILANCIA_2, limites[i]));
      vigilancias++;
      // Solo las futuras generan tareas: las vencidas quedan visibles en la vista de vigilancias vencidas
      if (x.programarTareas() && !limites[i].isBefore(hoy)) {
        programador.crearTareaAutomatica(new NuevaTareaAutomatica(x.organizacionId(),
            "Programar " + (i + 1) + "ª vigilancia de " + e.getCodigo(),
            "La " + (i + 1) + "ª vigilancia debe realizarse antes del " + limites[i] + " (certificación importada).",
            "PROGRAMAR_AUDITORIA", ServicioExpedientes.ENTIDAD, e.getId(), x.clienteId(), x.responsableId(),
            limites[i].minusDays(propiedades.diasAnticipacionVigilancia()),
            "expediente:" + e.getId() + ":vigilancia:" + (i + 1)));
        tareas++;
      }
    }
    certificados.flush();
    bitacora.registrar(EntradaBitacora.accion("EXPEDIENTE_IMPORTADO").entidad(ServicioExpedientes.ENTIDAD, e.getId())
        .organizacion(x.organizacionId()).actor(null, TipoActor.SISTEMA).origen(OrigenOperacion.MIGRACION)
        .estadoNuevo(Map.of("fuente", x.fuente(), "idLegado", x.idLegado(), "esquema", x.esquema(),
            "certificados", x.certificados().stream().map(CertificadoLegado::codigo).toList(),
            "vigilancias", vigilancias, "qr", totalQr)));
    return new ResultadoExpediente(e.getId(), true, x.certificados().size(), totalQr, vigilancias, tareas);
  }

  private static String recortar(String texto, int maximo) {
    if (texto == null || texto.isBlank()) return null;
    String limpio = texto.trim();
    return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
  }
}
