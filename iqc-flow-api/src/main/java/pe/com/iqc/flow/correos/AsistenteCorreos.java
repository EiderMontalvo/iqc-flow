package pe.com.iqc.flow.correos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 *lo que la IA hace con los correos cuando se enciende (parámetro IA_HABILITADA y clave del proveedor en el servidor). El contenido de los
 *correos es de terceros y no es confiable: el implementador lo trata como datos, nunca como instrucciones, y solo devuelve texto: ninguna
 *de estas operaciones envía, cambia ni borra nada. Quien la implemente (módulo `ia`) la expone como bean. Salvo `consumo`, los métodos
 *devuelven vacío si el proveedor no respondió o se negó, y lanzan {@code IA_LIMITE} si el proveedor limitó el uso
 */
public interface AsistenteCorreos {
  /**sin IA: no opina de nada*/
  AsistenteCorreos NINGUNO = new AsistenteCorreos() {
    @Override public boolean disponible() { return false; }
    @Override public Optional<TipoCorreo> clasificar(String asunto, String texto) { return Optional.empty(); }
    @Override public Optional<Resumen> resumir(String asunto, String texto) { return Optional.empty(); }
    @Override public Optional<Borrador> redactarRespuesta(CorreoContexto correo, String instrucciones, Tono tono) { return Optional.empty(); }
    @Override public Optional<Consulta> consultar(String pregunta, List<CorreoContexto> correos) { return Optional.empty(); }
  };

  enum Tono { FORMAL, CERCANO }

  /**qué modelo respondió y cuánto se gastó: para la bitácora y el control de costo, nunca el contenido*/
  record Consumo(String modelo, long tokensEntrada, long tokensSalida) {
  }

  /**un correo tal como se le muestra al modelo: ya recortado, sin cabeceras técnicas ni adjuntos*/
  record CorreoContexto(String id, String remitente, String asunto, Instant fecha, String texto) {
  }

  record Resumen(String resumen, List<String> puntosClave, String accionSugerida, TipoCorreo tipoSugerido, Consumo consumo) {
  }

  record Borrador(String asunto, String cuerpo, Consumo consumo) {
  }

  /**`idsUsados`: los ids de los correos del contexto en los que se apoyó la respuesta*/
  record Consulta(String respuesta, List<String> idsUsados, Consumo consumo) {
  }

  /**hay proveedor configurado (clave); no dice si la organización la tiene encendida: eso lo decide el parámetro IA_HABILITADA*/
  boolean disponible();

  /**el tipo del correo cuando las reglas no se deciden (se pide solo si el clasificador devolvió OTRO). Vacío también si el modelo dice OTRO*/
  Optional<TipoCorreo> clasificar(String asunto, String texto);

  Optional<Resumen> resumir(String asunto, String texto);

  /**borrador de respuesta al correo, que una persona revisa antes de enviar; `instrucciones` son de quien redacta (no del correo)*/
  Optional<Borrador> redactarRespuesta(CorreoContexto correo, String instrucciones, Tono tono);

  /**responde la pregunta solo con lo que dicen los correos del contexto, y dice cuáles usó*/
  Optional<Consulta> consultar(String pregunta, List<CorreoContexto> correos);
}
