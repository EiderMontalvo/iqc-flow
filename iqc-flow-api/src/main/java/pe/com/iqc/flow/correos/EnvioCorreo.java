package pe.com.iqc.flow.correos;

import java.util.List;
import java.util.Objects;

/**API pública del correo saliente: los demás módulos piden enviar un mensaje con una cuenta y no saben nada de SMTP ni de contraseñas*/
public interface EnvioCorreo {
  /**
   *envía el mensaje en texto plano con la cuenta indicada y devuelve su Message-ID (para encadenar respuestas); lanza {@code CORREO_CUENTA_DESCONOCIDA} si no existe,
   *{@code CORREO_NO_CONFIGURADO} si el servidor no tiene su usuario y clave, y {@code CORREO_NO_ENVIADO} (sin datos de la
   *cuenta) si el servidor de correo lo rechaza o no responde
   */
  String enviar(String cuenta, Mensaje mensaje);

  /**las cuentas que conoce el servidor y si tienen credenciales, sin sus contraseñas*/
  List<Estado> estados();

  /**`enRespuestaA` y `referencias` (Message-ID con sus <>) solo si responde a un correo: así el cliente lo ve en el mismo hilo*/
  record Mensaje(String para, String asunto, String texto, String enRespuestaA, String referencias) {
    public Mensaje {
      Objects.requireNonNull(para, "para");
      Objects.requireNonNull(asunto, "asunto");
      Objects.requireNonNull(texto, "texto");
    }

    public Mensaje(String para, String asunto, String texto) { this(para, asunto, texto, null, null); }
  }

  /**remitente y servidor solo si hay credenciales; nunca la contraseña*/
  record Estado(String cuenta, boolean configurado, String remitente, String servidor) {
  }
}
