package pe.com.iqc.flow.plantillas.infrastructure;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 *superficie de dibujo de una página. Todas las coordenadas van en puntos y con el origen arriba a la izquierda
 *(la «y» de un texto es su línea base), como las que se miden en el PDF original
 */
final class Lienzo implements AutoCloseable {
  enum Alineacion { IZQUIERDA, CENTRO, DERECHA }

  /**fragmento de texto con su estilo: permite mezclar negrita, color y regular en una misma línea o párrafo*/
  record Trozo(String texto, Estilo estilo) {
  }

  private final PDPageContentStream flujo;
  private final float alto;

  Lienzo(PDDocument documento, PDPage pagina) {
    try {
      this.flujo = new PDPageContentStream(documento, pagina);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    this.alto = pagina.getMediaBox().getHeight();
  }

  void fondo(PDFormXObject fondo) { hacer(() -> flujo.drawForm(fondo)); }

  void imagen(PDImageXObject imagen, float x, float arriba, float ancho, float altoImagen) {
    hacer(() -> flujo.drawImage(imagen, x, alto - arriba - altoImagen, ancho, altoImagen));
  }

  void rectangulo(Color color, float x, float arriba, float ancho, float altoRect) {
    hacer(() -> {
      flujo.setNonStrokingColor(color);
      flujo.addRect(x, alto - arriba - altoRect, ancho, altoRect);
      flujo.fill();
    });
  }

  float ancho(String texto, Estilo estilo) { return anchoDe(normalizar(texto, estilo.fuente()).strip(), estilo); }

  /**dibuja el texto con su línea base en «y»; «x» es el borde izquierdo, el centro o el borde derecho según la alineación*/
  void texto(String texto, Estilo estilo, float x, float y, Alineacion alineacion) {
    texto(texto, estilo, x, y, alineacion, 0);
  }

  /**igual que la anterior, con separación extra entre letras (el título «CERTIFICADO» va espaciado)*/
  void texto(String texto, Estilo estilo, float x, float y, Alineacion alineacion, float espaciado) {
    String t = normalizar(texto, estilo.fuente()).strip();
    if (t.isEmpty()) return;
    float ancho = anchoDe(t, estilo) + espaciado * (t.length() - 1) * estilo.escala() / 100f;
    escribir(t, estilo, inicio(x, ancho, alineacion), y, espaciado);
  }

  /**varios trozos seguidos en una sola línea; los espacios que separan un trozo de otro se conservan*/
  void linea(List<Trozo> trozos, float x, float y, Alineacion alineacion) {
    List<Trozo> limpios = trozos.stream().map(t -> new Trozo(normalizar(t.texto(), t.estilo().fuente()), t.estilo())).toList();
    float ancho = (float) limpios.stream().mapToDouble(t -> anchoDe(t.texto(), t.estilo())).sum();
    float cursor = inicio(x, ancho, alineacion);
    for (Trozo t : limpios) {
      escribir(t.texto(), t.estilo(), cursor, y, 0);
      cursor += anchoDe(t.texto(), t.estilo());
    }
  }

  /**
   *párrafo de trozos con estilos distintos, partido por palabras y, si se pide, justificado (la última línea queda a
   *la izquierda). Corta en {@code maxLineas}. Devuelve la línea base de la última línea escrita
   */
  float parrafo(List<Trozo> trozos, float x, float ancho, float y, float interlinea, boolean justificado, int maxLineas) {
    List<List<Trozo>> palabras = palabras(trozos);
    List<List<List<Trozo>>> lineas = new ArrayList<>();
    List<List<Trozo>> actual = new ArrayList<>();
    float usado = 0;
    for (List<Trozo> palabra : palabras) {
      float anchoPalabra = anchoPalabra(palabra);
      float espacio = actual.isEmpty() ? 0 : anchoDe(" ", palabra.get(0).estilo());
      if (!actual.isEmpty() && usado + espacio + anchoPalabra > ancho) {
        lineas.add(actual);
        actual = new ArrayList<>();
        espacio = 0;
        usado = 0;
      }
      actual.add(palabra);
      usado += espacio + anchoPalabra;
    }
    if (!actual.isEmpty()) lineas.add(actual);
    float base = y;
    int cuantas = Math.min(lineas.size(), maxLineas);
    for (int i = 0; i < cuantas; i++) {
      List<List<Trozo>> linea = lineas.get(i);
      boolean ultima = i == lineas.size() - 1;
      float suma = (float) linea.stream().mapToDouble(this::anchoPalabra).sum();
      float hueco = justificado && !ultima && linea.size() > 1 ? (ancho - suma) / (linea.size() - 1)
          : anchoDe(" ", linea.get(0).get(0).estilo());
      float cursor = x;
      for (List<Trozo> palabra : linea) {
        for (Trozo t : palabra) {
          escribir(t.texto(), t.estilo(), cursor, base, 0);
          cursor += anchoDe(t.texto(), t.estilo());
        }
        cursor += hueco;
      }
      if (i < cuantas - 1) base += interlinea;
    }
    return base;
  }

  /**parte el texto en líneas que caben en el ancho, cortando solo entre palabras*/
  List<String> ajustar(String texto, Estilo estilo, float anchoMaximo) {
    List<String> lineas = new ArrayList<>();
    StringBuilder actual = new StringBuilder();
    for (String palabra : normalizar(texto, estilo.fuente()).strip().split(" ")) {
      if (palabra.isEmpty()) continue;
      String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;
      if (!actual.isEmpty() && anchoDe(candidata, estilo) > anchoMaximo) {
        lineas.add(actual.toString());
        actual = new StringBuilder(palabra);
      } else {
        actual = new StringBuilder(candidata);
      }
    }
    if (!actual.isEmpty()) lineas.add(actual.toString());
    return lineas;
  }

  /**mayor estilo (bajando el tamaño de a medio punto hasta el mínimo) con el que el texto cabe en una línea*/
  Estilo queCabe(String texto, Estilo estilo, float minimo, float anchoMaximo) {
    Estilo actual = estilo;
    while (actual.tamano() > minimo && ancho(texto, actual) > anchoMaximo) actual = actual.conTamano(actual.tamano() - 0.5f);
    return actual;
  }

  /**
   *texto de una sola línea que nunca se sale de {@code anchoMaximo}: baja el cuerpo hasta {@code minimo} y, si aun así no
   *cabe, lo parte entre palabras en varias líneas (separadas {@code interlinea}) con la misma alineación. Una palabra más
   *ancha que el máximo se comprime en horizontal en lugar de desbordar. {@code x} es el borde izquierdo, el centro o el
   *borde derecho según la alineación. Devuelve cuántas líneas escribió (0 si no había texto), para que quien dibuja
   *pueda bajar lo que sigue
   */
  int lineaQueCabe(String texto, Estilo estilo, float minimo, float x, float y, float anchoMaximo, float interlinea,
      Alineacion alineacion) {
    if (texto == null || texto.isBlank()) return 0;
    Estilo reducido = queCabe(texto, estilo, minimo, anchoMaximo);
    List<String> lineas = ancho(texto, reducido) <= anchoMaximo ? List.of(texto) : ajustar(texto, reducido, anchoMaximo);
    for (int i = 0; i < lineas.size(); i++) {
      String linea = lineas.get(i);
      float exceso = ancho(linea, reducido);
      Estilo aplicado = exceso > anchoMaximo ? reducido.conEscala(reducido.escala() * anchoMaximo / exceso) : reducido;
      texto(linea, aplicado, x, y + i * interlinea, alineacion);
    }
    return lineas.size();
  }

  /**espacios normalizados (sin recortar) y, en lugar de los caracteres que la fuente no tiene, un «?»: el PDF nunca falla por un símbolo raro*/
  static String normalizar(String texto, PDFont fuente) {
    if (texto == null) return "";
    StringBuilder salida = new StringBuilder();
    texto.replaceAll("\\s+", " ").codePoints().forEach(cp -> {
      String caracter = new String(Character.toChars(cp));
      try {
        fuente.encode(caracter);
        salida.append(caracter);
      } catch (IOException | IllegalArgumentException e) {
        salida.append('?');
      }
    });
    return salida.toString();
  }

  /**palabras con sus trozos: un trozo que empieza sin espacio (p. ej. el punto tras una dirección web) se pega a la palabra anterior*/
  private List<List<Trozo>> palabras(List<Trozo> trozos) {
    List<List<Trozo>> palabras = new ArrayList<>();
    boolean pegar = false;
    for (Trozo trozo : trozos) {
      String texto = normalizar(trozo.texto(), trozo.estilo().fuente());
      boolean empiezaConEspacio = texto.startsWith(" ");
      String[] partes = texto.strip().split(" ");
      for (int i = 0; i < partes.length; i++) {
        if (partes[i].isEmpty()) continue;
        Trozo parte = new Trozo(partes[i], trozo.estilo());
        if (i == 0 && pegar && !empiezaConEspacio && !palabras.isEmpty()) palabras.get(palabras.size() - 1).add(parte);
        else palabras.add(new ArrayList<>(List.of(parte)));
      }
      pegar = !texto.endsWith(" ");
    }
    return palabras;
  }

  private float anchoPalabra(List<Trozo> palabra) {
    return (float) palabra.stream().mapToDouble(t -> anchoDe(t.texto(), t.estilo())).sum();
  }

  private static float inicio(float x, float ancho, Alineacion alineacion) {
    return switch (alineacion) {
      case IZQUIERDA -> x;
      case CENTRO -> x - ancho / 2;
      case DERECHA -> x - ancho;
    };
  }

  private void escribir(String texto, Estilo estilo, float x, float y, float espaciado) {
    if (texto.isEmpty()) return;
    hacer(() -> {
      flujo.beginText();
      flujo.setFont(estilo.fuente(), estilo.tamano());
      flujo.setNonStrokingColor(estilo.color());
      flujo.setHorizontalScaling(estilo.escala());
      flujo.setCharacterSpacing(espaciado);
      flujo.newLineAtOffset(x, alto - y);
      flujo.showText(texto);
      flujo.endText();
    });
  }

  private static float anchoDe(String texto, Estilo estilo) {
    try {
      return estilo.fuente().getStringWidth(texto) / 1000f * estilo.tamano() * estilo.escala() / 100f;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @FunctionalInterface
  private interface Operacion { void ejecutar() throws IOException; }

  private static void hacer(Operacion operacion) {
    try {
      operacion.ejecutar();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() { hacer(flujo::close); }
}
