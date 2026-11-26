package pe.com.iqc.flow.plantillas.infrastructure;

import static pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Alineacion.CENTRO;
import static pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Alineacion.IZQUIERDA;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.CertificadoPersonal;
import pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Trozo;

/**
 *certificado de curso o capacitación (A4 horizontal, 842 x 675 pt). Medidas tomadas del original de IQC: puntos desde
 *el borde superior izquierdo, con la línea base de cada texto
 */
final class PlantillaPersonal {
  private static final Pattern TIPO_DE_CURSO = Pattern.compile("^(.*?)\\s+en\\s+base\\s+a\\b.*$", Pattern.CASE_INSENSITIVE);

  private static final float CENTRO_PAGINA = 421f;
  private static final float ANCHO_TEXTO = 640f, ANCHO_DESCRIPCION = 305f;

  // Franja azul oscuro
  private static final float LOGO_X = 43f, LOGO_Y = 4.5f, LOGO_ANCHO = 156f, LOGO_ALTO_MAXIMO = 78.5f;
  private static final float X_TITULO = 285f, Y_TITULO = 48f, X_SUBTITULO = 287f, Y_SUBTITULO = 76f;

  // Cuerpo
  private static final float Y_PRESENTE = 152f, Y_NOMBRE = 187f, Y_DOCUMENTO = 217f, Y_INTRO = 241f;
  private static final float Y_NORMA = 266f, Y_RELACION = 288f;
  private static final float Y_DESCRIPCION = 311f, INTERLINEA_DESCRIPCION = 17f, SALTO_TEMA = 19f, SALTO_DETALLE = 20f;

  // Caja gris de datos del curso
  private static final float[] X_COLUMNAS = {90f, 279f, 480f};
  private static final float Y_ROTULOS = 403f, Y_VALORES = 427f;

  // Datos al pie y firma
  private static final float X_DATOS = 66f, Y_ENTRENADOR = 467f, Y_EMISION = 485f, Y_CODIGO = 503f;
  private static final float CENTRO_FIRMA = 654f, FIRMA_X = 602f, FIRMA_BASE_Y = 499f, FIRMA_ANCHO = 104f;
  private static final float Y_FIRMANTE = 517f, Y_CARGO = 531f;

  // Texto legal
  private static final float X_LEGAL = 66f, ANCHO_LEGAL = 712.5f, Y_LEGAL = 561f, INTERLINEA_LEGAL = 12f;
  private static final String SITIO_IQC = "www.iqcperu.com", SITIO_IQC_GLOBAL = "www.iqcglobal.com";

  private final ContextoPdf c;
  private final Lienzo l;

  private final Estilo franjaTitulo, franjaSubtitulo, presente, nombre, documento, norma, relacion, tema, detalleNegrita,
      detalle, rotulo, valor, dato, firmante, firmaCargo, legal, legalNegrita, enlace;

  PlantillaPersonal(ContextoPdf contexto) {
    this.c = contexto;
    this.l = contexto.lienzo();
    var f = contexto.fuentes();
    franjaTitulo = new Estilo(f.helveticaNegrita(), 32f, Paleta.BLANCO);
    franjaSubtitulo = new Estilo(f.helvetica(), 10f, Paleta.BLANCO);
    presente = new Estilo(f.helvetica(), 12f, Paleta.PERSONAL_TEXTO);
    nombre = new Estilo(f.helveticaNegrita(), 23f, Paleta.PERSONAL_TITULO);
    documento = new Estilo(f.helveticaNegrita(), 10f, Paleta.PERSONAL_DOCUMENTO);
    norma = new Estilo(f.helveticaNegrita(), 25f, Paleta.PERSONAL_TITULO);
    relacion = new Estilo(f.helveticaNegrita(), 10f, Paleta.PERSONAL_ACENTO);
    tema = new Estilo(f.helveticaNegrita(), 14f, Paleta.PERSONAL_TEXTO);
    detalleNegrita = new Estilo(f.helveticaNegrita(), 11f, Paleta.PERSONAL_TEXTO);
    detalle = new Estilo(f.helvetica(), 11f, Paleta.PERSONAL_TEXTO);
    rotulo = new Estilo(f.helveticaNegrita(), 9f, Paleta.PERSONAL_ACENTO);
    valor = new Estilo(f.helveticaNegrita(), 13f, Paleta.PERSONAL_TEXTO);
    dato = new Estilo(f.helvetica(), 10f, Paleta.PERSONAL_TEXTO);
    firmante = new Estilo(f.helveticaNegrita(), 10f, Paleta.PERSONAL_TEXTO);
    firmaCargo = dato;
    legal = new Estilo(f.helvetica(), 9f, Paleta.PERSONAL_TEXTO);
    legalNegrita = new Estilo(f.helveticaNegrita(), 9f, Paleta.PERSONAL_TEXTO);
    enlace = legal.conColor(Paleta.PERSONAL_ACENTO);
  }

  void dibujar(CertificadoPersonal d) {
    dibujarFranja(d);
    l.texto("Por la presente se certifica que", presente, CENTRO_PAGINA, Y_PRESENTE, CENTRO);
    l.texto(d.nombres(), l.queCabe(d.nombres(), nombre, 12f, ANCHO_TEXTO), CENTRO_PAGINA, Y_NOMBRE, CENTRO);
    if (hay(d.numeroDocumento())) {
      l.texto((hay(d.tipoDocumento()) ? d.tipoDocumento() + " " : "") + d.numeroDocumento(), documento, CENTRO_PAGINA,
          Y_DOCUMENTO, CENTRO);
    }
    l.texto(introduccion(d.curso()), presente, CENTRO_PAGINA, Y_INTRO, CENTRO);
    l.texto(tituloNorma(d), norma, CENTRO_PAGINA, Y_NORMA, CENTRO);
    if (hay(d.subtitulo())) l.texto(d.subtitulo(), relacion, CENTRO_PAGINA, Y_RELACION, CENTRO);
    dibujarDescripcion(d.descripcion());
    dibujarCaja(d);
    dibujarDatosYFirma(d);
    dibujarLegal(d.urlConsulta());
  }

  /**
   *la descripción admite hasta tres párrafos (separados por salto de línea), como el original: el tema en negrita
   *grande (hasta dos líneas), el sistema en negrita y el detalle en regular
   */
  private void dibujarDescripcion(String descripcion) {
    if (!hay(descripcion)) return;
    String[] parrafos = descripcion.strip().split("\\R+");
    List<String> lineas = l.ajustar(parrafos[0], tema, ANCHO_DESCRIPCION);
    float y = Y_DESCRIPCION;
    for (int i = 0; i < Math.min(lineas.size(), 2); i++) {
      l.texto(lineas.get(i), tema, CENTRO_PAGINA, y + i * INTERLINEA_DESCRIPCION, CENTRO);
    }
    y += (Math.min(lineas.size(), 2) - 1) * INTERLINEA_DESCRIPCION;
    if (parrafos.length > 1) {
      y += SALTO_TEMA;
      l.texto(parrafos[1], detalleNegrita, CENTRO_PAGINA, y, CENTRO);
    }
    if (parrafos.length > 2) l.texto(parrafos[2], detalle, CENTRO_PAGINA, y + SALTO_DETALLE, CENTRO);
  }

  private void dibujarFranja(CertificadoPersonal d) {
    var logo = d.logo() != null ? c.imagen(d.logo(), "logo-marca") : c.imagen("imagenes/logo-iqc-peru-blanco.png");
    float alto = LOGO_ANCHO * logo.getHeight() / logo.getWidth();
    // un logo de marca más alto que la franja se reduce para no salirse de ella
    float escala = Math.min(1f, LOGO_ALTO_MAXIMO / alto);
    l.imagen(logo, LOGO_X, LOGO_Y, LOGO_ANCHO * escala, alto * escala);
    l.texto("CERTIFICADO", franjaTitulo, X_TITULO, Y_TITULO, IZQUIERDA);
    l.texto(d.curso().toUpperCase(Locale.ROOT), franjaSubtitulo, X_SUBTITULO, Y_SUBTITULO, IZQUIERDA);
  }

  private void dibujarCaja(CertificadoPersonal d) {
    String[] rotulos = {"DURACIÓN", "MODALIDAD", "FECHAS DEL CURSO"};
    String[] valores = {horas(d.duracionHoras()), d.modalidad(), FormatoFechas.rango(d.fechaInicio(), d.fechaFin())};
    for (int i = 0; i < rotulos.length; i++) {
      if (!hay(valores[i])) continue;
      l.texto(rotulos[i], rotulo, X_COLUMNAS[i], Y_ROTULOS, IZQUIERDA);
      l.texto(valores[i], valor, X_COLUMNAS[i], Y_VALORES, IZQUIERDA);
    }
  }

  private void dibujarDatosYFirma(CertificadoPersonal d) {
    if (hay(d.entrenador())) l.texto("Entrenador: " + d.entrenador(), dato, X_DATOS, Y_ENTRENADOR, IZQUIERDA);
    l.texto("Fecha de emisión: " + FormatoFechas.texto(d.fechaEmision()), dato, X_DATOS, Y_EMISION, IZQUIERDA);
    l.texto("Código de verificación: " + d.codigo(), dato, X_DATOS, Y_CODIGO, IZQUIERDA);
    if (d.firmante().conFirma()) {
      var firma = c.imagen("imagenes/firma-gerencia.png");
      float alto = FIRMA_ANCHO * firma.getHeight() / firma.getWidth();
      l.imagen(firma, FIRMA_X, FIRMA_BASE_Y - alto, FIRMA_ANCHO, alto);
    }
    String nombreFirmante = (hay(d.firmante().titulo()) ? d.firmante().titulo() + " " : "") + d.firmante().nombre();
    l.texto(nombreFirmante, firmante, CENTRO_FIRMA, Y_FIRMANTE, CENTRO);
    l.texto(d.firmante().cargo(), firmaCargo, CENTRO_FIRMA, Y_CARGO, CENTRO);
  }

  /**texto legal fijo de IQC con sus enlaces en azul y sus rótulos en negrita; el segundo párrafo sigue al primero*/
  private void dibujarLegal(String urlConsulta) {
    List<Trozo> general = new ArrayList<>(List.of(
        new Trozo("IQC supervisa anualmente la validez de estos certificados, los cuales se pueden verificar en la web ", legal),
        new Trozo(SITIO_IQC, enlace),
        new Trozo(" como registro para verificar la vigencia y validez. Por favor visitar ", legal),
        new Trozo(urlConsulta, enlace),
        new Trozo(" para verificar la autenticidad y validez de este certificado de conformidad. ", legal),
        new Trozo("Corporate Office:", legalNegrita),
        new Trozo(" Platinum City, G/13/03, Site # 02, Next to CMTI, HMT Road, Yeshwanthpur Post, Bangalore - 560 022, India. "
            + "Tel: +91(80) 41172752, 41277353, 41280347. Email: iqccorporate@iqcglobal.com. Website: ", legal),
        new Trozo(SITIO_IQC_GLOBAL, enlace),
        new Trozo(".", legal)));
    float y = l.parrafo(general, X_LEGAL, ANCHO_LEGAL, Y_LEGAL, INTERLINEA_LEGAL, true, 10) + INTERLINEA_LEGAL;
    l.parrafo(List.of(new Trozo("Peru Representative:", legalNegrita),
        new Trozo(" Jr. Abancay No. 425 Urb. San Carlos Third Floor, Office 301 Junin - Huancayo Huancayo Republic of Peru. "
            + "Tel: +51- 923980697. E-mail: administracion@iqcperu.com. RUC No: 20604476691.", legal)),
        X_LEGAL, ANCHO_LEGAL, y, INTERLINEA_LEGAL, true, 4);
  }

  /**«Curso de auditor líder en base a la 17024:2012» → «ha completado con éxito el curso de auditor líder en»*/
  private static String introduccion(String curso) {
    Matcher tipo = TIPO_DE_CURSO.matcher(curso.strip());
    String tema = tipo.matches() ? tipo.group(1) : "curso";
    return "ha completado con éxito el " + tema.substring(0, 1).toLowerCase(Locale.ROOT) + tema.substring(1) + " en";
  }

  /**«ISO 27701:2025»; sin norma de referencia, el nombre del curso*/
  private static String tituloNorma(CertificadoPersonal d) {
    if (!hay(d.normaReferencia())) return d.curso();
    return hay(d.normaAnio()) ? d.normaReferencia() + ":" + d.normaAnio() : d.normaReferencia();
  }

  /**«40 horas», «12,5 horas», «1 hora»*/
  private static String horas(BigDecimal horas) {
    if (horas == null) return null;
    BigDecimal limpio = horas.stripTrailingZeros();
    String numero = limpio.scale() < 0 ? limpio.setScale(0).toPlainString() : limpio.toPlainString().replace('.', ',');
    return numero + (limpio.compareTo(BigDecimal.ONE) == 0 ? " hora" : " horas");
  }

  private static boolean hay(String texto) { return texto != null && !texto.isBlank(); }
}
