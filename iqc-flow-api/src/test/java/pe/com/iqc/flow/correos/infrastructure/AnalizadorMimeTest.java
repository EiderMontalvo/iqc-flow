package pe.com.iqc.flow.correos.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.correos.CorreosDePrueba;
import pe.com.iqc.flow.correos.CorreosDePrueba.Adjunto;
import pe.com.iqc.flow.correos.domain.MensajeLeido;

class AnalizadorMimeTest {
  private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");

  private static MensajeLeido leer(byte[] eml) { return AnalizadorMime.leer(eml, AHORA); }

  @Test
  void leeEncabezadosYTextoConAcentos() {
    MensajeLeido m = leer(CorreosDePrueba.correo("Ana Pérez <Ana.Perez@MineraAndina.pe>", "Cotización ISO 9001 – ñandú", "Buenos días,\nNecesitamos cotizar la certificación.", "<m1@mineraandina.pe>"));
    assertThat(m.remitenteCorreo()).isEqualTo("ana.perez@mineraandina.pe");
    assertThat(m.remitenteNombre()).isEqualTo("Ana Pérez");
    assertThat(m.asunto()).isEqualTo("Cotización ISO 9001 – ñandú");
    assertThat(m.texto()).contains("Buenos días").contains("cotizar la certificación");
    assertThat(m.messageId()).isEqualTo("<m1@mineraandina.pe>");
    assertThat(m.para()).isEqualTo("administracion@iqcperu.net");
    assertThat(m.adjuntos()).isEmpty();
    assertThat(m.textoTruncado()).isFalse();
  }

  @Test
  void losAdjuntosSonDatosYSeDescargaElContenidoPorIndice() {
    byte[] pdf = "%PDF-1.4 contenido de prueba".getBytes(StandardCharsets.ISO_8859_1);
    byte[] eml = CorreosDePrueba.correo("a@b.pe", "Solicitud", "Adjunto la solicitud.", "<m2@b.pe>", null, null,
        new Adjunto("Solicitud ñ.pdf", "application/pdf", pdf), new Adjunto("firma.png", "image/png", new byte[] {1, 2, 3}));
    MensajeLeido m = leer(eml);
    assertThat(m.texto()).contains("Adjunto la solicitud");
    assertThat(m.adjuntos()).extracting(MensajeLeido.Adjunto::nombre).containsExactly("Solicitud ñ.pdf", "firma.png");
    assertThat(m.adjuntos().get(0).tipo()).isEqualTo("application/pdf");
    assertThat(m.adjuntos().get(0).tamano()).isEqualTo(pdf.length);
    var descargado = AnalizadorMime.adjunto(eml, 0);
    assertThat(descargado.nombre()).isEqualTo("Solicitud ñ.pdf");
    assertThat(descargado.contenido()).isEqualTo(pdf);
    assertThat(AnalizadorMime.adjunto(eml, 1).contenido()).containsExactly(1, 2, 3);
  }

  @Test
  void unAdjuntoQueNoExisteSeRechaza() {
    byte[] eml = CorreosDePrueba.correo("a@b.pe", "x", "x", null);
    assertThatThrownBy(() -> AnalizadorMime.adjunto(eml, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AnalizadorMime.adjunto(eml, -1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unCorreoSoloHtmlSeConvierteATextoSinScriptsNiImagenesRemotas() {
    MensajeLeido m = leer(CorreosDePrueba.soloHtml("a@b.pe", "Pago", "<html><head><style>p{}</style></head><body><p>Estimados, adjunto el <b>voucher</b>.</p>"
        + "<script>robar()</script><img src='http://rastreador.example/pixel.gif'></body></html>", "<m3@b.pe>"));
    assertThat(m.texto()).isEqualTo("Estimados, adjunto el voucher.");
    assertThat(m.texto()).doesNotContain("<").doesNotContain("robar").doesNotContain("rastreador");
  }

  @Test
  void leeUnaCodificacionLatin1QuePrometeQuotedPrintable() {
    String eml = "From: Luis <luis@cliente.pe>\r\nTo: administracion@iqcperu.net\r\nSubject: =?ISO-8859-1?Q?Programaci=F3n_de_auditor=EDa?=\r\nMessage-ID: <m4@cliente.pe>\r\n"
        + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=ISO-8859-1\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nLa auditor=EDa ser=E1 el lunes.\r\n";
    MensajeLeido m = leer(eml.getBytes(StandardCharsets.US_ASCII));
    assertThat(m.asunto()).isEqualTo("Programación de auditoría");
    assertThat(m.texto()).contains("La auditoría será el lunes.");
  }

  @Test
  void conUnCharsetDesconocidoLeeElTextoEnVezDePerderElCorreo() {
    String eml = "From: x@y.pe\r\nSubject: Hola\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=charset-inventado\r\n\r\nTexto legible\r\n";
    assertThat(leer(eml.getBytes(StandardCharsets.US_ASCII)).texto()).contains("Texto legible");
  }

  @Test
  void elNombreDeUnAdjuntoPeligrosoSeSanea() {
    byte[] eml = CorreosDePrueba.correo("a@b.pe", "x", "x", null, null, null, new Adjunto("..\\..\\windows\\sys<tem>|32?.exe", "application/octet-stream", new byte[] {1}),
        new Adjunto("../../etc/passwd", "text/plain", new byte[] {2}), new Adjunto("a".repeat(400) + ".pdf", "application/pdf", new byte[] {3}));
    var nombres = leer(eml).adjuntos().stream().map(MensajeLeido.Adjunto::nombre).toList();
    assertThat(nombres.get(0)).isEqualTo("system32.exe");
    assertThat(nombres.get(1)).isEqualTo("passwd");
    assertThat(nombres.get(2)).hasSize(150).endsWith(".pdf");
    assertThat(nombres).noneMatch(n -> n.contains("/") || n.contains("\\") || n.contains(".."));
  }

  @Test
  void unTextoEnormeSeTrunca() {
    MensajeLeido m = leer(CorreosDePrueba.correo("a@b.pe", "Largo", "palabra ".repeat(30_000), "<m5@b.pe>"));
    assertThat(m.texto()).hasSize(AnalizadorMime.TEXTO_MAXIMO);
    assertThat(m.textoTruncado()).isTrue();
  }

  @Test
  void sinRemitenteOConFechaImposibleUsaValoresSeguros() {
    String eml = "To: administracion@iqcperu.net\r\nSubject: Sin remitente\r\nDate: Mon, 01 Jan 2040 10:00:00 +0000\r\nMessage-ID: <m6@x.pe>\r\n\r\ncuerpo\r\n";
    MensajeLeido m = leer(eml.getBytes(StandardCharsets.US_ASCII));
    assertThat(m.remitenteCorreo()).isEqualTo("desconocido");
    assertThat(m.fecha()).isEqualTo(AHORA);
  }

  @Test
  void leeLosEncabezadosDelHilo() {
    MensajeLeido m = leer(CorreosDePrueba.correo("a@b.pe", "Re: Cotización", "gracias", "<m8@b.pe>", "<m7@b.pe>", "<m1@b.pe> <m7@b.pe>"));
    assertThat(m.inReplyTo()).isEqualTo("<m7@b.pe>");
    assertThat(m.referencias()).isEqualTo("<m1@b.pe> <m7@b.pe>");
  }

  @Test
  void bytesQueNoSonUnCorreoNoTumbanElProceso() {
    MensajeLeido m = leer(new byte[] {0, 1, 2, (byte) 0xFF, 10, 13});
    assertThat(m.remitenteCorreo()).isEqualTo("desconocido");
  }

  private static byte[] crudo(String cabeceras) {
    return ("From: x@y.pe\r\nTo: administracion@iqcperu.net\r\nSubject: Novedades\r\nMessage-ID: <m9@y.pe>\r\n" + cabeceras + "\r\ntexto\r\n").getBytes(StandardCharsets.US_ASCII);
  }

  @Test
  void reconoceElEnvioMasivoPorSusCabeceras() {
    assertThat(leer(crudo("List-Unsubscribe: <mailto:baja@y.pe>\r\n")).masivo()).isTrue();
    assertThat(leer(crudo("List-Id: boletin <boletin.y.pe>\r\n")).masivo()).isTrue();
    assertThat(leer(crudo("Precedence: bulk\r\n")).masivo()).isTrue();
    assertThat(leer(crudo("Precedence: list\r\n")).masivo()).isTrue();
    assertThat(leer(crudo("X-Mailer: Mailchimp Mailer - **CID123**\r\n")).masivo()).isTrue();
    assertThat(leer(crudo("X-Mailgun-Sid: WyI3\r\n")).masivo()).isTrue();
    assertThat(leer(crudo("X-SG-EID: abc\r\n")).masivo()).isTrue();
    assertThat(leer(crudo("Feedback-ID: 1:campania:amazonses\r\n")).masivo()).isTrue();
  }

  @Test
  void unCorreoNormalNoEsMasivo() {
    assertThat(leer(crudo("")).masivo()).isFalse();
    assertThat(leer(crudo("Precedence: first-class\r\n")).masivo()).isFalse();
    assertThat(leer(crudo("X-Mailer: Microsoft Outlook 16.0\r\n")).masivo()).isFalse();
    assertThat(leer(CorreosDePrueba.correo("a@b.pe", "Hola", "texto", "<m10@b.pe>")).masivo()).isFalse();
  }

  @Test
  void elIndicadorSeCalculaTambienSoloDesdeLosBytesGuardados() {
    assertThat(AnalizadorMime.esMasivo(crudo("List-Unsubscribe: <mailto:baja@y.pe>\r\n"))).isTrue();
    assertThat(AnalizadorMime.esMasivo(crudo(""))).isFalse();
    assertThat(AnalizadorMime.esMasivo(new byte[] {0, 1, 2})).isFalse();
  }
}
