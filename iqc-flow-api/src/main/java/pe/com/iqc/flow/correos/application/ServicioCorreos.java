package pe.com.iqc.flow.correos.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.EnvioCorreo;
import pe.com.iqc.flow.correos.EnvioCorreo.Estado;
import pe.com.iqc.flow.correos.EnvioCorreo.Mensaje;

/**configuración del correo para quien administra: ver qué cuenta usa el servidor y mandar un correo de prueba*/
@Service
public class ServicioCorreos {
  /**evita usar la prueba para mandar muchos correos seguidos a un tercero*/
  private static final Duration ESPERA_ENTRE_PRUEBAS = Duration.ofSeconds(10);

  private final EnvioCorreo envio;
  private final Bitacora bitacora;
  private final Map<String, Instant> ultimaPrueba = new ConcurrentHashMap<>();

  public ServicioCorreos(EnvioCorreo envio, Bitacora bitacora) {
    this.envio = envio;
    this.bitacora = bitacora;
  }

  @PreAuthorize("hasAuthority('correos:configurar')")
  public List<Estado> estados() { return envio.estados(); }

  @PreAuthorize("hasAuthority('correos:configurar')")
  public void enviarPrueba(String cuenta, String para) {
    Instant ahora = Instant.now();
    Instant anterior = ultimaPrueba.put(cuenta, ahora);
    if (anterior != null && Duration.between(anterior, ahora).compareTo(ESPERA_ENTRE_PRUEBAS) < 0) {
      ultimaPrueba.put(cuenta, anterior);
      throw new ReglaNegocioException("DEMASIADAS_PRUEBAS", "Espera unos segundos antes de mandar otra prueba.");
    }
    try {
      envio.enviar(cuenta, new Mensaje(para, "Prueba de correo de IQC Flow",
          "Este es un correo de prueba enviado desde IQC Flow.\n\nSi lo recibiste, el envío de correos está bien configurado. Puedes borrarlo."));
    } catch (RuntimeException e) {
      if (anterior == null) ultimaPrueba.remove(cuenta); else ultimaPrueba.put(cuenta, anterior);
      throw e;
    }
    bitacora.registrar(EntradaBitacora.accion("CORREO_PRUEBA_ENVIADO").entidad("CORREO", cuenta)
        .estadoNuevo(Map.of("cuenta", cuenta, "destino", enmascarar(para))));
  }

  /**la bitácora guarda el correo enmascarado, como el de los contactos: «a***@dominio.com»*/
  static String enmascarar(String correo) {
    int arroba = correo.indexOf('@');
    return arroba <= 0 ? "***" : correo.charAt(0) + "***" + correo.substring(arroba);
  }
}
