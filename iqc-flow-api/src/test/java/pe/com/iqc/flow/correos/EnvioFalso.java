package pe.com.iqc.flow.correos;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**guarda lo que se «envía» en vez de mandarlo; conoce solo las cuentas administracion y comercial*/
public class EnvioFalso implements EnvioCorreo {
  public record Enviado(String cuenta, Mensaje mensaje, String messageId) {
  }

  private final List<Enviado> enviados = new CopyOnWriteArrayList<>();
  private final AtomicInteger contador = new AtomicInteger();

  public List<Enviado> enviados() { return enviados; }

  public void vaciar() { enviados.clear(); }

  @Override
  public String enviar(String cuenta, Mensaje mensaje) {
    if (!Set.of("administracion", "comercial").contains(cuenta)) {
      throw new ReglaNegocioException("CORREO_CUENTA_DESCONOCIDA", "No existe la cuenta de correo «" + cuenta + "».");
    }
    String id = "<envio-" + contador.incrementAndGet() + "@prueba.iqcperu.net>";
    enviados.add(new Enviado(cuenta, mensaje, id));
    return id;
  }

  @Override
  public List<Estado> estados() { return List.of(); }
}
