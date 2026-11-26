package pe.com.iqc.flow.cartas.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 *iqc.cartas: comando de LibreOffice, tiempo máximo de cada conversión y datos legales de la empresa que van en las
 *cartas (razón social, RUC, dirección fiscal), la ciudad que se propone en la fecha y el membrete de respaldo para
 *cuando la marca del país aún no tiene cargados su dirección, teléfono o correo (Marcas y portal)
 */
@ConfigurationProperties("iqc.cartas")
public record PropiedadesCartas(@DefaultValue("soffice") String soffice, @DefaultValue("60s") Duration tiempoMaximo,
    @DefaultValue("Lima") String ciudad, @DefaultValue Empresa empresa, @DefaultValue Membrete membrete) {

  public record Empresa(@DefaultValue("IQC PERÚ S.A.C.") String razonSocial, @DefaultValue("20604476691") String ruc,
      @DefaultValue("Av. Arequipa Nro. 2450 Int. 805, Lince – Lima") String direccionFiscal) {
  }

  public record Membrete(@DefaultValue("JR. ABANCAY NRO. 425 URB. SAN CARLOS (TERCER PISO, OFICINA 301) JUNÍN – HUANCAYO – PERÚ") String direccion,
      @DefaultValue("+51 980 923 270") String telefono, @DefaultValue("administracion@iqcperu.com") String correo) {
  }
}
