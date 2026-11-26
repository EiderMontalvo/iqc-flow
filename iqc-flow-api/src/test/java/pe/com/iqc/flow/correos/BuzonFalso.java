package pe.com.iqc.flow.correos;

import static java.util.Comparator.comparingLong;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.application.BuzonCorreo;

/**un buzón en memoria que cumple el mismo contrato que el IMAP real, para probar la sincronización sin conectarse a nada*/
public class BuzonFalso implements BuzonCorreo {
  private final Map<String, List<CorreoCrudo>> mensajes = new HashMap<>();
  private final Map<String, Long> validez = new HashMap<>();
  private final Map<String, Long> ultimoUid = new HashMap<>();
  private final Set<String> conFallas = new HashSet<>();

  public synchronized void vaciar() {
    mensajes.clear(); validez.clear(); ultimoUid.clear(); conFallas.clear();
  }

  public synchronized void agregar(String cuenta, byte[] eml) { agregar(cuenta, eml, false); }

  public synchronized void agregar(String cuenta, byte[] eml, boolean soloEncabezado) {
    long uid = ultimoUid.merge(cuenta, 1L, Long::sum);
    mensajes.computeIfAbsent(cuenta, c -> new ArrayList<>()).add(new CorreoCrudo(uid, eml, soloEncabezado));
  }

  /**el servidor renumera todo (cambia el UIDVALIDITY): los mismos mensajes con UID nuevos*/
  public synchronized void renumerar(String cuenta) {
    validez.merge(cuenta, 1L, (a, b) -> a + 1);
    List<CorreoCrudo> antes = mensajes.getOrDefault(cuenta, List.of());
    List<CorreoCrudo> despues = new ArrayList<>();
    long uid = 1000;
    for (CorreoCrudo c : antes) despues.add(new CorreoCrudo(uid++, c.eml(), c.soloEncabezado()));
    mensajes.put(cuenta, despues);
    ultimoUid.put(cuenta, uid - 1);
  }

  public synchronized void fallar(String cuenta) { conFallas.add(cuenta); }

  public synchronized void repararFallas() { conFallas.clear(); }

  @Override
  public synchronized Lectura leerNuevos(String cuenta, SolicitudLectura s) {
    if (conFallas.contains(cuenta)) throw new ReglaNegocioException("CORREO_NO_LEIDO", "El servidor de correo rechazó el usuario o la contraseña.");
    long actual = validez.getOrDefault(cuenta, 1L);
    boolean mismoServidor = s.uidValidityConocido() != null && s.uidValidityConocido() == actual;
    List<CorreoCrudo> todos = mensajes.getOrDefault(cuenta, List.of());
    List<CorreoCrudo> candidatos = todos.stream().filter(c -> !mismoServidor || c.uid() > s.ultimoUid()).sorted(comparingLong(CorreoCrudo::uid)).toList();
    long siguiente = todos.stream().mapToLong(CorreoCrudo::uid).max().orElse(0) + 1;
    return new Lectura(actual, siguiente, candidatos.subList(0, Math.min(candidatos.size(), s.maximo())), candidatos.size() > s.maximo());
  }
}
