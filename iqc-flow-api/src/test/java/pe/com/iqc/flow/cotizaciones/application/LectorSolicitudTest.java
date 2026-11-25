package pe.com.iqc.flow.cotizaciones.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import jakarta.activation.DataHandler;

/**lectura de correos guardados (.eml) y detección del contacto, con datos ficticios y sin red*/
class LectorSolicitudTest {

  @Test
  void leeCorreoConCuerpoAlternativoYPdfAdjunto() throws Exception {
    MimeMessage m = new MimeMessage(Session.getInstance(new Properties()));
    m.setFrom(new InternetAddress("ana.rios@ejemplo.pe", "Ana Ríos", "UTF-8"));
    m.setRecipients(MimeMessage.RecipientType.TO, "comercial@ejemplo.com");
    m.setSubject("Cotización ISO 37001", "UTF-8");
    m.setSentDate(new Date(1_795_000_000_000L));

    MimeMultipart alternativa = new MimeMultipart("alternative");
    MimeBodyPart plano = new MimeBodyPart();
    plano.setText("Buenos días, somos 450 trabajadores en 3 sedes.", "UTF-8");
    MimeBodyPart html = new MimeBodyPart();
    html.setContent("<p>Buenos días, <b>HTML</b></p>", "text/html; charset=UTF-8");
    alternativa.addBodyPart(plano);
    alternativa.addBodyPart(html);
    MimeBodyPart cuerpo = new MimeBodyPart();
    cuerpo.setContent(alternativa);

    MimeBodyPart pdf = new MimeBodyPart();
    pdf.setDataHandler(new DataHandler(new ByteArrayDataSource(pdf("Normas: ISO 9001:2015 RUC 20123456789"), "application/pdf")));
    pdf.setFileName("requerimiento.pdf");

    MimeMultipart mixto = new MimeMultipart("mixed");
    mixto.addBodyPart(cuerpo);
    mixto.addBodyPart(pdf);
    m.setContent(mixto);
    m.saveChanges();
    ByteArrayOutputStream eml = new ByteArrayOutputStream();
    m.writeTo(eml);

    byte[] datos = eml.toByteArray();
    assertThat(LectorSolicitud.esCorreo(datos)).isTrue();
    String t = LectorSolicitud.texto(datos);
    assertThat(t).startsWith("Correo de: Ana Ríos <ana.rios@ejemplo.pe>\nFecha: ");
    assertThat(t).contains("Asunto: Cotización ISO 37001", "450 trabajadores en 3 sedes", "Adjunto «requerimiento.pdf»:", "ISO 9001:2015");
    assertThat(t).doesNotContain("HTML");

    var r = AnalisisLocalSolicitud.analizar(t);
    assertThat(r.contactoNombre()).isEqualTo("Ana Ríos");
    assertThat(r.correo()).isEqualTo("ana.rios@ejemplo.pe");
    assertThat(r.ruc()).isEqualTo("20123456789");
    assertThat(r.normas()).extracting(AnalisisLocalSolicitud.NormaLeida::numero).containsExactly("37001", "9001");
  }

  @Test
  void enUnReenvioElContactoEsElRemitenteOriginal() {
    String t = """
        Correo de: Persona Comercial <comercial@ejemplo.com>
        Asunto: Fwd: Solicitud de cotización

        ---------- Forwarded message ---------
        De: Luis Paredes <lparedes@cliente.pe>
        Fecha: lun, 23 nov 2026
        Necesitamos la ISO 14001.""";
    var r = AnalisisLocalSolicitud.analizar(t);
    assertThat(r.contactoNombre()).isEqualTo("Luis Paredes");
    assertThat(r.correo()).isEqualTo("lparedes@cliente.pe");
  }

  @Test
  void sinCabecerasTomaElContactoDelTextoODeLaFirma() {
    assertThat(AnalisisLocalSolicitud.analizar("Queremos la ISO 9001. Contacto: María de la Cruz, 912 345 678").contactoNombre())
        .isEqualTo("María de la Cruz");
    assertThat(AnalisisLocalSolicitud.analizar("Queremos la ISO 9001.\n\nSaludos cordiales,\nJorge Quispe Mamani\nGerente").contactoNombre())
        .isEqualTo("Jorge Quispe Mamani");
  }

  @Test
  void elTextoPegadoNoSeConfundeConUnCorreo() {
    byte[] pegado = "De: Ana\nPara: IQC\nAsunto: hola\n\nQueremos cotizar".getBytes(StandardCharsets.UTF_8);
    assertThat(LectorSolicitud.esCorreo(pegado)).isFalse();
    assertThat(LectorSolicitud.texto(pegado)).startsWith("De: Ana");
  }

  private static byte[] pdf(String linea) throws Exception {
    try (PDDocument d = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage p = new PDPage();
      d.addPage(p);
      try (PDPageContentStream c = new PDPageContentStream(d, p)) {
        c.beginText();
        c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        c.newLineAtOffset(50, 700);
        c.showText(linea);
        c.endText();
      }
      d.save(out);
      return out.toByteArray();
    }
  }
}
