package pe.com.iqc.flow.documentos.domain;

import java.util.Optional;

/**
 *tipos de archivo admitidos. Se reconocen por sus primeros bytes (firma del formato), no por la extensión
 *ni por el Content-Type que envía el navegador, que el usuario puede alterar
 */
public enum TipoArchivo {
  PDF("application/pdf", "pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
  PNG("image/png", "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
  JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

  private final String tipoContenido;
  private final String extension;
  private final byte[] firma;

  TipoArchivo(String tipoContenido, String extension, byte[] firma) {
    this.tipoContenido = tipoContenido;
    this.extension = extension;
    this.firma = firma;
  }

  public static Optional<TipoArchivo> detectar(byte[] inicio) {
    for (TipoArchivo tipo : values()) {
      if (inicio.length >= tipo.firma.length) {
        boolean coincide = true;
        for (int i = 0; i < tipo.firma.length && coincide; i++) coincide = inicio[i] == tipo.firma[i];
        if (coincide) return Optional.of(tipo);
      }
    }
    return Optional.empty();
  }

  public String tipoContenido() { return tipoContenido; }
  public String extension() { return extension; }
}
