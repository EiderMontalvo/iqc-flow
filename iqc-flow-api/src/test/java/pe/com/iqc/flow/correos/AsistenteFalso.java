package pe.com.iqc.flow.correos;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**un asistente de IA programable que anota cada llamada y lo que recibió: así se prueba la bandeja sin hablar con ningún proveedor*/
public class AsistenteFalso implements AsistenteCorreos {
  public static final Consumo CONSUMO = new Consumo("modelo-falso", 120, 45);

  public volatile boolean disponible = true;
  public final List<String> llamadas = new CopyOnWriteArrayList<>();
  public volatile Optional<TipoCorreo> tipo = Optional.of(TipoCorreo.RECLAMO);
  public volatile Optional<Resumen> resumen = Optional.empty();
  public volatile Optional<Borrador> borrador = Optional.empty();
  public volatile Function<List<CorreoContexto>, Consulta> consulta = null;
  public volatile CorreoContexto ultimoCorreo;
  public volatile String ultimasInstrucciones;
  public volatile Tono ultimoTono;
  public volatile List<CorreoContexto> ultimoContexto;

  public void restablecer() {
    disponible = true;
    llamadas.clear();
    tipo = Optional.of(TipoCorreo.RECLAMO);
    resumen = Optional.of(new Resumen("Pide cotización de ISO 9001.", List.of("Quiere certificarse", "Tiene dos sedes"), "Responder con la propuesta", TipoCorreo.COTIZACION, CONSUMO));
    borrador = Optional.of(new Borrador("Re: Solicitud", "Estimado cliente:\n\nGracias por escribirnos.\n\nIQC Perú", CONSUMO));
    consulta = null;
    ultimoCorreo = null; ultimasInstrucciones = null; ultimoTono = null; ultimoContexto = null;
  }

  @Override public boolean disponible() { return disponible; }

  @Override public Optional<TipoCorreo> clasificar(String asunto, String texto) { llamadas.add("clasificar"); return tipo; }

  @Override public Optional<Resumen> resumir(String asunto, String texto) { llamadas.add("resumir"); return resumen; }

  @Override
  public Optional<Borrador> redactarRespuesta(CorreoContexto correo, String instrucciones, Tono tono) {
    llamadas.add("redactar");
    ultimoCorreo = correo; ultimasInstrucciones = instrucciones; ultimoTono = tono;
    return borrador;
  }

  @Override
  public Optional<Consulta> consultar(String pregunta, List<CorreoContexto> correos) {
    llamadas.add("consultar");
    ultimoContexto = correos;
    return consulta == null ? Optional.empty() : Optional.of(consulta.apply(correos));
  }
}
