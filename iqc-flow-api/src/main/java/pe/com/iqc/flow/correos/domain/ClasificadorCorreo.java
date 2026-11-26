package pe.com.iqc.flow.correos.domain;

import static pe.com.iqc.flow.correos.domain.NormalizadorTextoCorreo.casiIguales;
import static pe.com.iqc.flow.correos.domain.NormalizadorTextoCorreo.normalizar;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import pe.com.iqc.flow.correos.TipoCorreo;

/**
 *decide de qué trata un correo con reglas de palabras clave sobre el asunto, el cuerpo y los nombres de los adjuntos (sin tildes ni
 *mayúsculas, y tolerando una errata en las palabras largas), más reglas por dominio del remitente y señales de envío masivo.
 *Todas las reglas están en las constantes de abajo: se ajustan ahí, sin tocar la lógica.
 *
 *<p><b>Cómo se decide</b> (en este orden):
 *<ol>
 *<li>Cada tipo suma puntos: una palabra en el asunto pesa 3, en un adjunto 2 y en el cuerpo 1 (hasta 3); una frase muy específica del
 *asunto pesa 5; una regla de dominio del remitente pesa 6.
 *<li>PUBLICIDAD solo gana si llega a {@value #UMBRAL} puntos y le saca al mejor de los otros tipos al menos {@value #MARGEN_PUBLICIDAD}.
 *Por eso un correo masivo con una señal fuerte de otro tipo (una alerta automática de un organismo, una regla de dominio) se queda con
 *ese tipo. Además, si otro tipo ya tiene una palabra en el asunto, PUBLICIDAD necesita al menos una señal de contenido (oferta, emoji,
 *grito, pregunta de venta, adjunto promocional): las cabeceras de lista y «darse de baja» solas no bastan, porque también las traen los
 *avisos legítimos de facturas y sistemas.
 *<li>Un remitente que es contacto de un cliente registrado nunca es PUBLICIDAD, salvo que además sea envío masivo y traiga una frase de baja
 *(«darse de baja», «unsubscribe»): su correo se trata como el de un cliente.
 *<li>Entre los demás tipos gana el de más puntos si llega al umbral ({@value #UMBRAL}; uno menos si el remitente es cliente conocido).
 *Un empate, o no llegar al umbral, da OTRO: que lo decida una persona.
 *</ol>
 */
public final class ClasificadorCorreo {
  private static final int PESO_ASUNTO = 3;
  private static final int PESO_ASUNTO_FUERTE = 5;
  private static final int PESO_ADJUNTO = 2;
  private static final int PESO_CUERPO = 1;
  private static final int TOPE_CUERPO = 3;
  private static final int PESO_DOMINIO = 6;
  private static final int UMBRAL = 3;
  private static final int MARGEN_PUBLICIDAD = 2;
  /**solo se analiza el principio del cuerpo: lo importante está arriba y así el costo no crece con correos enormes*/
  private static final int CUERPO_MAXIMO = 20_000;
  /**las palabras de al menos esta longitud toleran una errata*/
  private static final int LONGITUD_PARA_ERRATA = 8;

  /**
   *palabras por tipo. Un asterisco final es «empieza por» (cotiz* vale para cotización, cotizar, cotizaciones); sin él, la palabra o
   *frase completa. Van ya en minúsculas y sin tildes. `asuntoFuerte` son frases tan específicas que, en el asunto, pesan más
   */
  private static final Map<TipoCorreo, Reglas> REGLAS = new EnumMap<>(TipoCorreo.class);

  /**el remitente (su dominio) ya dice de qué trata; con `palabras`, solo si el correo menciona alguna. Pesa más que cualquier palabra suelta*/
  private static final List<ReglaDominio> REGLAS_DOMINIO = List.of(
      new ReglaDominio("iafcertsearch.org", TipoCorreo.CERTIFICADO, List.of()),
      new ReglaDominio("gob.pe", TipoCorreo.CERTIFICADO,
          List.of("fiscalizacion posterior", "validacion*", "verificacion*", "autenticidad", "validar", "verificar")));

  /**validar o verificar solo indica un certificado si además se habla de uno (si no, es «verifique su cuenta» y similares)*/
  private static final List<String> PALABRAS_DE_VERIFICACION = List.of("validacion*", "validar", "verificacion*", "verificar", "autenticidad", "fiscalizacion posterior",
      "constancia de certificacion");
  private static final List<String> OBJETOS_DE_CERTIFICADO = List.of("certificad*", "certificacion*", "iso", "iqc");

  /**frases de baja de las listas de envío masivo: casi nunca aparecen en un correo escrito a una persona*/
  private static final List<String> FRASES_DE_BAJA = List.of("darse de baja", "darte de baja", "cancelar suscripcion", "cancelar tu suscripcion", "cancelar su suscripcion",
      "anular suscripcion", "unsubscribe", "dejar de recibir", "no desea recibir");
  /**ofertas y captación: en el asunto o el cuerpo*/
  private static final List<String> FRASES_COMERCIALES_ASUNTO = List.of("por solo s/", "oferta*", "descuento*", "promocion*", "demo gratis", "gratis", "base de datos de empresas",
      "haz crecer tus ventas", "reclutamiento de personal", "canastas navidenas", "tecnologia italiana", "aprovecha", "no te lo pierdas", "ultimos dias", "webinar",
      "escribenos por whatsapp", "suscribete", "cupon*", "black friday", "cyber");
  private static final List<String> FRASES_COMERCIALES_CUERPO = List.of("por solo s/", "oferta*", "descuento*", "promocion*", "demo gratis", "gratis", "base de datos de empresas",
      "escribenos por whatsapp", "contactanos", "pide tu demo", "ver catalogo");
  private static final List<String> ADJUNTOS_PROMOCIONALES = List.of("brochure", "catalogo", "flyer", "promo*", "oferta*");
  private static final Pattern PREGUNTA_DE_VENTA = Pattern.compile("(?:tu|su|vuestra) (?:empresa|negocio|equipo)\\b[^?]*\\?");
  private static final Pattern GRITO = Pattern.compile("[!¡]");

  static {
    REGLAS.put(TipoCorreo.COTIZACION, new Reglas(
        List.of("cotiz*", "presupuesto*", "proforma", "propuesta economica", "solicitud de certificacion", "solicitud de servicio", "tarifa*", "costo de la certificacion",
            "requerimiento de servicio", "licitacion*"),
        List.of(),
        List.of("cotiz*", "presupuesto*", "precio*", "costo*", "tarifa*", "solicitud de certificacion", "deseamos certificar", "queremos certificar", "interesad*",
            "propuesta economica"),
        List.of("solicitud", "cotiz*", "requerimiento", "proforma")));
    REGLAS.put(TipoCorreo.PAGO, new Reglas(
        List.of("pago*", "factura*", "comprobante*", "voucher", "deposito*", "transferencia*", "detraccion*", "boleta*", "cobranza*", "abono*", "recibo*"),
        List.of(),
        List.of("factura*", "pago*", "voucher", "deposito*", "transferencia*", "detraccion*", "constancia de pago", "cuenta bancaria", "cancelar la factura", "comprobante de pago"),
        List.of("voucher", "factura*", "comprobante*", "pago*", "deposito*", "detraccion*")));
    REGLAS.put(TipoCorreo.AUDITORIA, new Reglas(
        List.of("auditoria*", "vigilancia*", "cronograma*", "plan de auditoria", "etapa 1", "etapa 2", "reprogramacion", "reprogramar", "no conformidad*", "hallazgo*"),
        List.of(),
        List.of("auditoria*", "auditor", "auditores", "vigilancia*", "cronograma*", "etapa 1", "etapa 2", "programar", "no conformidad*", "acciones correctivas"),
        List.of("plan de auditoria", "informe de auditoria", "cronograma*", "acciones correctivas", "no conformidad*")));
    REGLAS.put(TipoCorreo.CERTIFICADO, new Reglas(
        List.of("certificado*", "codigo de verificacion", "verificacion de certificado", "duplicado", "constancia de certificacion", "vigencia"),
        List.of("fiscalizacion posterior", "validacion de certificado", "verificacion de certificado", "autenticidad del certificado"),
        List.of("certificado*", "codigo qr", "codigo de verificacion", "verificar", "vigencia", "no aparece", "correccion de datos", "error en el certificado"),
        List.of("certificado*")));
    REGLAS.put(TipoCorreo.RECLAMO, new Reglas(
        List.of("reclamo*", "queja*", "disconform*", "inconform*", "insatisf*", "denuncia*", "apelacion*", "apelar", "mala atencion", "mal servicio"),
        List.of(),
        List.of("reclamo*", "queja*", "molest*", "inconform*", "insatisf*", "demora*", "indecopi", "apelacion*", "inaceptable", "mala atencion", "exijo", "no hemos recibido"),
        List.of("reclamo*", "queja*", "apelacion*")));
  }

  private ClasificadorCorreo() {
  }

  /**
   *remitenteEsCliente: el remitente es un contacto de un cliente registrado; masivo: sus cabeceras son de envío masivo;
   *remitente: su dirección (para las reglas por dominio), puede ser nula
   */
  public record Entrada(String asunto, String texto, List<String> nombresAdjuntos, boolean remitenteEsCliente, boolean masivo, String remitente) {
    public Entrada {
      nombresAdjuntos = nombresAdjuntos == null ? List.of() : List.copyOf(nombresAdjuntos);
    }

    public Entrada(String asunto, String texto, List<String> nombresAdjuntos, boolean remitenteEsCliente) {
      this(asunto, texto, nombresAdjuntos, remitenteEsCliente, false, null);
    }
  }

  /**motivo: una frase corta que dice por qué se eligió ese tipo, para mostrarla junto a él*/
  public record Clasificacion(TipoCorreo tipo, String motivo) {
  }

  public static Clasificacion clasificar(Entrada entrada) {
    Texto asunto = new Texto(entrada.asunto());
    Texto cuerpo = new Texto(entrada.texto() == null || entrada.texto().length() <= CUERPO_MAXIMO ? entrada.texto() : entrada.texto().substring(0, CUERPO_MAXIMO));
    Texto adjuntos = new Texto(String.join(" | ", entrada.nombresAdjuntos()));
    String dominio = dominio(entrada.remitente());
    int umbral = entrada.remitenteEsCliente() ? UMBRAL - 1 : UMBRAL;

    TipoCorreo ganador = null;
    int mejor = 0, mejorEnAsunto = 0;
    boolean empate = false;
    List<String> pistas = List.of();
    for (var regla : REGLAS.entrySet()) {
      List<String> encontradas = new ArrayList<>();
      TipoCorreo tipo = regla.getKey();
      Reglas r = regla.getValue();
      int enAsunto = puntos(r.asunto(), asunto, PESO_ASUNTO, Integer.MAX_VALUE, encontradas, "asunto") + puntos(r.asuntoFuerte(), asunto, PESO_ASUNTO_FUERTE, Integer.MAX_VALUE, encontradas, "asunto");
      int total = enAsunto + puntos(r.adjuntos(), adjuntos, PESO_ADJUNTO, Integer.MAX_VALUE, encontradas, "adjunto") + puntos(r.cuerpo(), cuerpo, PESO_CUERPO, TOPE_CUERPO, encontradas, "texto");
      total += porDominio(tipo, dominio, asunto, cuerpo, encontradas);
      if (tipo == TipoCorreo.CERTIFICADO) total += verificacionDeCertificado(asunto, cuerpo, encontradas);
      if (total > mejor || (total == mejor && enAsunto > mejorEnAsunto)) {
        ganador = tipo; mejor = total; mejorEnAsunto = enAsunto; empate = false; pistas = encontradas;
      } else if (total == mejor && total > 0 && enAsunto == mejorEnAsunto) {
        empate = true;
      }
    }

    Publicidad publicidad = puntosDePublicidad(entrada, asunto, cuerpo, adjuntos);
    boolean evidenciaDeContenido = publicidad.contenido() > 0 || mejorEnAsunto == 0;
    if (publicidad.total() >= UMBRAL && publicidad.total() >= mejor + MARGEN_PUBLICIDAD && evidenciaDeContenido) {
      return new Clasificacion(TipoCorreo.PUBLICIDAD, motivo("Parece publicidad: ", publicidad.pistas(), false));
    }

    if (ganador == null || mejor < umbral) return new Clasificacion(TipoCorreo.OTRO, "Sin palabras que lo indiquen con claridad");
    if (empate) return new Clasificacion(TipoCorreo.OTRO, "Podría ser de más de un tipo");
    return new Clasificacion(ganador, motivo("Menciona ", pistas, entrada.remitenteEsCliente()));
  }

  private static String motivo(String inicio, List<String> pistas, boolean remitenteConocido) {
    String texto = inicio + String.join(", ", pistas.stream().limit(3).toList());
    return remitenteConocido ? texto + " (remitente conocido)" : texto;
  }

  /**
   *puntos de PUBLICIDAD: cabeceras de envío masivo, frases de baja, ofertas, emoji o gritos en el asunto y la pregunta de venta
   *(«¿Su empresa …?», que solo cuenta si ya hay otra señal, porque un cliente nuevo también pregunta así). Un contacto de un cliente
   *solo suma si es envío masivo con frase de baja
   */
  private static Publicidad puntosDePublicidad(Entrada e, Texto asunto, Texto cuerpo, Texto adjuntos) {
    boolean baja = hay(FRASES_DE_BAJA, asunto) || hay(FRASES_DE_BAJA, cuerpo);
    if (e.remitenteEsCliente() && !(e.masivo() && baja)) return new Publicidad(0, 0, List.of());
    List<String> pistas = new ArrayList<>();
    int cabeceras = 0, contenido = 0;
    if (e.masivo()) { cabeceras += 4; pistas.add("cabeceras de envío masivo"); }
    if (baja) { cabeceras += 3; pistas.add("frase de baja de lista"); }
    contenido += puntos(FRASES_COMERCIALES_ASUNTO, asunto, PESO_ASUNTO, Integer.MAX_VALUE, pistas, "asunto");
    contenido += puntos(FRASES_COMERCIALES_CUERPO, cuerpo, PESO_CUERPO, TOPE_CUERPO, pistas, "texto");
    contenido += puntos(ADJUNTOS_PROMOCIONALES, adjuntos, PESO_ADJUNTO, 1, pistas, "adjunto");
    if (tieneEmoji(e.asunto())) { contenido += 2; pistas.add("emoji en el asunto"); }
    if (esGrito(e.asunto())) { contenido += 2; pistas.add("asunto en mayúsculas con signos"); }
    if (cabeceras + contenido >= 1 && PREGUNTA_DE_VENTA.matcher(asunto.normal()).find()) { contenido += PESO_ASUNTO; pistas.add("pregunta de venta en el asunto"); }
    return new Publicidad(cabeceras + contenido, contenido, pistas);
  }

  /**`contenido` son los puntos que vienen de lo que dice el correo (no de sus cabeceras ni de la frase de baja)*/
  private record Publicidad(int total, int contenido, List<String> pistas) {
  }

  /**puntos por la regla de dominio de este tipo, si el remitente es de ese dominio y (cuando la regla lo pide) el correo menciona alguna de sus palabras*/
  private static int porDominio(TipoCorreo tipo, String dominio, Texto asunto, Texto cuerpo, List<String> pistas) {
    if (dominio == null) return 0;
    for (ReglaDominio r : REGLAS_DOMINIO) {
      if (r.tipo() != tipo || !(dominio.equals(r.dominio()) || dominio.endsWith("." + r.dominio()))) continue;
      if (r.palabras().isEmpty() || hay(r.palabras(), asunto) || hay(r.palabras(), cuerpo)) {
        pistas.add("remitente de " + r.dominio());
        return PESO_DOMINIO;
      }
    }
    return 0;
  }

  /**validar o verificar junto a un certificado, una norma ISO o IQC: 4 puntos si va en el asunto, 2 si solo en el cuerpo*/
  private static int verificacionDeCertificado(Texto asunto, Texto cuerpo, List<String> pistas) {
    boolean objeto = hay(OBJETOS_DE_CERTIFICADO, asunto) || hay(OBJETOS_DE_CERTIFICADO, cuerpo);
    if (!objeto) return 0;
    if (hay(PALABRAS_DE_VERIFICACION, asunto)) { pistas.add("validación o verificación de un certificado en asunto"); return 4; }
    if (hay(PALABRAS_DE_VERIFICACION, cuerpo)) { pistas.add("validación o verificación de un certificado en texto"); return 2; }
    return 0;
  }

  /**suma los puntos de las palabras encontradas (cada una cuenta una vez); `maximo` limita cuántas coincidencias puede aportar un grupo*/
  private static int puntos(List<String> palabras, Texto texto, int peso, int maximo, List<String> encontradas, String donde) {
    int aciertos = 0;
    for (String palabra : palabras) {
      if (aciertos >= maximo) break;
      if (coincide(palabra, texto)) { aciertos++; encontradas.add("«" + palabra.replace("*", "") + "» en " + donde); }
    }
    return aciertos * peso;
  }

  private static boolean hay(List<String> palabras, Texto texto) {
    for (String palabra : palabras) if (coincide(palabra, texto)) return true;
    return false;
  }

  /**exacta (palabra o frase completa, o empezando por, si lleva *) y, para una sola palabra de 8 letras o más, con una errata*/
  private static boolean coincide(String palabra, Texto texto) {
    if (texto.normal().isEmpty()) return false;
    boolean prefijo = palabra.endsWith("*");
    String base = prefijo ? palabra.substring(0, palabra.length() - 1) : palabra;
    if (PATRONES.computeIfAbsent(palabra, p -> Pattern.compile("(?<![a-z0-9])" + Pattern.quote(base) + (prefijo ? "" : "(?![a-z0-9])"))).matcher(texto.normal()).find()) return true;
    if (base.length() < LONGITUD_PARA_ERRATA || base.indexOf(' ') >= 0) return false;
    for (String p : texto.palabras()) {
      if (prefijo ? empiezaConErrata(p, base) : casiIguales(p, base)) return true;
    }
    return false;
  }

  private static boolean empiezaConErrata(String palabra, String raiz) {
    for (int largo = raiz.length() - 1; largo <= raiz.length() + 1; largo++) {
      if (palabra.length() >= largo && casiIguales(palabra.substring(0, largo), raiz)) return true;
    }
    return false;
  }

  private static boolean tieneEmoji(String texto) {
    if (texto == null) return false;
    return texto.codePoints().anyMatch(c -> (c >= 0x1F300 && c <= 0x1FAFF) || (c >= 0x2600 && c <= 0x27BF) || c == 0x2B50 || c == 0x2B06);
  }

  /**un asunto que grita: casi todo en mayúsculas (12 letras o más) y con signos de admiración. En mayúsculas solo, como los oficios de un organismo, no cuenta*/
  private static boolean esGrito(String asunto) {
    if (asunto == null || !GRITO.matcher(asunto).find()) return false;
    long letras = asunto.chars().filter(Character::isLetter).count();
    long mayusculas = asunto.chars().filter(Character::isUpperCase).count();
    return letras >= 12 && mayusculas >= letras * 0.7;
  }

  private static String dominio(String remitente) {
    if (remitente == null) return null;
    int arroba = remitente.lastIndexOf('@');
    return arroba < 0 || arroba == remitente.length() - 1 ? null : remitente.substring(arroba + 1).strip().toLowerCase(Locale.ROOT);
  }

  private static final Map<String, Pattern> PATRONES = new ConcurrentHashMap<>();

  /**un texto ya normalizado y partido en palabras, para no repetirlo por cada regla*/
  private record Texto(String normal, List<String> palabras) {
    Texto(String original) { this(normalizar(original), NormalizadorTextoCorreo.palabras(normalizar(original))); }
  }

  private record Reglas(List<String> asunto, List<String> asuntoFuerte, List<String> cuerpo, List<String> adjuntos) {
  }

  private record ReglaDominio(String dominio, TipoCorreo tipo, List<String> palabras) {
  }
}
