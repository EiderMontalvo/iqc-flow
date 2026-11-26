package pe.com.iqc.flow.correos.application;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.infrastructure.BloqueoDistribuido;
import pe.com.iqc.flow.correos.AsistenteCorreos;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;
import pe.com.iqc.flow.correos.TipoCorreo;
import pe.com.iqc.flow.correos.application.BuzonCorreo.CorreoCrudo;
import pe.com.iqc.flow.correos.application.BuzonCorreo.Lectura;
import pe.com.iqc.flow.correos.application.BuzonCorreo.SolicitudLectura;
import pe.com.iqc.flow.correos.application.DtosBandeja.ResultadoCuenta;
import pe.com.iqc.flow.correos.domain.ClasificadorCorreo;
import pe.com.iqc.flow.correos.domain.ClasificadorCorreo.Clasificacion;
import pe.com.iqc.flow.correos.domain.MensajeLeido;
import pe.com.iqc.flow.correos.domain.OrigenTipo;
import pe.com.iqc.flow.correos.infrastructure.AnalizadorMime;
import pe.com.iqc.flow.correos.infrastructure.PropiedadesCorreo;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos.EstadoSincronizacion;
import pe.com.iqc.flow.correos.infrastructure.RepositorioCorreos.NuevoCorreo;
import pe.com.iqc.flow.organizaciones.ServicioOrganizaciones;

/**
 *revisa los buzones y guarda lo nuevo: lee solo lo que llegó desde la última vez (por UID; la primera vez, la ventana de días
 *configurada), lo clasifica por reglas y lo liga al cliente si el remitente es uno de sus contactos. Una sola instancia a la vez
 *(bloqueo de PostgreSQL). Un fallo en una cuenta se anota y no frena a las demás ni a las próximas pasadas
 */
@Service
public class SincronizadorCorreos {
  private static final Logger LOG = LoggerFactory.getLogger(SincronizadorCorreos.class);
  private static final long BLOQUEO_SINCRONIZACION = 0x1C0F_0002L;
  private static final String TEXTO_ENORME = "(Mensaje muy grande: solo se guardó su encabezado. Ábrelo en el webmail para leerlo completo.)";

  private final BuzonCorreo buzon;
  private final RepositorioCorreos repositorio;
  private final ConsultaClientes clientes;
  private final ServicioOrganizaciones organizaciones;
  private final ObjectProvider<AsistenteCorreos> asistente;
  private final ParametrosOrganizacion parametros;
  private final BloqueoDistribuido bloqueo;
  private final PropiedadesCorreo propiedades;
  private final Clock reloj;

  public SincronizadorCorreos(BuzonCorreo buzon, RepositorioCorreos repositorio, ConsultaClientes clientes, ServicioOrganizaciones organizaciones,
      ObjectProvider<AsistenteCorreos> asistente, ParametrosOrganizacion parametros, BloqueoDistribuido bloqueo, PropiedadesCorreo propiedades, Clock reloj) {
    this.buzon = buzon;
    this.repositorio = repositorio;
    this.clientes = clientes;
    this.organizaciones = organizaciones;
    this.asistente = asistente;
    this.parametros = parametros;
    this.bloqueo = bloqueo;
    this.propiedades = propiedades;
    this.reloj = reloj;
  }

  /**una pasada por todas las cuentas con credenciales; vacío si otra instancia ya la estaba haciendo*/
  public Optional<List<ResultadoCuenta>> pasada() {
    return bloqueo.siLibre(BLOQUEO_SINCRONIZACION, this::revisarCuentas);
  }

  private List<ResultadoCuenta> revisarCuentas() {
    UUID organizacion = organizaciones.buscarPorCodigo(propiedades.sincronizacion().organizacion()).map(o -> o.id()).orElse(null);
    if (organizacion == null) {
      LOG.warn("No se revisan los buzones: no existe la organización «{}»", propiedades.sincronizacion().organizacion());
      return List.of();
    }
    List<ResultadoCuenta> resultados = new ArrayList<>();
    propiedades.cuentas().forEach((nombre, cuenta) -> {
      if (cuenta.sincronizable()) resultados.add(revisar(organizacion, nombre));
    });
    return resultados;
  }

  private ResultadoCuenta revisar(UUID organizacion, String cuenta) {
    Instant ahora = reloj.instant();
    try {
      EstadoSincronizacion estado = repositorio.estado(cuenta);
      var config = propiedades.sincronizacion();
      Lectura lectura = buzon.leerNuevos(cuenta, new SolicitudLectura(estado.uidValidity(), estado.ultimoUid(), ahora.minus(Duration.ofDays(config.ventanaInicialDias())),
          config.maximoPorPasada()));
      long ultimo = Objects.equals(estado.uidValidity(), lectura.uidValidity()) ? estado.ultimoUid() : 0;
      int nuevos = 0, ilegibles = 0;
      for (CorreoCrudo correo : lectura.correos()) {
        try {
          if (guardar(organizacion, cuenta, lectura.uidValidity(), correo, ahora)) nuevos++;
        } catch (IllegalArgumentException e) {
          ilegibles++; // un mensaje corrupto no debe trabar el buzón: se salta y se sigue
          LOG.warn("Un correo de la cuenta {} no se pudo leer ({})", cuenta, e.getClass().getSimpleName());
        }
        ultimo = Math.max(ultimo, correo.uid());
      }
      if (!lectura.hayMas()) ultimo = Math.max(ultimo, lectura.uidSiguiente() - 1); // lo anterior ya se vio: no se vuelve a pedir
      String aviso = ilegibles == 0 ? null : ilegibles + " mensaje(s) no se pudieron leer";
      repositorio.guardarPasada(cuenta, lectura.uidValidity(), ultimo, nuevos, aviso, ahora);
      return new ResultadoCuenta(cuenta, nuevos, aviso);
    } catch (RuntimeException e) {
      String error = e instanceof ReglaNegocioException ? e.getMessage() : "Error inesperado: revisa el registro del servidor.";
      LOG.warn("No se pudo revisar el buzón de la cuenta {} ({})", cuenta, e.getClass().getSimpleName());
      repositorio.guardarError(cuenta, error, ahora);
      return new ResultadoCuenta(cuenta, 0, error);
    }
  }

  private boolean guardar(UUID organizacion, String cuenta, long uidValidity, CorreoCrudo correo, Instant ahora) {
    MensajeLeido mensaje = AnalizadorMime.leer(correo.eml(), ahora);
    if (correo.soloEncabezado()) mensaje = mensaje.conTexto(TEXTO_ENORME, true);
    Optional<UUID> cliente = clientes.clientePorCorreoDeContacto(organizacion, mensaje.remitenteCorreo());
    Clasificacion clasificacion = ClasificadorCorreo.clasificar(new ClasificadorCorreo.Entrada(mensaje.asunto(), mensaje.texto(),
        mensaje.adjuntos().stream().map(MensajeLeido.Adjunto::nombre).toList(), cliente.isPresent(), mensaje.masivo(), mensaje.remitenteCorreo()));
    TipoCorreo tipo = clasificacion.tipo();
    OrigenTipo origen = OrigenTipo.REGLA;
    String motivo = clasificacion.motivo();
    // La IA solo mira lo que las reglas no supieron clasificar, y solo si la organización la encendió: si no, el correo no sale del servidor
    if (tipo == TipoCorreo.OTRO && parametros.activo(organizacion, ParametroOrganizacion.IA_HABILITADA)) {
      Optional<TipoCorreo> propuesto = asistente.getIfAvailable(() -> AsistenteCorreos.NINGUNO).clasificar(mensaje.asunto(), mensaje.texto());
      if (propuesto.isPresent()) { tipo = propuesto.get(); origen = OrigenTipo.IA; motivo = "Propuesto por el asistente"; }
    }
    return repositorio.guardar(new NuevoCorreo(organizacion, cuenta, uidValidity, correo.uid(), mensaje, correo.eml().length, tipo, origen, motivo, cliente.orElse(null)),
        correo.eml()).isPresent();
  }
}
