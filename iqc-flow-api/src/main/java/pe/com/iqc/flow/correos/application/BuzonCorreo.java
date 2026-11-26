package pe.com.iqc.flow.correos.application;

import java.time.Instant;
import java.util.List;

/**
 *el buzón de una cuenta visto solo para leer: el sincronizador pide lo nuevo y no puede marcar, mover ni borrar nada. Es un puerto
 *para probar la sincronización con un buzón falso; la implementación real habla IMAP
 */
public interface BuzonCorreo {
  /**
   *los mensajes de la bandeja de entrada de la cuenta, del más antiguo al más nuevo, hasta `maximo`. Si el servidor sigue con el mismo
   *`uidValidity` que se conocía, solo los de UID mayor a `ultimoUid`; si cambió (o no se conocía), los recibidos desde `desde`
   */
  Lectura leerNuevos(String cuenta, SolicitudLectura solicitud);

  record SolicitudLectura(Long uidValidityConocido, long ultimoUid, Instant desde, int maximo) {
  }

  /**
   *hayMas: quedaron mensajes por traer en la próxima pasada; uidSiguiente: el UID que tendrá el próximo mensaje que llegue, leído
   *antes de listar (así, si no hay más, todo lo anterior ya se vio)
   */
  record Lectura(long uidValidity, long uidSiguiente, List<CorreoCrudo> correos, boolean hayMas) {
  }

  /**el mensaje original (.eml); si era enorme, solo su encabezado y `soloEncabezado` verdadero*/
  record CorreoCrudo(long uid, byte[] eml, boolean soloEncabezado) {
  }
}
