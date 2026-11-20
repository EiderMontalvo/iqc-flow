package pe.com.iqc.flow.certificaciones.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 *genera el PNG del QR. Con logo, usa la corrección de errores más alta (H, hasta 30 % del código recuperable) y el
 *logo ocupa como máximo un 20 % del ancho, sobre un recuadro blanco, para que el QR se siga leyendo
 */
public final class GeneradorQr {
  public static final int TAMANO_MINIMO = 160;
  public static final int TAMANO_MAXIMO = 2000;
  private static final double PROPORCION_LOGO = 0.20;

  private GeneradorQr() {
  }

  public static byte[] png(String contenido, int tamano, byte[] logo) {
    int lado = Math.max(TAMANO_MINIMO, Math.min(tamano, TAMANO_MAXIMO));
    Map<EncodeHintType, Object> opciones = Map.of(
        EncodeHintType.ERROR_CORRECTION, logo == null ? ErrorCorrectionLevel.M : ErrorCorrectionLevel.H,
        EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
        EncodeHintType.MARGIN, 2);
    BitMatrix matriz;
    try {
      matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, lado, lado, opciones);
    } catch (WriterException e) {
      throw new IllegalArgumentException("No se pudo generar el QR", e);
    }
    BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < lado; x++) {
      for (int y = 0; y < lado; y++) imagen.setRGB(x, y, matriz.get(x, y) ? 0x000000 : 0xFFFFFF);
    }
    if (logo != null) superponerLogo(imagen, logo);
    try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
      ImageIO.write(imagen, "png", salida);
      return salida.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void superponerLogo(BufferedImage qr, byte[] logo) {
    BufferedImage original;
    try {
      original = ImageIO.read(new ByteArrayInputStream(logo));
    } catch (IOException e) {
      return;
    }
    if (original == null) return;
    int lado = qr.getWidth();
    int maximo = (int) (lado * PROPORCION_LOGO);
    double escala = Math.min((double) maximo / original.getWidth(), (double) maximo / original.getHeight());
    int ancho = Math.max(1, (int) (original.getWidth() * escala));
    int alto = Math.max(1, (int) (original.getHeight() * escala));
    int margen = Math.max(4, lado / 60);
    int x = (lado - ancho) / 2;
    int y = (lado - alto) / 2;
    Graphics2D g = qr.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setColor(Color.WHITE);
      g.fill(new RoundRectangle2D.Double(x - margen, y - margen, ancho + 2.0 * margen, alto + 2.0 * margen,
          margen * 2.0, margen * 2.0));
      g.drawImage(original, x, y, ancho, alto, null);
    } finally {
      g.dispose();
    }
  }
}
