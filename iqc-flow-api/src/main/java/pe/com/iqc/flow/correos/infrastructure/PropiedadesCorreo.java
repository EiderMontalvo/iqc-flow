package pe.com.iqc.flow.correos.infrastructure;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 *cuentas de correo con nombre (iqc.correo.cuentas.&lt;nombre&gt;.*) y cómo se revisan los buzones (iqc.correo.sincronizacion.*). Los
 *servidores y puertos (SMTP para enviar, IMAP para leer) van en application.yml; el usuario y la clave de cada cuenta solo vienen
 *de variables de entorno (IQC_CORREO_CUENTAS_&lt;NOMBRE&gt;_USUARIO y _CLAVE), nunca del código ni del repositorio. Una cuenta sin
 *credenciales existe pero queda apagada
 */
@ConfigurationProperties("iqc.correo")
public record PropiedadesCorreo(Map<String, Cuenta> cuentas, @DefaultValue Sincronizacion sincronizacion, @DefaultValue Asistente asistente) {
  public PropiedadesCorreo {
    Map<String, Cuenta> porNombre = new TreeMap<>();
    if (cuentas != null) cuentas.forEach((nombre, cuenta) -> porNombre.put(nombre.toLowerCase(Locale.ROOT), cuenta));
    cuentas = Collections.unmodifiableMap(porNombre); // ordenado por nombre: Map.copyOf lo perdería
    if (sincronizacion == null) sincronizacion = new Sincronizacion(true, Duration.ofMinutes(3), 14, 100, "IQC_PERU");
    if (asistente == null) asistente = new Asistente(10, 6_000);
  }

  public record Cuenta(String host, @DefaultValue("587") int puerto, String usuario, String clave, @DefaultValue("IQC Flow") String nombreRemitente,
      String imapHost, @DefaultValue("993") int imapPuerto) {
    public boolean configurado() { return usuario != null && !usuario.isBlank() && clave != null && !clave.isBlank(); }

    /**se puede leer su buzón: tiene credenciales y servidor IMAP*/
    public boolean sincronizable() { return configurado() && imapHost != null && !imapHost.isBlank(); }

    /**el registro imprimiría la clave en cualquier log o mensaje de error: aquí se oculta*/
    @Override
    public String toString() { return "Cuenta[host=" + host + ", puerto=" + puerto + ", usuario=" + usuario + ", clave=***]"; }
  }

  /**
   *habilitada: apaga o enciende la revisión programada (la manual sigue disponible); intervalo: cada cuánto; ventanaInicialDias: hasta
   *cuántos días atrás se importa la primera vez; maximoPorPasada: tope de mensajes por cuenta y pasada; organizacion: código de la
   *organización dueña de lo que llega a estos buzones
   */
  public record Sincronizacion(@DefaultValue("true") boolean habilitada, @DefaultValue("PT3M") Duration intervalo,
      @DefaultValue("14") int ventanaInicialDias, @DefaultValue("100") int maximoPorPasada, @DefaultValue("IQC_PERU") String organizacion) {
  }

  /**
   *límites del asistente de IA (iqc.correo.asistente.*): llamadasPorMinuto por persona, y textoMaximo: cuántos caracteres de un correo
   *salen hacia el proveedor (el resto no se envía)
   */
  public record Asistente(@DefaultValue("10") int llamadasPorMinuto, @DefaultValue("6000") int textoMaximo) {
  }
}
