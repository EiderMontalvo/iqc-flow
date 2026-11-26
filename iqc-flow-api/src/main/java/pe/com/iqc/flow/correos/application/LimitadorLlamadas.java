package pe.com.iqc.flow.correos.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *cuántas veces por minuto puede una persona pedirle algo a la IA: cada llamada cuesta dinero y un clic repetido no debe vaciar la cuota.
 *shortcut: la cuenta vive en la memoria de esta instancia (con varias, cada una cuenta aparte); si se necesita un tope global, a la base
 */
final class LimitadorLlamadas {
  private static final Duration VENTANA = Duration.ofMinutes(1);

  private final int maximo;
  private final Clock reloj;
  private final Map<UUID, Deque<Instant>> llamadas = new ConcurrentHashMap<>();

  LimitadorLlamadas(int maximoPorMinuto, Clock reloj) {
    this.maximo = maximoPorMinuto;
    this.reloj = reloj;
  }

  /**anota la llamada y devuelve true, o false si ya llegó al tope en el último minuto*/
  boolean permitir(UUID persona) {
    Instant ahora = reloj.instant();
    Deque<Instant> mias = llamadas.computeIfAbsent(persona, k -> new ArrayDeque<>());
    synchronized (mias) {
      while (!mias.isEmpty() && mias.peekFirst().isBefore(ahora.minus(VENTANA))) mias.pollFirst();
      if (mias.size() >= maximo) return false;
      mias.addLast(ahora);
      return true;
    }
  }
}
