package pe.com.iqc.flow.plantillas.infrastructure;

import static pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Alineacion.CENTRO;
import static pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Alineacion.DERECHA;
import static pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Alineacion.IZQUIERDA;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.Acreditadora;
import pe.com.iqc.flow.plantillas.GeneradorPdfCertificado.CertificadoEmpresarial;
import pe.com.iqc.flow.plantillas.infrastructure.Lienzo.Trozo;

/**
 *certificado de sistema de gestión DDC-FRM-015 (A4 vertical). Las medidas salen del original de IQC: puntos desde el
 *borde superior izquierdo, con la línea base de cada texto. Las líneas condensadas del original (razón social, fechas)
 *se reproducen con la escala horizontal de su estilo
 */
final class PlantillaEmpresarial {
  private static final String FORMATO = "DDC-FRM-015 – V.08";

  // Geometría
  private static final float CENTRO_PAGINA = 297.6f;
  private static final float ANCHO_TITULO = 230.4f, ANCHO_MAXIMO_TITULAR = 430f;
  private static final float Y_TITULO = 138.4f, Y_RECONOCIMIENTO = 162.8f, Y_TITULAR = 191.3f, Y_DIRECCION = 213.5f;
  private static final float Y_REQUISITOS = 246.6f, Y_NORMA = 286.7f, Y_ALCANCE_ROTULO = 315f;
  private static final float X_ALCANCE = 76.5f, ANCHO_ALCANCE = 461.5f, Y_ALCANCE = 346.2f, INTERLINEA_ALCANCE = 13.2f;
  private static final int ALCANCE_MAX_LINEAS = 9;
  // Texto centrado: cuerpo mínimo antes de partir la línea, y separación entre las líneas partidas
  private static final float MINIMO_RECONOCIMIENTO = 10f, MINIMO_TITULAR = 8f, MINIMO_DIRECCION = 6.5f, MINIMO_NORMA = 18f;
  private static final float INTERLINEA_RECONOCIMIENTO = 15f, INTERLINEA_TITULAR = 14f, INTERLINEA_DIRECCION = 10f, INTERLINEA_NORMA = 28f;
  private static final float X_CODIGO = 229.3f, Y_CODIGO = 471.5f;
  private static final float X_ETIQUETAS = 98.8f, X_VALORES = 501f, Y_PRIMERA_FILA = 487.9f, PASO_FILA = 15f, DESFASE_VALORES = -5.3f;
  private static final float ANCHO_ACREDITACION = 170f, MINIMO_ACREDITACION = 5.2f;
  private static final float Y_ACREDITADO_POR = 613.9f, Y_ACREDITACION = 665.5f, PASO_ACREDITACION = 7.6f;
  private static final float PARCHE_X = 235f, PARCHE_Y = 618f, PARCHE_ANCHO = 127f, PARCHE_ALTO = 44f;
  private static final float LOGO_INACAL_ANCHO = 80f, LOGO_INACAL_Y = 619f;
  private static final float CENTRO_FIRMA = 443.2f, FIRMA_X = 389.6f, FIRMA_Y = 614.7f, FIRMA_ANCHO = 117.8f;
  private static final float Y_FIRMANTE = 680.8f, Y_EMPRESA = 695.4f, Y_CARGO = 707.8f;
  private static final float X_PIE = 87.8f, X_UBICACION = 119.6f, DERECHA_PIE = 518.6f;
  private static final float Y_VERIFICACION = 727.3f, Y_UBICACION = 736.3f, PASO_UBICACION = 7.5f, Y_CONTACTO = 753f;
  private static final float Y_NOTA_1 = 761.9f, Y_NOTA_1B = 770.8f, Y_NOTA_2 = 779.8f, Y_NOTA_2B = 788.7f;
  private static final float Y_VERSION = 767.2f, Y_VALIDEZ_1 = 778.1f, Y_VALIDEZ_2 = 787.9f, X_FORMATO = 50.4f, Y_FORMATO = 813.3f;

  private final Lienzo l;
  private final ContextoPdf c;

  // Tipografía (tamaño real y escala horizontal medidos en el original)
  private final Estilo titulo, reconocimiento, titular, direccion, requisitos, norma, alcanceRotulo, alcance, codigo, fila,
      acreditadoPor, acreditacion, acreditacionNumero, firmante, firmaCargo, pie, pieLigero, version, validez, formato;

  PlantillaEmpresarial(ContextoPdf contexto) {
    this.c = contexto;
    this.l = contexto.lienzo();
    var f = contexto.fuentes();
    titulo = new Estilo(f.latoNegrita(), 31.237f, Paleta.AZUL_EMPRESARIAL);
    reconocimiento = new Estilo(f.latoNegrita(), 13.878f, Paleta.NEGRO);
    titular = new Estilo(f.latoNegrita(), 15f, Paleta.AZUL_EMPRESARIAL, 72.9f);
    direccion = new Estilo(f.latoNegrita(), 9f, Paleta.AZUL_EMPRESARIAL);
    requisitos = new Estilo(f.latoNegrita(), 12.246f, Paleta.NEGRO);
    norma = new Estilo(f.latoNegrita(), 25.65f, Paleta.AZUL_EMPRESARIAL);
    alcanceRotulo = requisitos;
    alcance = new Estilo(f.helvetica(), 10.2f, Paleta.TEXTO_ALCANCE);
    codigo = new Estilo(f.latoNegrita(), 10.09f, Paleta.NEGRO, 100.5f);
    fila = new Estilo(f.latoLigera(), 11f, Paleta.FECHAS, 82.2f);
    acreditadoPor = new Estilo(f.helveticaNegrita(), 7.102f, Paleta.TEXTO_ALCANCE);
    acreditacion = new Estilo(f.helveticaNegrita(), 6.385f, Paleta.TEXTO_ALCANCE);
    acreditacionNumero = acreditacion.conColor(Paleta.ACREDITACION_NUMERO);
    firmante = new Estilo(f.latoNegrita(), 8.925f, Paleta.NEGRO, 104.5f);
    firmaCargo = new Estilo(f.latoLigera(), 8.925f, Paleta.NEGRO, 104.5f);
    pie = new Estilo(f.latoNegrita(), 6.247f, Paleta.PIE_EMPRESARIAL);
    pieLigero = new Estilo(f.latoLigera(), 6.247f, Paleta.PIE_EMPRESARIAL);
    version = new Estilo(f.latoNegrita(), 8.032f, Paleta.VERSION_EMPRESARIAL, 104.5f);
    validez = new Estilo(f.latoLigeraCursiva(), 7.14f, Paleta.VALIDEZ, 104.5f);
    formato = new Estilo(f.latoLigera(), 6f, Paleta.FORMATO_GRIS, 104.5f);
  }

  void dibujar(CertificadoEmpresarial d) {
    l.texto("CERTIFICADO", titulo, CENTRO_PAGINA, Y_TITULO, CENTRO, espaciadoParaAncho("CERTIFICADO", titulo, ANCHO_TITULO));
    // Todo texto centrado cabe dentro del marco; si una línea tiene que partirse, lo que sigue baja lo que ocupó de más
    float baja = 0;
    baja += sobrante(l.lineaQueCabe("En reconocimiento al " + d.sistemaGestion() + " de:", reconocimiento, MINIMO_RECONOCIMIENTO,
        CENTRO_PAGINA, Y_RECONOCIMIENTO, ANCHO_MAXIMO_TITULAR, INTERLINEA_RECONOCIMIENTO, CENTRO), INTERLINEA_RECONOCIMIENTO);
    baja += sobrante(l.lineaQueCabe(d.razonSocial(), titular, MINIMO_TITULAR, CENTRO_PAGINA, Y_TITULAR + baja,
        ANCHO_MAXIMO_TITULAR, INTERLINEA_TITULAR, CENTRO), INTERLINEA_TITULAR);
    baja += sobrante(l.lineaQueCabe(d.direccion(), direccion, MINIMO_DIRECCION, CENTRO_PAGINA, Y_DIRECCION + baja,
        ANCHO_MAXIMO_TITULAR, INTERLINEA_DIRECCION, CENTRO), INTERLINEA_DIRECCION);
    l.texto("El cual se encuentra implementado y es conforme con los requisitos de la norma:", requisitos, CENTRO_PAGINA,
        Y_REQUISITOS + baja, CENTRO);
    baja += sobrante(l.lineaQueCabe(d.norma(), norma, MINIMO_NORMA, CENTRO_PAGINA, Y_NORMA + baja, ANCHO_MAXIMO_TITULAR,
        INTERLINEA_NORMA, CENTRO), INTERLINEA_NORMA);
    if (hay(d.alcance())) {
      l.texto("Alcance:", alcanceRotulo, CENTRO_PAGINA, Y_ALCANCE_ROTULO + baja, CENTRO);
      // el alcance pierde las líneas que se llevaron las partidas de arriba, para no pisar el ciclo de certificación
      dibujarAlcance(d.alcance(), baja, Math.max(1, ALCANCE_MAX_LINEAS - (int) Math.ceil(baja / INTERLINEA_ALCANCE)));
    }
    l.texto("Certificado No.: " + d.codigo(), codigo, X_CODIGO, Y_CODIGO, IZQUIERDA);
    dibujarCiclo(d);
    dibujarAcreditacion(d);
    dibujarFirma(d);
    dibujarPie(d);
  }

  /**lo que una línea partida ocupa de más que una sola*/
  private static float sobrante(int lineas, float interlinea) { return lineas > 1 ? (lineas - 1) * interlinea : 0; }

  /**el alcance es lo único de largo variable: se justifica y, si no cabe, se achica hasta 8 pt; pasado eso se corta*/
  private void dibujarAlcance(String texto, float baja, int maxLineas) {
    Estilo estilo = alcance;
    while (estilo.tamano() > 8 && l.ajustar(texto, estilo, ANCHO_ALCANCE).size() > maxLineas) {
      estilo = estilo.conTamano(estilo.tamano() - 0.5f);
    }
    // shortcut: un alcance de más de ALCANCE_MAX_LINEAS a 8 pt se corta; subir el límite si IQC lo necesita
    l.parrafo(List.of(new Trozo(texto, estilo)), X_ALCANCE, ANCHO_ALCANCE, Y_ALCANCE + baja,
        estilo.tamano() * INTERLINEA_ALCANCE / alcance.tamano(), true, maxLineas);
  }

  private void dibujarCiclo(CertificadoEmpresarial d) {
    float y = Y_PRIMERA_FILA;
    l.texto("Ciclo de certificación Nro. " + d.ciclo(), fila, X_ETIQUETAS, y, IZQUIERDA);
    y = filaFecha("Fecha efectiva de otorgamiento:", FormatoFechas.larga(d.fechaOtorgamiento()), y + PASO_FILA);
    y = filaFecha("Fecha de emisión:", FormatoFechas.larga(d.fechaEmision()), y + PASO_FILA);
    y = filaOpcional("Auditoría de Seguimiento 1 programada antes de:", d.seguimiento1(), y);
    y = filaOpcional("Auditoría de Seguimiento 2 programada antes de:", d.seguimiento2(), y);
    filaFecha("Fecha de expiración, la renovación debe ser programada antes de:", "**" + FormatoFechas.larga(d.fechaExpiracion()),
        y + PASO_FILA);
  }

  private float filaOpcional(String etiqueta, LocalDate fecha, float yAnterior) {
    return fecha == null ? yAnterior : filaFecha(etiqueta, "*" + FormatoFechas.larga(fecha), yAnterior + PASO_FILA);
  }

  /**el original imprime la fecha un poco más arriba que su etiqueta; se conserva ese desfase*/
  private float filaFecha(String etiqueta, String valor, float y) {
    l.texto(etiqueta, fila, X_ETIQUETAS, y, IZQUIERDA);
    l.texto(valor, fila, X_VALORES, y + DESFASE_VALORES, DERECHA);
    return y;
  }

  /**el fondo trae el logo de SAE: para INACAL se tapa con blanco (el fondo es liso ahí) y se pone el de INACAL*/
  private void dibujarAcreditacion(CertificadoEmpresarial d) {
    l.texto("ACREDITADO POR", acreditadoPor, CENTRO_PAGINA, Y_ACREDITADO_POR, CENTRO);
    if (d.acreditadora() == Acreditadora.INACAL) {
      l.rectangulo(Paleta.BLANCO, PARCHE_X, PARCHE_Y, PARCHE_ANCHO, PARCHE_ALTO);
      var logo = c.imagen("imagenes/acreditadora-inacal.png");
      float alto = LOGO_INACAL_ANCHO * logo.getHeight() / logo.getWidth();
      l.imagen(logo, CENTRO_PAGINA - LOGO_INACAL_ANCHO / 2, LOGO_INACAL_Y, LOGO_INACAL_ANCHO, alto);
    }
    if (hay(d.numeroAcreditacion())) {
      l.linea(List.of(new Trozo("Acreditación N° ", acreditacion), new Trozo(d.numeroAcreditacion(), acreditacionNumero)),
          CENTRO_PAGINA, Y_ACREDITACION, CENTRO);
    }
    l.texto("CERTIFICACIÓN DE SISTEMAS DE", acreditacion, CENTRO_PAGINA, Y_ACREDITACION + PASO_ACREDITACION, CENTRO);
    // entre el logo de IQC y la firma hay ANCHO_ACREDITACION pt: el nombre más largo del catálogo se achica o se parte
    l.lineaQueCabe(d.sistemaGestion().replaceFirst("^Sistema de ", "").toUpperCase(Locale.ROOT), acreditacion, MINIMO_ACREDITACION,
        CENTRO_PAGINA, Y_ACREDITACION + 2 * PASO_ACREDITACION, ANCHO_ACREDITACION, PASO_ACREDITACION, CENTRO);
  }

  private void dibujarFirma(CertificadoEmpresarial d) {
    if (d.firmante().conFirma()) {
      var imagen = c.imagen("imagenes/firma-gerencia.png");
      l.imagen(imagen, FIRMA_X, FIRMA_Y, FIRMA_ANCHO, FIRMA_ANCHO * imagen.getHeight() / imagen.getWidth());
    }
    l.texto(d.firmante().nombre(), firmante, CENTRO_FIRMA, Y_FIRMANTE, CENTRO);
    l.texto(d.firmante().empresa(), firmante, CENTRO_FIRMA, Y_EMPRESA, CENTRO);
    l.texto(d.firmante().cargo(), firmaCargo, CENTRO_FIRMA, Y_CARGO, CENTRO);
  }

  private void dibujarPie(CertificadoEmpresarial d) {
    l.linea(List.of(new Trozo("Para verificar el certificado, ingresar a: ", pie), new Trozo(d.urlVerificacion(), pieLigero)),
        X_PIE, Y_VERIFICACION, IZQUIERDA);
    if (!d.ubicaciones().isEmpty()) {
      l.texto("Ubicación:", pie, X_PIE, Y_UBICACION, IZQUIERDA);
      for (int i = 0; i < d.ubicaciones().size(); i++) {
        l.texto(d.ubicaciones().get(i), pieLigero, X_UBICACION, Y_UBICACION + i * PASO_UBICACION, IZQUIERDA);
      }
    }
    if (hay(d.contacto())) {
      l.linea(List.of(new Trozo("Contactos: ", pie), new Trozo(d.contacto(), pieLigero)), X_PIE, Y_CONTACTO, IZQUIERDA);
    }
    l.linea(List.of(new Trozo("Nota*: ", pie), new Trozo("La vigencia de la certificación otorgada está sujeta a las auditorías "
        + "de seguimiento satisfactorias", pieLigero)), X_PIE, Y_NOTA_1, IZQUIERDA);
    l.texto("y previo a las fechas del ciclo de certificación.", pieLigero, X_PIE, Y_NOTA_1B, IZQUIERDA);
    l.linea(List.of(new Trozo("Nota**: ", pie), new Trozo("Este documento podrá renovarse por la organización certificada "
        + "siempre y cuando se realice la", pieLigero)), X_PIE, Y_NOTA_2, IZQUIERDA);
    l.texto("auditoría de renovación dentro de los 2 meses previos a la fecha de expiración del presente certificado.",
        pieLigero, X_PIE, Y_NOTA_2B, IZQUIERDA);
    l.texto(FormatoFechas.corta(d.fechaEmision()) + " – V.%02d".formatted(d.numeroVersion()), version, DERECHA_PIE, Y_VERSION, DERECHA);
    l.texto("Cualquier versión anterior a la fecha de", validez, DERECHA_PIE, Y_VALIDEZ_1, DERECHA);
    l.texto("este certificado no tiene validez.", validez, DERECHA_PIE, Y_VALIDEZ_2, DERECHA);
    l.texto(FORMATO, formato, X_FORMATO, Y_FORMATO, IZQUIERDA);
  }

  /**separación entre letras para que la palabra mida el ancho del original (el título va espaciado)*/
  private float espaciadoParaAncho(String texto, Estilo estilo, float anchoObjetivo) {
    return (anchoObjetivo - l.ancho(texto, estilo)) / (texto.length() - 1);
  }

  private static boolean hay(String texto) { return texto != null && !texto.isBlank(); }
}
