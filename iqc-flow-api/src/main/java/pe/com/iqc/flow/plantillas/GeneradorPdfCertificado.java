package pe.com.iqc.flow.plantillas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 *API pública del generador de PDF de certificados. Cada método devuelve un PDF de una página; los textos opcionales
 *nulos o vacíos simplemente no se imprimen
 */
public interface GeneradorPdfCertificado {
  /**certificado de sistema de gestión con el diseño DDC-FRM-015 (INACAL y SAE; IQC Global lo emite India)*/
  byte[] empresarial(CertificadoEmpresarial datos);

  /**certificado de curso o capacitación de una persona*/
  byte[] personal(CertificadoPersonal datos);

  /**certificado de IQC Global (India) con el diseño QF-12; lo firma siempre el Director de IQC Global*/
  byte[] iqcGlobal(CertificadoIqcGlobal datos);

  enum Acreditadora { SAE, INACAL }

  /**quien firma: imprime la imagen de la firma solo si {@code conFirma} (certificado firmado y emitido)*/
  record Firmante(String titulo, String nombre, String cargo, String empresa, boolean conFirma) {
    public Firmante {
      Objects.requireNonNull(nombre, "nombre");
      Objects.requireNonNull(cargo, "cargo");
    }
  }

  /**
   *sistemaGestion: «Sistema de Gestión Antisoborno»; norma: «ISO 37001:2025»; los seguimientos y la expiración vienen
   *calculados por el sistema; numeroAcreditacion nulo omite esa línea; ubicaciones y contacto van en el pie
   */
  record CertificadoEmpresarial(String razonSocial, String direccion, String sistemaGestion, String norma, String alcance,
      String codigo, int ciclo, LocalDate fechaOtorgamiento, LocalDate fechaEmision, LocalDate seguimiento1,
      LocalDate seguimiento2, LocalDate fechaExpiracion, Acreditadora acreditadora, String numeroAcreditacion,
      int numeroVersion, String urlVerificacion, List<String> ubicaciones, String contacto, Firmante firmante) {
    public CertificadoEmpresarial {
      Objects.requireNonNull(razonSocial, "razonSocial");
      Objects.requireNonNull(sistemaGestion, "sistemaGestion");
      Objects.requireNonNull(norma, "norma");
      Objects.requireNonNull(codigo, "codigo");
      Objects.requireNonNull(fechaOtorgamiento, "fechaOtorgamiento");
      Objects.requireNonNull(fechaEmision, "fechaEmision");
      Objects.requireNonNull(fechaExpiracion, "fechaExpiracion");
      Objects.requireNonNull(acreditadora, "acreditadora");
      Objects.requireNonNull(firmante, "firmante");
      ubicaciones = ubicaciones == null ? List.of() : List.copyOf(ubicaciones);
    }
  }

  /**
   *curso: línea bajo «CERTIFICADO»; normaReferencia y normaAnio: el título grande («ISO 27701» y «2025»); subtitulo: la
   *línea azul bajo el título; descripcion: el tema del curso. logo: PNG del logo de la marca para la franja azul oscuro,
   *o nulo para el de IQC Perú
   */
  record CertificadoPersonal(String nombres, String tipoDocumento, String numeroDocumento, String curso,
      String normaReferencia, String normaAnio, String subtitulo, String descripcion, BigDecimal duracionHoras,
      String modalidad, LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaEmision, String entrenador,
      String codigo, String urlConsulta, byte[] logo, Firmante firmante) {
    public CertificadoPersonal {
      Objects.requireNonNull(nombres, "nombres");
      Objects.requireNonNull(curso, "curso");
      Objects.requireNonNull(fechaEmision, "fechaEmision");
      Objects.requireNonNull(codigo, "codigo");
      Objects.requireNonNull(urlConsulta, "urlConsulta");
      Objects.requireNonNull(firmante, "firmante");
    }
  }

  /**
   *sistemasGestion: «Sistemas de Gestión de la Calidad» (el título del catálogo, en plural); normaCodigo: «ISO_9001», que
   *elige el fondo (9001, 14001 y 45001 tienen el suyo y las demás normas, uno genérico); norma: «ISO 9001:2015»;
   *codigo: «PER/QEO/JAS-C0000/0001». Subdivisión, clase, procesos no aplicables y anexos son opcionales: sin dato, los dos
   *últimos salen «NA» y los otros dos con un guion. Los seguimientos y la expiración vienen calculados por el sistema
   */
  record CertificadoIqcGlobal(String razonSocial, String direccion, String sistemasGestion, String normaCodigo, String norma,
      String alcance, String codigo, LocalDate fechaEmision, LocalDate seguimiento1, LocalDate seguimiento2,
      LocalDate fechaExpiracion, String subdivision, String clase, String procesosNoAplicables, String anexos,
      String urlVerificacion) {
    public CertificadoIqcGlobal {
      Objects.requireNonNull(razonSocial, "razonSocial");
      Objects.requireNonNull(sistemasGestion, "sistemasGestion");
      Objects.requireNonNull(normaCodigo, "normaCodigo");
      Objects.requireNonNull(norma, "norma");
      Objects.requireNonNull(codigo, "codigo");
      Objects.requireNonNull(fechaEmision, "fechaEmision");
      Objects.requireNonNull(fechaExpiracion, "fechaExpiracion");
    }
  }
}
