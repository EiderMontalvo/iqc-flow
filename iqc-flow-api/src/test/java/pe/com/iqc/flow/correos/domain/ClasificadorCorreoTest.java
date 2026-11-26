package pe.com.iqc.flow.correos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.correos.TipoCorreo;
import pe.com.iqc.flow.correos.domain.ClasificadorCorreo.Entrada;

/**casos reales de lo que llega a los buzones de IQC, en el español de Perú*/
class ClasificadorCorreoTest {
  private static TipoCorreo tipo(String asunto, String texto) { return ClasificadorCorreo.clasificar(new Entrada(asunto, texto, List.of(), false)).tipo(); }

  @Test
  void unaSolicitudDeCotizacionSeReconoceAunConTildesYMayusculas() {
    assertThat(tipo("Solicitud de cotización ISO 9001 - Constructora Andina SAC", "Buenos días, deseamos certificar nuestro sistema de gestión.")).isEqualTo(TipoCorreo.COTIZACION);
    assertThat(tipo("COTIZACIÓN URGENTE", "")).isEqualTo(TipoCorreo.COTIZACION);
    assertThat(tipo("Presupuesto para certificación ISO 45001", "")).isEqualTo(TipoCorreo.COTIZACION);
  }

  @Test
  void losPagosYFacturasSeReconocen() {
    assertThat(tipo("RE: Factura F001-00234 - pago realizado", "Adjunto el voucher del depósito.")).isEqualTo(TipoCorreo.PAGO);
    assertThat(tipo("Constancia de detracción", "")).isEqualTo(TipoCorreo.PAGO);
    assertThat(tipo("Envío de comprobante", "Se realizó la transferencia a su cuenta bancaria.")).isEqualTo(TipoCorreo.PAGO);
  }

  @Test
  void laProgramacionDeAuditoriasSeReconoce() {
    assertThat(tipo("Programación de auditoría de vigilancia 2026", "")).isEqualTo(TipoCorreo.AUDITORIA);
    assertThat(tipo("Cronograma etapa 2", "Les enviamos las fechas para la auditoría.")).isEqualTo(TipoCorreo.AUDITORIA);
    assertThat(tipo("Respuesta a no conformidades", "Adjuntamos las acciones correctivas.")).isEqualTo(TipoCorreo.AUDITORIA);
  }

  @Test
  void lasConsultasDeUnCertificadoSeReconocen() {
    assertThat(tipo("Verificación de certificado PE-SAE-01520240132", "")).isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo("Código de verificación", "No aparece mi certificado al escanear el QR.")).isEqualTo(TipoCorreo.CERTIFICADO);
  }

  @Test
  void unReclamoGanaAUnaMencionDeCertificado() {
    assertThat(tipo("Reclamo por demora en la entrega del certificado", "Estamos molestos: la demora es inaceptable.")).isEqualTo(TipoCorreo.RECLAMO);
    assertThat(tipo("Queja formal", "Exigimos una respuesta.")).isEqualTo(TipoCorreo.RECLAMO);
  }

  @Test
  void sinEvidenciaClaraEsOtro() {
    assertThat(tipo("Hola", "Buen día, gracias por su atención.")).isEqualTo(TipoCorreo.OTRO);
    assertThat(tipo("", "")).isEqualTo(TipoCorreo.OTRO);
    assertThat(tipo("Saludos", "Quería saber si tienen un momento para conversar y de paso la cotizacion.")).isEqualTo(TipoCorreo.OTRO);
  }

  @Test
  void noConfundePalabrasQueSoloCompartenLetras() {
    assertThat(tipo("Corte de energía: apagón en la sede", "El apagón duró dos horas.")).isEqualTo(TipoCorreo.OTRO);
    assertThat(tipo("Nueva página web", "Les compartimos la página.")).isEqualTo(TipoCorreo.OTRO);
  }

  @Test
  void unEmpateEntreDosTiposEsOtroConSuMotivo() {
    var c = ClasificadorCorreo.clasificar(new Entrada("Factura y auditoría", "", List.of(), false));
    assertThat(c.tipo()).isEqualTo(TipoCorreo.OTRO);
    assertThat(c.motivo()).contains("más de un tipo");
  }

  @Test
  void losNombresDeLosAdjuntosTambienCuentan() {
    var c = ClasificadorCorreo.clasificar(new Entrada("Documentos", "", List.of("Factura-001.pdf", "Voucher.jpg"), false));
    assertThat(c.tipo()).isEqualTo(TipoCorreo.PAGO);
    assertThat(c.motivo()).contains("adjunto");
  }

  @Test
  void unRemitenteConocidoBajaLaExigenciaPeroNoInventa() {
    String cuerpo = "Necesitamos la cotizacion y el precio del servicio.";
    assertThat(ClasificadorCorreo.clasificar(new Entrada("Consulta", cuerpo, List.of(), false)).tipo()).isEqualTo(TipoCorreo.OTRO);
    var conocido = ClasificadorCorreo.clasificar(new Entrada("Consulta", cuerpo, List.of(), true));
    assertThat(conocido.tipo()).isEqualTo(TipoCorreo.COTIZACION);
    assertThat(conocido.motivo()).contains("remitente conocido");
    assertThat(ClasificadorCorreo.clasificar(new Entrada("Consulta", "Hola, gracias.", List.of(), true)).tipo()).isEqualTo(TipoCorreo.OTRO);
  }

  @Test
  void elMotivoDiceQueSeEncontro() {
    assertThat(ClasificadorCorreo.clasificar(new Entrada("Solicitud de cotización", "", List.of(), false)).motivo()).contains("cotiz").contains("asunto");
  }

  // ---- PUBLICIDAD, validación de certificados con erratas, reglas por dominio (asuntos sintéticos) ----

  private static TipoCorreo tipo(String asunto, String texto, String remitente, boolean masivo, boolean cliente, String... adjuntos) {
    return ClasificadorCorreo.clasificar(new Entrada(asunto, texto, List.of(adjuntos), cliente, masivo, remitente)).tipo();
  }

  @Test
  void laPublicidadDeBasesDeDatosYOfertasSeReconoceSolaPorSuAsunto() {
    assertThat(tipo("Haz crecer tus ventas con nuestra Base de Datos de Empresas Perú por solo S/119", "Pide tu demo gratis.", "ventas@bases-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("¡¡OFERTA IMPERDIBLE!! DESCUENTO DEL 50% SOLO HOY", "", "promo@tienda-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("Pide tu demo gratis hoy", "", "hola@software-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
  }

  @Test
  void laPublicidadConEmojiYFrasesComercialesSeReconoce() {
    assertThat(tipo("🎄 Canastas navideñas personalizadas para tu empresa", "Aprovecha el descuento por volumen.", "pedidos@regalos-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("Reclutamiento de personal: perfiles evaluados para tu empresa", "Escríbenos por WhatsApp.", "rrhh@agencia-ejemplo.pe", true, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("Tecnología italiana para tu planta", "Mira nuestro catálogo. Para cancelar suscripción responde BAJA.", "info@maquinas-ejemplo.it", false, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
  }

  @Test
  void lasPreguntasDeVentaCuentanSoloConOtraSenalPorqueUnProspectoRealTambienPregunta() {
    assertThat(tipo("Su empresa realiza controles de alcohol al personal?", "Agenda tu demo gratis. Para darse de baja responde BAJA.", "ventas@alcohol-ejemplo.pe", false, false))
        .isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("¿Su empresa necesita ayuda con la contabilidad?", "", "alguien@correo-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.OTRO);
    assertThat(tipo("Su empresa ofrece certificación ISO 9001?", "Quisiera conocer el costo y el plazo.", "prospecto@empresa-ejemplo.pe", false, false)).isNotEqualTo(TipoCorreo.PUBLICIDAD);
  }

  @Test
  void lasCabecerasMasivasYLaFraseDeBajaSumanYUnAdjuntoPromocionalAyuda() {
    assertThat(tipo("Novedades de febrero", "Para dejar de recibir estos correos, haz clic abajo.", "news@boletin-ejemplo.pe", true, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("Nuestra nueva línea", "Hola.", "info@marca-ejemplo.pe", true, false, "Brochure-2026.pdf")).isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("Novedades de febrero", "Hola, buen día.", "persona@correo-ejemplo.pe", false, false)).as("sin cabeceras masivas ni frases, es OTRO").isEqualTo(TipoCorreo.OTRO);
  }

  @Test
  void unAsuntoQueGritaEnMayusculasConSignosCuentaPeroUnOficioEnMayusculasNo() {
    assertThat(tipo("ÚLTIMOS DÍAS!! GRAN LIQUIDACIÓN", "", "tienda@liquida-ejemplo.pe", true, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("COMUNICADO DE LA OFICINA DE ADMINISTRACION", "Se informa a los proveedores.", "oficina@entidad-ejemplo.gob.pe", false, false)).isEqualTo(TipoCorreo.OTRO);
  }

  @Test
  void unClienteConocidoNuncaEsPublicidadSalvoEnvioMasivoConFraseDeBaja() {
    assertThat(tipo("Oferta de servicios y descuento especial", "Le escribo para ofrecerle un descuento.", "ana@cliente-ejemplo.pe", false, true)).isNotEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("Oferta especial de fin de año", "Descuento. Para darse de baja responda BAJA.", "ana@cliente-ejemplo.pe", true, true)).isEqualTo(TipoCorreo.PUBLICIDAD);
    assertThat(tipo("Cotización COT-2026-0007", "Adjunto la cotización.", "ana@cliente-ejemplo.pe", true, true)).isEqualTo(TipoCorreo.COTIZACION);
  }

  @Test
  void unCorreoMasivoConUnaSenalFuerteDeOtroTipoSeQuedaConEseTipo() {
    // alerta automática de una plataforma de certificados: viaja con cabeceras masivas y frase de baja, pero el dominio manda
    assertThat(tipo("Email Guard - IAF", "Your query was registered. Unsubscribe here.", "noreply@iafcertsearch.org", true, false)).isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo("Email Guard - IAF", "", "alertas@mail.iafcertsearch.org", true, false)).as("también desde un subdominio").isEqualTo(TipoCorreo.CERTIFICADO);
    // un aviso de factura con cabeceras de lista no es publicidad: otro tipo ya tiene una palabra en el asunto y no hay oferta
    assertThat(tipo("Factura electrónica F001-889", "Para dejar de recibir estos avisos, haz clic abajo.", "no-reply@facturacion-ejemplo.pe", true, false)).isEqualTo(TipoCorreo.PAGO);
    // y la publicidad que se disfraza de certificado sigue siendo publicidad
    assertThat(tipo("CERTIFICADO ISO 9001 EN 30 DÍAS!! OFERTA", "Descuento. Para darse de baja responda BAJA.", "ventas@consultora-ejemplo.pe", true, false)).isEqualTo(TipoCorreo.PUBLICIDAD);
  }

  @Test
  void elMismoAsuntoDeOtroDominioNoGanaPorDominio() {
    assertThat(tipo("Email Guard - IAF", "", "noreply@otro-dominio-ejemplo.com", false, false)).isEqualTo(TipoCorreo.OTRO);
  }

  @Test
  void laValidacionDeUnCertificadoSeReconoceAunConErratas() {
    assertThat(tipo("RV: Solicito Validación de certiticado ISO", "Adjunto el documento.", "mesa@entidad-ejemplo.gob.pe", false, false)).isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo("RV: Solicito Validación de certiticado ISO", "", "persona@correo-ejemplo.pe", false, false)).as("sin dominio de gobierno también").isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo("Validacion de certifcado ISO 9001", "", "x@empresa-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo("Verificacion de certificaco", "", "x@empresa-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo("Autenticidad del certifiacdo", "", "x@empresa-ejemplo.pe", false, false)).as("dos letras cambiadas de lugar").isEqualTo(TipoCorreo.CERTIFICADO);
  }

  @Test
  void verificarSinUnCertificadoNoEsCertificado() {
    assertThat(tipo("Verifique su cuenta", "Haga clic aquí para continuar.", "seguridad@banco-falso-ejemplo.com", false, false)).isEqualTo(TipoCorreo.OTRO);
    assertThat(tipo("Validar datos", "", "x@empresa-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.OTRO);
  }

  @Test
  void laFiscalizacionPosteriorDeUnaLicitacionEsCertificadoYLaLicitacionSolaEsCotizacion() {
    String asunto = "FISCALIZACION POSTERIOR - LICITACIÓN PÚBLICA ABREVIADA DE OBRAS Nº 045-2026";
    assertThat(tipo(asunto, "Se solicita verificar la autenticidad del certificado ISO 9001 presentado.", "logistica@municipalidad-ejemplo.gob.pe", false, false)).isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo(asunto, "", "logistica@municipalidad-ejemplo.gob.pe", false, false)).isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo(asunto, "", "alguien@empresa-ejemplo.com", false, false)).as("la frase pesa más que «licitación» aunque no sea de gobierno").isEqualTo(TipoCorreo.CERTIFICADO);
    assertThat(tipo("LICITACIÓN PÚBLICA N° 12-2026 - Invitación a presentar propuesta", "", "alguien@empresa-ejemplo.com", false, false)).isEqualTo(TipoCorreo.COTIZACION);
  }

  @Test
  void unDominioDeGobiernoSinPalabrasDeVerificacionNoEsCertificado() {
    assertThat(tipo("Invitación a la ceremonia de aniversario", "", "protocolo@entidad-ejemplo.gob.pe", false, false)).isEqualTo(TipoCorreo.OTRO);
  }

  @Test
  void tildesMayusculasYEmojiNoCambianElTipo() {
    assertThat(tipo("COTIZACIÓN ISO 9001 — PRESUPUESTO", "", "x@empresa-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.COTIZACION);
    assertThat(tipo("💼 Solicitud de cotización", "", "x@empresa-ejemplo.pe", false, false)).as("un emoji solo no vuelve publicidad una cotización").isEqualTo(TipoCorreo.COTIZACION);
    assertThat(tipo("Reclamo urgente!!", "Estamos molestos.", "x@empresa-ejemplo.pe", false, false)).isEqualTo(TipoCorreo.RECLAMO);
  }

  @Test
  void elMotivoDeLaPublicidadYDelDominioDicenPorQue() {
    var publicidad = ClasificadorCorreo.clasificar(new Entrada("Pide tu demo gratis hoy", "", List.of(), false, true, "a@b-ejemplo.pe"));
    assertThat(publicidad.motivo()).startsWith("Parece publicidad");
    var dominio = ClasificadorCorreo.clasificar(new Entrada("Aviso", "", List.of(), false, false, "x@iafcertsearch.org"));
    assertThat(dominio.motivo()).contains("remitente de iafcertsearch.org");
  }

  @Test
  void sinRemitenteNiCabecerasElClasificadorSigueFuncionando() {
    assertThat(ClasificadorCorreo.clasificar(new Entrada("Solicitud de cotización", "", List.of(), false)).tipo()).isEqualTo(TipoCorreo.COTIZACION);
    assertThat(tipo("Hola", "Buen día", "sin-arroba", false, false)).isEqualTo(TipoCorreo.OTRO);
    assertThat(tipo(null, null, null, false, false)).isEqualTo(TipoCorreo.OTRO);
  }
}
