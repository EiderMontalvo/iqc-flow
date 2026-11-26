package pe.com.iqc.flow.correos.infrastructure;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Folder;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import org.springframework.stereotype.Component;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.correos.application.BuzonCorreo;
import pe.com.iqc.flow.correos.infrastructure.PropiedadesCorreo.Cuenta;

/**
 *lee la bandeja de entrada por IMAP sobre SSL, SOLO LECTURA: abre INBOX en modo de solo lectura y baja los mensajes con BODY.PEEK, así
 *que no se marcan como leídos; no borra ni mueve nada. Verifica el certificado del servidor. Un mensaje de más de 10 MB se baja
 *solo con su encabezado
 */
@Component
class BuzonImap implements BuzonCorreo {
  private static final int TIEMPO_MS = 20_000;
  private static final int MENSAJE_MAXIMO = 10 * 1024 * 1024;

  private final PropiedadesCorreo propiedades;

  BuzonImap(PropiedadesCorreo propiedades) { this.propiedades = propiedades; }

  @Override
  public Lectura leerNuevos(String nombre, SolicitudLectura s) {
    Cuenta cuenta = propiedades.cuentas().get(nombre);
    if (cuenta == null || !cuenta.sincronizable()) throw new ReglaNegocioException("CORREO_NO_CONFIGURADO", "La cuenta «" + nombre + "» no se puede leer.");
    try (Store almacen = sesion().getStore("imaps")) {
      almacen.connect(cuenta.imapHost(), cuenta.imapPuerto(), cuenta.usuario(), cuenta.clave());
      Folder bandeja = almacen.getFolder("INBOX");
      bandeja.open(Folder.READ_ONLY);
      try {
        UIDFolder uids = (UIDFolder) bandeja;
        long validez = uids.getUIDValidity();
        long siguiente = uids.getUIDNext();
        boolean mismoServidor = s.uidValidityConocido() != null && s.uidValidityConocido() == validez;
        Message[] candidatos = mismoServidor
            ? uids.getMessagesByUID(s.ultimoUid() + 1, UIDFolder.LASTUID) // «n:*» siempre incluye el último, aunque sea menor a n
            : bandeja.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(s.desde())));
        List<Message> nuevos = new ArrayList<>();
        for (Message m : candidatos) if (!mismoServidor || uids.getUID(m) > s.ultimoUid()) nuevos.add(m);
        nuevos.sort((a, b) -> Long.compare(uid(uids, a), uid(uids, b)));
        List<CorreoCrudo> correos = new ArrayList<>();
        for (Message m : nuevos.subList(0, Math.min(nuevos.size(), s.maximo()))) correos.add(descargar(uids.getUID(m), m));
        return new Lectura(validez, siguiente, correos, nuevos.size() > s.maximo());
      } finally {
        bandeja.close(false);
      }
    } catch (AuthenticationFailedException e) {
      throw new ReglaNegocioException("CORREO_NO_LEIDO", "El servidor de correo rechazó el usuario o la contraseña.");
    } catch (MessagingException e) {
      throw new ReglaNegocioException("CORREO_NO_LEIDO", "No se pudo leer el buzón: el servidor de correo no respondió.");
    }
  }

  private static long uid(UIDFolder carpeta, Message m) {
    try {
      return carpeta.getUID(m);
    } catch (MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static CorreoCrudo descargar(long uid, Message mensaje) throws MessagingException {
    if (mensaje.getSize() > MENSAJE_MAXIMO) return new CorreoCrudo(uid, soloEncabezado(mensaje), true);
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      mensaje.writeTo(bytes);
      return new CorreoCrudo(uid, bytes.toByteArray(), false);
    } catch (IOException e) {
      throw new MessagingException("No se pudo descargar el mensaje", e);
    }
  }

  private static byte[] soloEncabezado(Message mensaje) throws MessagingException {
    StringBuilder encabezado = new StringBuilder();
    Enumeration<Header> lineas = mensaje.getAllHeaders();
    while (lineas.hasMoreElements()) {
      Header h = lineas.nextElement();
      encabezado.append(h.getName()).append(": ").append(h.getValue().replaceAll("[\\r\\n]+", " ")).append("\r\n");
    }
    return encabezado.append("\r\n").toString().getBytes(StandardCharsets.UTF_8);
  }

  private static Session sesion() {
    Properties p = new Properties();
    p.put("mail.imaps.ssl.enable", "true");
    p.put("mail.imaps.ssl.checkserveridentity", "true");
    p.put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3");
    p.put("mail.imaps.peek", "true");
    p.put("mail.imaps.connectiontimeout", String.valueOf(TIEMPO_MS));
    p.put("mail.imaps.timeout", String.valueOf(TIEMPO_MS * 3));
    p.put("mail.imaps.writetimeout", String.valueOf(TIEMPO_MS));
    return Session.getInstance(p);
  }
}
