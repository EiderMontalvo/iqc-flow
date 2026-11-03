package pe.com.iqc.flow.identidad.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *política de contraseñas: longitud antes que reglas de composición (criterio NIST SP 800-63B).
 *el máximo de 72 bytes es el límite de bcrypt.
 */
public final class PoliticaContrasena {
  public static final int LONGITUD_MINIMA = 12;
  private static final int BYTES_MAXIMOS = 72;

  private PoliticaContrasena() {
  }

  public static void validar(String contrasena, String correo) {
    if (contrasena == null || contrasena.codePointCount(0, contrasena.length()) < LONGITUD_MINIMA) {
      throw new ReglaNegocioException("CONTRASENA_DEBIL",
          "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
    }
    if (contrasena.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS) {
      throw new ReglaNegocioException("CONTRASENA_DEMASIADO_LARGA", "La contraseña no puede superar 72 bytes.");
    }
    if (contrasena.codePoints().distinct().count() < 4) {
      throw new ReglaNegocioException("CONTRASENA_DEBIL", "La contraseña es demasiado repetitiva.");
    }
    String usuarioCorreo = Usuario.normalizarCorreo(correo).split("@")[0];
    if (usuarioCorreo.length() >= 4 && contrasena.toLowerCase(Locale.ROOT).contains(usuarioCorreo)) {
      throw new ReglaNegocioException("CONTRASENA_DEBIL", "La contraseña no debe contener tu usuario de correo.");
    }
  }
}
