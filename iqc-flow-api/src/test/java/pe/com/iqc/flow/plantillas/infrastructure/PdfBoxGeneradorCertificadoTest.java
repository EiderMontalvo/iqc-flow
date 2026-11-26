package pe.com.iqc.flow.plantillas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.Acreditadora;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.CertificadoEmpresarial;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.CertificadoIqcGlobal;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.CertificadoPersonal;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.Firmante;

class PdfBoxGeneradorCertificadoTest {
  /**los PDF de muestra quedan en target/ para revisarlos a ojo contra los originales de plantillas/*/
  private static final Path MUESTRAS = Path.of("target", "muestras-certificados");

  private final PdfBoxGeneradorCertificado generador = new PdfBoxGeneradorCertificado(new RecursosCertificado());
  private final Firmante firmante = new Firmante("Lic.", "Fiorella Tenorio Alfaro", "Gerente General", "IQC PERU S.A.C.", true);

  @Test
  void empresarialSae() throws IOException {
    byte[] pdf = generador.empresarial(empresarial(Acreditadora.SAE, "SAE CSGAS 24-001", ALCANCE_CORTO));
    guardar("empresarial-sae.pdf", pdf);
    String texto = textoDe(pdf, 1);
    assertThat(texto).contains("CERTIFICADO", "En reconocimiento al Sistema de Gestión Antisoborno de:",
        "GRUPO DE DESARROLLO TELECOMUNICACIONES S.A.C.", "ISO 37001:2025", "Certificado No.: PE-SAE-03820260111A",
        "Ciclo de certificación Nro. 1", "03 de Agosto del 2026", "*02 de Agosto del 2027", "**02 de Agosto del 2029",
        "Acreditación N° SAE CSGAS 24-001", "GESTIÓN ANTISOBORNO", "Fiorella Tenorio Alfaro", "03/08/2026 – V.01");
    assertThat(texto).doesNotContain("{{").doesNotContain("?");
  }

  @Test
  void empresarialInacalSinNumeroDeAcreditacionNiFirma() throws IOException {
    CertificadoEmpresarial base = empresarial(Acreditadora.INACAL, null, ALCANCE_CORTO);
    byte[] pdf = generador.empresarial(new CertificadoEmpresarial(base.razonSocial(), base.direccion(), base.sistemaGestion(),
        base.norma(), base.alcance(), base.codigo(), 2, base.fechaOtorgamiento(), base.fechaEmision(), base.seguimiento1(),
        null, base.fechaExpiracion(), Acreditadora.INACAL, null, 2, base.urlVerificacion(), List.of(), null,
        new Firmante(null, "Fiorella Tenorio Alfaro", "Gerente General", "IQC PERU S.A.C.", false)));
    guardar("empresarial-inacal.pdf", pdf);
    String texto = textoDe(pdf, 1);
    assertThat(texto).contains("Ciclo de certificación Nro. 2", "– V.02").doesNotContain("Acreditación N°", "Seguimiento 2");
  }

  @Test
  void empresarialConAlcanceMuyLargoSigueEnUnaPagina() throws IOException {
    byte[] pdf = generador.empresarial(empresarial(Acreditadora.SAE, "SAE CSA 24-001", ALCANCE_CORTO.repeat(30)));
    guardar("empresarial-alcance-largo.pdf", pdf);
    assertThat(textoDe(pdf, 1)).contains("Alcance:");
  }

  @Test
  void personal() throws IOException {
    byte[] pdf = generador.personal(new CertificadoPersonal("Ana Lucía Torres Vega", "DNI", "12345678",
        "Curso de auditor líder en base a la 17024:2012", "ISO 27701", "2025", "En relación con la NTP-ISO/IEC 27701:2026",
        "Seguridad de la Información, Ciberseguridad y Protección de la Privacidad\nSistema de Gestión de la Información sobre "
            + "Privacidad\nCurso de interpretación y auditor líder", new BigDecimal("40.00"), "En línea",
        LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 19), "Carlos Mendoza Ríos", "IQC/PE/IIP045",
        "https://consultas.iqcperu.com/", null, firmante));
    guardar("personal.pdf", pdf);
    String texto = textoDe(pdf, 1);
    assertThat(texto).contains("CERTIFICADO", "CURSO DE AUDITOR LÍDER EN BASE A LA 17024:2012", "Ana Lucía Torres Vega",
        "DNI 12345678", "ISO 27701:2025", "40 horas", "En línea", "05 al 11 de julio de 2026", "Entrenador: Carlos Mendoza Ríos",
        "Fecha de emisión: 19 de julio de 2026", "Código de verificación: IQC/PE/IIP045", "Lic. Fiorella Tenorio Alfaro",
        "https://consultas.iqcperu.com/", "ha completado con éxito el curso de auditor líder en",
        "Seguridad de la Información, Ciberseguridad", "y Protección de la Privacidad",
        "Sistema de Gestión de la Información sobre Privacidad", "Curso de interpretación y auditor líder");
    assertThat(texto).doesNotContain("{{");
  }

  @Test
  void caracteresQueLaFuenteNoTieneNoRompenElPdf() throws IOException {
    byte[] pdf = generador.personal(new CertificadoPersonal("Łukasz Ñandú 😀", null, null, "Curso", null, null, null, null,
        new BigDecimal("1"), null, null, null, LocalDate.of(2026, 1, 2), null, "X-1", "https://consultas.iqcperu.com/", null,
        firmante));
    assertThat(textoDe(pdf, 1)).contains("Ñandú").contains("1 hora");
  }

  @Test
  void iqcGlobalUsaElFondoDeCadaFamiliaDeNorma() throws IOException {
    for (String[] n : new String[][] {{"ISO_9001", "ISO 9001:2015", "Sistemas de Gestión de la Calidad", "9001"},
        {"ISO_14001", "ISO 14001:2015", "Sistemas de Gestión Ambiental", "14001"},
        {"ISO_45001", "ISO 45001:2018", "Sistemas de Gestión de la Seguridad y Salud en el Trabajo", "45001"},
        {"ISO_37001", "ISO 37001:2025", "Sistemas de Gestión Antisoborno", "generico"}}) {
      byte[] pdf = generador.iqcGlobal(global(n[0], n[1], n[2], "TRANSPORTES DEL SUR S.A.C.", ALCANCE_GLOBAL));
      guardar("global-" + n[3] + ".pdf", pdf);
      String texto = textoDe(pdf, 1);
      assertThat(texto).contains("INTEGRATED QUALITY CERTIFICATION PRIVATE LIMITED", "TRANSPORTES DEL SUR S.A.C.", n[1], n[2],
          "ha sido evaluado y cumple con los", "Alcance:", "Servicios de transporte y distribución", "Número de certificado",
          ": PER/QEO/JAS-C0000/0001", "Fecha de emisión inicial", ": 17.06.2026", ": 16.06.2029", "Suma Shankar", "Director",
          "Procesos no aplicables", ": NA", "https://consultas.iqcperu.com/", "QF-12 Rev 01 date 28.10.2025");
      assertThat(texto).doesNotContain("{{").doesNotContain("?");
    }
  }

  @Test
  void ningunTextoSaleDelMarcoConLosNombresMasLargosDelCatalogo() throws IOException {
    String sistema = "Sistema de Gestión de la Seguridad y Salud en el Trabajo";
    String razon = "CORPORACIÓN DE INGENIERÍA, CONSTRUCCIÓN Y SERVICIOS GENERALES DEL SUR SOCIEDAD ANÓNIMA CERRADA - CISGSUR S.A.C.";
    String direccion = "AV. EJEMPLO NRO. 123 INT. 301 URB. CERCADO DE LIMA (ALTURA CUADRA 10 DEL JR. DE LA UNIÓN) LIMA - LIMA - LIMA";
    CertificadoEmpresarial b = empresarial(Acreditadora.SAE, "SAE CSGAS 24-001", ALCANCE_CORTO);
    byte[] empresarial = generador.empresarial(new CertificadoEmpresarial(razon, direccion, sistema, "ISO 45001:2018", b.alcance(),
        b.codigo(), 1, b.fechaOtorgamiento(), b.fechaEmision(), b.seguimiento1(), b.seguimiento2(), b.fechaExpiracion(),
        Acreditadora.SAE, null, 1, b.urlVerificacion(), List.of(), null, firmante));
    guardar("empresarial-nombres-largos.pdf", empresarial);
    // el titular, su dirección y el sistema de gestión van centrados dentro de las líneas del marco (x 62 a 533 pt); el
    // bloque llega hasta antes de «Alcance:» (el alcance se justifica en su propio ancho)
    assertThat(extremos(empresarial, 0, 300)).satisfies(e -> {
      assertThat(e.izquierda()).isGreaterThanOrEqualTo(MARCO_IZQUIERDA);
      assertThat(e.derecha()).isLessThanOrEqualTo(MARCO_DERECHA);
    });
    assertThat(extremos(empresarial, 600, 700)).satisfies(e -> assertThat(e.izquierda()).isGreaterThanOrEqualTo(MARCO_IZQUIERDA));
    assertThat(textoDe(empresarial, 1)).contains("En reconocimiento al Sistema de Gestión de la Seguridad", "Salud en el Trabajo", "CISGSUR S.A.C.");

    byte[] global = generador.iqcGlobal(global("ISO_45001", "ISO 45001:2018", "Sistemas de Gestión de la Seguridad y Salud en el Trabajo",
        razon, ALCANCE_GLOBAL.repeat(6)));
    guardar("global-nombres-largos.pdf", global);
    assertThat(extremos(global, 0, 560)).satisfies(e -> {
      assertThat(e.izquierda()).isGreaterThanOrEqualTo(GLOBAL_IZQUIERDA);
      assertThat(e.derecha()).isLessThanOrEqualTo(GLOBAL_DERECHA);
    });
    assertThat(textoDe(global, 1)).contains("CISGSUR S.A.C.", "Número de certificado");
  }

  private static final float MARCO_IZQUIERDA = 62f, MARCO_DERECHA = 533f, GLOBAL_IZQUIERDA = 90f, GLOBAL_DERECHA = 580f;
  private static final String ALCANCE_GLOBAL = "Servicios de transporte y distribución de carga para clientes industriales. ";

  private CertificadoIqcGlobal global(String normaCodigo, String norma, String sistemas, String razonSocial, String alcance) {
    return new CertificadoIqcGlobal(razonSocial, "AV. EJEMPLO MZA. A LOTE. 10 URB. LAS FLORES LIMA - LIMA – LURIN", sistemas,
        normaCodigo, norma, alcance, "PER/QEO/JAS-C0000/0001", LocalDate.of(2026, 6, 17), LocalDate.of(2027, 6, 16),
        LocalDate.of(2028, 6, 16), LocalDate.of(2029, 6, 16), "46", "4610", "8.3", null, "https://consultas.iqcperu.com/");
  }

  /**el texto más a la izquierda y más a la derecha de las líneas cuya base está entre dos alturas de la página*/
  private static Extremos extremos(byte[] pdf, float desdeY, float hastaY) throws IOException {
    float[] e = {Float.MAX_VALUE, 0};
    try (PDDocument documento = Loader.loadPDF(pdf)) {
      new PDFTextStripper() {
        @Override
        protected void writeString(String texto, List<TextPosition> letras) {
          for (TextPosition t : letras) {
            // las siglas giradas de la franja lateral (QMS, EMS…) vienen en el fondo y no cuentan
            if (t.getUnicode().isBlank() || t.getDir() != 0 || t.getYDirAdj() < desdeY || t.getYDirAdj() > hastaY) continue;
            e[0] = Math.min(e[0], t.getXDirAdj());
            e[1] = Math.max(e[1], t.getXDirAdj() + t.getWidthDirAdj());
          }
        }
      }.getText(documento);
    }
    return new Extremos(e[0], e[1]);
  }

  private record Extremos(float izquierda, float derecha) {
  }

  @Test
  void rangoDeFechasSegunMesYAnio() {
    assertThat(FormatoFechas.rango(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 11))).isEqualTo("05 al 11 de julio de 2026");
    assertThat(FormatoFechas.rango(LocalDate.of(2026, 6, 28), LocalDate.of(2026, 7, 2))).isEqualTo("28 de junio al 02 de julio de 2026");
    assertThat(FormatoFechas.rango(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 2)))
        .isEqualTo("28 de diciembre de 2026 al 02 de enero de 2027");
    assertThat(FormatoFechas.rango(null, null)).isNull();
  }

  private static final String ALCANCE_CORTO = "Elaboración de estudios de pre inversión, expedientes técnicos, estudios definitivos, "
      + "supervisión de expedientes técnicos y supervisión de obras y servicios de: seguridad ciudadana, telecomunicaciones, "
      + "tecnologías de la información y comunicación que incluyen comunicaciones, fibra óptica, red de datos y conectividad. ";

  private CertificadoEmpresarial empresarial(Acreditadora acreditadora, String numeroAcreditacion, String alcance) {
    return new CertificadoEmpresarial("GRUPO DE DESARROLLO TELECOMUNICACIONES S.A.C.",
        "JR. CANTA NRO. 141 URB. SAN FELIPE LIMA - LIMA - COMAS", "Sistema de Gestión Antisoborno", "ISO 37001:2025", alcance,
        "PE-SAE-03820260111A", 1, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3), LocalDate.of(2027, 8, 2),
        LocalDate.of(2028, 8, 2), LocalDate.of(2029, 8, 2), acreditadora, numeroAcreditacion, 1, "https://consultas.iqcperu.com/",
        List.of("Av. Arequipa nro. 2450 Int. 805 (Edificio El Dorado) - Lima", "Jirón Abancay 425 Oficina 301 - Huancayo"),
        "+51 923 980 697 | comercial@iqcperu.net", firmante);
  }

  private static void guardar(String nombre, byte[] pdf) throws IOException {
    Files.createDirectories(MUESTRAS);
    Files.write(MUESTRAS.resolve(nombre), pdf);
  }

  private static String textoDe(byte[] pdf, int paginasEsperadas) throws IOException {
    try (PDDocument documento = Loader.loadPDF(pdf)) {
      assertThat(documento.getNumberOfPages()).isEqualTo(paginasEsperadas);
      return new PDFTextStripper().getText(documento);
    }
  }
}
