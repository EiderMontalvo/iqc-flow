package pe.com.iqc.flow.plantillas.infrastructure;

import static pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Alineacion.CENTRO;
import static pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Alineacion.DERECHA;
import static pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Alineacion.IZQUIERDA;

import java.time.LocalDate;
import java.util.List;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.CertificadoIqcGlobal;
import pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Trozo;

/**
 *certificado de IQC Global (India), formato QF-12 (A4 vertical). El arte (franja lateral, logos, sello de la norma,
 *marca de agua y firma del Director) está en el fondo; aquí se escribe lo que cambia. Las medidas salen de los tres
 *originales de IQC y varían un poco entre ellos (se armaron a mano), así que cada familia de norma lleva las suyas
 */
final class PlantillaIqcGlobal {
  /**
   *medidas de una familia: «x» y líneas base en puntos desde el borde superior izquierdo. La tabla de datos tiene dos
   *columnas (izquierda: subdivisión…; derecha: fechas); {@code pie} son las líneas base de las cinco líneas del pie
   */
  record Diseno(String fondo, float centro, float yEncabezado, float yReconocimiento, float xTitular, float yTitular,
      float yDireccion, float xCuerpo, float yCuerpo, float ySistemas, float xNorma, float yNorma, float tamanoNorma,
      float xAlcance, float yAlcance, float xIzquierda, float xDerecha, float yTabla, float centroFirma, float yFirma,
      float xParrafo, float yParrafo, float xPie, float[] pie, float[] tamanoPie, float derechaFormato, float yFormato,
      float tamanoFormato) {
  }

  private static final Diseno QMS = new Diseno("fondos/global-9001.pdf", 331.6f, 149.8f, 168.3f, 92f, 204.9f, 250.3f, 95.6f,
      304.1f, 320.9f, 95.2f, 339f, 16f, 92f, 367.8f, 97.7f, 401.9f, 586.9f, 519.5f, 721.8f, 102.3f, 748.1f, 101.8f,
      new float[] {793.2f, 801.2f, 809.2f, 817.2f, 825.2f}, new float[] {7, 7, 7, 7, 7}, 565.3f, 829.8f, 7f);
  private static final Diseno EMS = new Diseno("fondos/global-14001.pdf", 331.6f, 149.8f, 168.3f, 103f, 218.7f, 271.8f,
      105.1f, 332.5f, 349.3f, 104.8f, 367.4f, 16f, 100.1f, 410f, 99.9f, 404f, 589.3f, 512.7f, 717f, 101.8f, 744.3f, 101.8f,
      new float[] {793.2f, 802.2f, 810.2f, 818.1f, 826.8f}, new float[] {8, 8, 7, 7, 7}, 583.1f, 832.7f, 8f);
  private static final Diseno OHSMS = new Diseno("fondos/global-45001.pdf", 331f, 166.8f, 188.2f, 97f, 224.2f, 270.2f, 103.3f,
      336.5f, 351.7f, 102.1f, 372.9f, 18f, 97f, 410.2f, 98.5f, 402.6f, 617.2f, 527.2f, 717.8f, 103.6f, 742.4f, 101.2f,
      new float[] {789f, 797.9f, 806f, 813.8f, 822.5f}, new float[] {8, 8, 7, 7, 7}, 579.3f, 828.6f, 8f);
  /**normas sin original (ISO 37001, 27001…): el diseño de ISO 9001 con un fondo sin sello ni siglas en la franja lateral*/
  private static final Diseno GENERICO = new Diseno("fondos/global-generico.pdf", QMS.centro(), QMS.yEncabezado(),
      QMS.yReconocimiento(), QMS.xTitular(), QMS.yTitular(), QMS.yDireccion(), QMS.xCuerpo(), QMS.yCuerpo(), QMS.ySistemas(),
      QMS.xNorma(), QMS.yNorma(), QMS.tamanoNorma(), QMS.xAlcance(), QMS.yAlcance(), QMS.xIzquierda(), QMS.xDerecha(),
      QMS.yTabla(), QMS.centroFirma(), QMS.yFirma(), QMS.xParrafo(), QMS.yParrafo(), QMS.xPie(), QMS.pie(), QMS.tamanoPie(),
      QMS.derechaFormato(), QMS.yFormato(), QMS.tamanoFormato());

  static Diseno diseno(String normaCodigo) {
    return switch (normaCodigo) {
      case "ISO_9001" -> QMS;
      case "ISO_14001" -> EMS;
      case "ISO_45001" -> OHSMS;
      default -> GENERICO;
    };
  }

  // Textos fijos del formato QF-12
  private static final String EMISOR = "INTEGRATED QUALITY CERTIFICATION PRIVATE LIMITED";
  private static final String CERTIFICA = "hereby certifies that the Quality Management Systems of";
  private static final String EVALUADO = "ha sido evaluado y cumple con los";
  private static final String FIRMANTE = "Suma Shankar", CARGO = "Director", FORMATO = "QF-12 Rev 01 date 28.10.2025";
  private static final List<String> SUPERVISION = List.of(
      "IQC supervisa anualmente la validez del alcance de los sistemas de gestión mediante auditorías de seguimiento planificadas, conside-",
      "rando los cambios en el sistema de gestión del cliente. El certificado de cumplimiento se actualizará en el sitio web/registro como",
      "suspendido o retirado si el programa de auditorías de seguimiento no se coordina e implementa antes de la fecha límite indicada",
      "anteriormente.");
  private static final List<String> PIE = List.of(
      "Corporate Office : Platinum City, G/13/03, Site # 02, Next to CMTI, HMT Road, Yeshwanthpur Post, Bangalore - 560 022, India.",
      "Tel : +91(80) 41172752, 41277353, 41280347, Email : iqccorporate@iqcglobal.com  Website : www.iqcglobal.com.",
      "Peru Representative : Jr. Abancay No. 425 Urb. San Carlos Third Floor, Office 301 Junin - Huancayo  Huancayo  Republic of Peru.",
      "Tel : +51- 923980697,  E-mail : administracion@iqcperu.com, RUC No: 20604476691.");

  // Geometría común a las tres familias
  // el texto llega hasta el borde derecho de los originales; las líneas junto al sello de la norma, hasta donde empieza el sello
  private static final float DERECHA_TEXTO = 580f, DERECHA_JUNTO_AL_SELLO = 480f;
  private static final float ANCHO_CONDENSADO_DIRECCION = 83.6f;
  private static final float INTERLINEA_NOMBRE = 20f, INTERLINEA_DIRECCION = 14f, INTERLINEA_SISTEMAS = 17f;
  private static final float SANGRIA_ALCANCE = 54.6f, INTERLINEA_ALCANCE = 14f, Y_LIMITE_ALCANCE = 545f, MINIMO_ALCANCE = 8f;
  private static final float COLUMNA_DOS_PUNTOS = 135.4f, ANCHO_COLUMNA_DERECHA = 175.7f;
  private static final float PASO_IZQUIERDA = 19.15f, PASO_DERECHA = 17.8f, SEPARACION_CARGO = 12.3f, INTERLINEA_PARRAFO = 9.6f;

  private final Lienzo l;
  private final Diseno d;
  private final Estilo emisor, certifica, nombre, direccion, cuerpo, norma, alcanceRotulo, alcance, fila, filaChica, firmante,
      supervision, pie, formato;

  PlantillaIqcGlobal(ContextoPdf contexto, Diseno diseno) {
    this.l = contexto.lienzo();
    this.d = diseno;
    var f = contexto.fuentes();
    emisor = new Estilo(f.palatino(), 15f, Paleta.GLOBAL_TEXTO);
    certifica = new Estilo(f.palatino(), 16f, Paleta.GLOBAL_NEGRO);
    nombre = new Estilo(f.palatinoNegrita(), 17f, Paleta.GLOBAL_NEGRO);
    direccion = new Estilo(f.palatino(), 13.33f, Paleta.GLOBAL_TEXTO, ANCHO_CONDENSADO_DIRECCION);
    cuerpo = new Estilo(f.palatino(), 15f, Paleta.GLOBAL_TEXTO);
    norma = new Estilo(f.palatinoNegrita(), diseno.tamanoNorma(), Paleta.GLOBAL_NEGRO);
    alcanceRotulo = new Estilo(f.palatinoNegrita(), 13f, Paleta.GLOBAL_NEGRO);
    alcance = new Estilo(f.palatino(), 11f, Paleta.GLOBAL_NEGRO);
    fila = new Estilo(f.palatino(), 11f, Paleta.GLOBAL_TEXTO);
    filaChica = fila.conTamano(10f);
    firmante = new Estilo(f.palatino(), 11f, Paleta.GLOBAL_TEXTO);
    supervision = new Estilo(f.palatino(), 8f, Paleta.GLOBAL_NEGRO);
    pie = new Estilo(f.palatino(), 7f, Paleta.GLOBAL_NEGRO);
    formato = new Estilo(f.palatino(), diseno.tamanoFormato(), Paleta.GLOBAL_TEXTO);
  }

  void dibujar(CertificadoIqcGlobal c) {
    float ancho = DERECHA_TEXTO - d.xTitular();
    l.texto(EMISOR, emisor, d.centro(), d.yEncabezado(), CENTRO);
    l.texto(CERTIFICA, certifica, d.centro(), d.yReconocimiento(), CENTRO);
    // Todo texto cabe entre el margen y la derecha; si una línea tiene que partirse, lo que sigue baja lo que ocupó de más
    float baja = 0;
    baja += sobrante(l.lineaQueCabe(c.razonSocial(), nombre, 11f, d.xTitular(), d.yTitular() + baja, ancho, INTERLINEA_NOMBRE, IZQUIERDA), INTERLINEA_NOMBRE);
    baja += sobrante(l.lineaQueCabe(c.direccion(), direccion, 8f, d.xTitular(), d.yDireccion() + baja, ancho, INTERLINEA_DIRECCION, IZQUIERDA), INTERLINEA_DIRECCION);
    l.texto(EVALUADO, cuerpo, d.xCuerpo(), d.yCuerpo() + baja, IZQUIERDA);
    baja += sobrante(l.lineaQueCabe(c.sistemasGestion(), cuerpo, 12f, d.xCuerpo(), d.ySistemas() + baja,
        DERECHA_JUNTO_AL_SELLO - d.xCuerpo(), INTERLINEA_SISTEMAS, IZQUIERDA), INTERLINEA_SISTEMAS);
    baja += sobrante(l.lineaQueCabe(c.norma(), norma, 11f, d.xNorma(), d.yNorma() + baja, DERECHA_JUNTO_AL_SELLO - d.xNorma(), norma.tamano() + 2,
        IZQUIERDA), norma.tamano() + 2);
    if (hay(c.alcance())) dibujarAlcance(c.alcance(), baja);
    dibujarTabla(c);
    l.texto(FIRMANTE, firmante, d.centroFirma(), d.yFirma(), CENTRO);
    l.texto(CARGO, firmante, d.centroFirma(), d.yFirma() + SEPARACION_CARGO, CENTRO);
    for (int i = 0; i < SUPERVISION.size(); i++) {
      l.texto(SUPERVISION.get(i), supervision, d.xParrafo(), d.yParrafo() + i * INTERLINEA_PARRAFO, IZQUIERDA);
    }
    dibujarPie(c);
  }

  /**rótulo «Alcance:» en negrita y el texto a su lado; si no cabe en una línea se parte y se achica hasta 8 pt, sin llegar a la tabla*/
  private void dibujarAlcance(String texto, float baja) {
    float y = d.yAlcance() + baja, x = d.xAlcance() + SANGRIA_ALCANCE, ancho = DERECHA_TEXTO - x;
    int maxLineas = Math.max(1, (int) ((Y_LIMITE_ALCANCE - y) / INTERLINEA_ALCANCE) + 1);
    Estilo estilo = alcance;
    while (estilo.tamano() > MINIMO_ALCANCE && l.ajustar(texto, estilo, ancho).size() > maxLineas) estilo = estilo.conTamano(estilo.tamano() - 0.5f);
    l.texto("Alcance:", alcanceRotulo, d.xAlcance(), y, IZQUIERDA);
    // shortcut: un alcance de más líneas de las que caben a 8 pt se corta; subir Y_LIMITE_ALCANCE si IQC lo necesita
    l.parrafo(List.of(new Trozo(texto, estilo)), x, ancho, y, INTERLINEA_ALCANCE, false, maxLineas);
  }

  private void dibujarTabla(CertificadoIqcGlobal c) {
    float xDosPuntos = d.xIzquierda() + COLUMNA_DOS_PUNTOS;
    String[][] izquierda = {{"Subdivisión", dato(c.subdivision(), "–")}, {"Clase", dato(c.clase(), "–")},
        {"Procesos no aplicables", dato(c.procesosNoAplicables(), "NA")}, {"Número de certificado", c.codigo()},
        {"Anexos", dato(c.anexos(), "NA")}};
    for (int i = 0; i < izquierda.length; i++) {
      float y = d.yTabla() + i * PASO_IZQUIERDA;
      l.texto(izquierda[i][0], fila, d.xIzquierda(), y, IZQUIERDA);
      l.texto(": " + izquierda[i][1], fila, xDosPuntos, y, IZQUIERDA);
    }
    String[][] derecha = {{"Fecha de emisión inicial", puntos(c.fechaEmision())}, {"Fecha de 1era vigilancia", puntos(c.seguimiento1())},
        {"Fecha de 2da vigilancia", puntos(c.seguimiento2())}, {"Certificado válido hasta", puntos(c.fechaExpiracion())}};
    // los dos puntos de las cuatro fechas quedan alineados: se parte del ancho de una fecha completa
    float xFechas = d.xDerecha() + ANCHO_COLUMNA_DERECHA - l.ancho(": 00.00.0000", fila);
    for (int i = 0; i < derecha.length; i++) {
      float y = d.yTabla() + i * PASO_DERECHA;
      Estilo estilo = i == derecha.length - 1 ? filaChica : fila;
      l.texto(derecha[i][0], estilo, d.xDerecha(), y, IZQUIERDA);
      l.texto(": " + derecha[i][1], estilo, xFechas, y, IZQUIERDA);
    }
  }

  private void dibujarPie(CertificadoIqcGlobal c) {
    for (int i = 0; i < PIE.size(); i++) l.texto(PIE.get(i), pie.conTamano(d.tamanoPie()[i]), d.xPie(), d.pie()[i], IZQUIERDA);
    String url = hay(c.urlVerificacion()) ? c.urlVerificacion() : "https://consultas.iqcperu.com/";
    l.texto("Porfavor visitar " + url + " para verificar la autenticidad y validez de este certificado de conformidad.",
        pie.conTamano(d.tamanoPie()[4]), d.xPie(), d.pie()[4], IZQUIERDA);
    l.texto(FORMATO, formato, d.derechaFormato(), d.yFormato(), DERECHA);
  }

  /**lo que una línea partida ocupa de más que una sola*/
  private static float sobrante(int lineas, float interlinea) { return lineas > 1 ? (lineas - 1) * interlinea : 0; }

  private static String puntos(LocalDate fecha) { return fecha == null ? "–" : FormatoFechas.puntos(fecha); }

  private static String dato(String valor, String porDefecto) { return hay(valor) ? valor : porDefecto; }

  private static boolean hay(String texto) { return texto != null && !texto.isBlank(); }
}
