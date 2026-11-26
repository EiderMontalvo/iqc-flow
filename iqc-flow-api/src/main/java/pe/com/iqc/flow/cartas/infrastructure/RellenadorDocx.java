package pe.com.iqc.flow.cartas.infrastructure;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.springframework.stereotype.Component;

/**
 *llena una plantilla Word (.docx) de plantillas/ con datos, siguiendo las reglas de plantillas/README.md:
 *<ul>
 *<li>{{campo}} se reemplaza por su valor; un campo sin dato queda vacío (las plantillas nunca parten un campo entre runs);</li>
 *<li>un párrafo con campos {{norma.x}} se repite por cada elemento de la lista {@code normas};</li>
 *<li>un párrafo cuyo único texto es {{cuerpo}} se convierte en varios párrafos (línea en blanco = párrafo nuevo;
 *salto simple = salto de línea), con el formato del párrafo de la plantilla;</li>
 *<li>una imagen cuyo texto alternativo es {{firma}} o {{logo}} se cambia por la imagen dada, ajustada a su recuadro
 *sin deformarla.</li>
 *</ul>
 *Trabaja sobre el XML (no sobre la lista de párrafos de POI) para llegar también a tablas, marcos y encabezados
 */
@Component
public class RellenadorDocx {
  private static final Pattern CAMPO = Pattern.compile("\\{\\{([\\w.]+)\\}\\}");
  private static final String W = "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' ";
  private static final String WP = "declare namespace wp='http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing' ";
  private static final String A = "declare namespace a='http://schemas.openxmlformats.org/drawingml/2006/main' ";
  private static final String R_EMBED = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed";

  /**datos ya resueltos: campos de texto, filas de normas, cuerpo libre e imágenes por nombre de campo (firma, logo)*/
  public record Datos(Map<String, String> campos, List<Map<String, String>> normas, String cuerpo, Map<String, byte[]> imagenes) {
    public Datos {
      campos = campos == null ? Map.of() : campos;
      normas = normas == null ? List.of() : normas;
      imagenes = imagenes == null ? Map.of() : imagenes;
    }
  }

  public byte[] llenar(InputStream plantilla, Datos datos) {
    try (XWPFDocument doc = new XWPFDocument(plantilla); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
      XmlObject cuerpo = doc.getDocument().getBody();
      repetirNormas(cuerpo, datos.normas());
      expandirCuerpo(cuerpo, datos.cuerpo());
      reemplazarTextos(cuerpo, datos.campos());
      cambiarImagenes(cuerpo, doc, datos.imagenes());
      for (XWPFHeaderFooter parte : partes(doc)) {
        reemplazarTextos(parte._getHdrFtr(), datos.campos());
        cambiarImagenes(parte._getHdrFtr(), parte, datos.imagenes());
      }
      doc.write(salida);
      return salida.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo llenar la plantilla", e);
    }
  }

  private static List<XWPFHeaderFooter> partes(XWPFDocument doc) {
    List<XWPFHeaderFooter> partes = new ArrayList<>(doc.getHeaderList());
    partes.addAll(doc.getFooterList());
    return partes;
  }

  private static void repetirNormas(XmlObject raiz, List<Map<String, String>> normas) {
    for (XmlObject o : raiz.selectPath(W + ".//w:p")) {
      CTP p = (CTP) o;
      if (!texto(p).contains("{{norma.")) continue;
      for (Map<String, String> n : normas) {
        CTP copia = duplicarAntes(p);
        Map<String, String> valores = new java.util.HashMap<>();
        n.forEach((k, v) -> valores.put("norma." + k, v));
        reemplazarTextos(copia, valores);
      }
      eliminar(p);
    }
  }

  private static void expandirCuerpo(XmlObject raiz, String cuerpo) {
    for (XmlObject o : raiz.selectPath(W + ".//w:p")) {
      CTP p = (CTP) o;
      if (!texto(p).trim().equals("{{cuerpo}}")) continue;
      String limpio = cuerpo == null ? "" : cuerpo.replace("\r", "").trim();
      for (String bloque : limpio.isEmpty() ? List.of("") : List.of(limpio.split("\\n\\s*\\n"))) {
        CTP copia = duplicarAntes(p);
        var formato = copia.sizeOfRArray() > 0 && copia.getRArray(0).isSetRPr() ? copia.getRArray(0).getRPr().copy() : null;
        while (copia.sizeOfRArray() > 0) copia.removeR(0);
        String[] lineas = bloque.strip().split("\\n");
        for (int i = 0; i < lineas.length; i++) {
          CTR r = copia.addNewR();
          if (formato != null) r.setRPr((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr) formato);
          CTText t = r.addNewT();
          t.setStringValue(lineas[i].strip());
          t.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
          if (i < lineas.length - 1) r.addNewBr();
        }
      }
      eliminar(p);
    }
  }

  private static void reemplazarTextos(XmlObject raiz, Map<String, String> valores) {
    for (XmlObject o : raiz.selectPath(W + ".//w:t")) {
      CTText t = (CTText) o;
      String v = t.getStringValue();
      if (v == null || !v.contains("{{")) continue;
      Matcher m = CAMPO.matcher(v);
      StringBuilder sb = new StringBuilder();
      while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(valores.getOrDefault(m.group(1), "")));
      m.appendTail(sb);
      t.setStringValue(sb.toString());
    }
  }

  /**cambia el contenido de cada imagen marcada y ajusta su tamaño al recuadro de la plantilla conservando la proporción*/
  private static void cambiarImagenes(XmlObject raiz, POIXMLDocumentPart parte, Map<String, byte[]> imagenes) {
    if (imagenes.isEmpty()) return;
    for (XmlObject contenedor : raiz.selectPath(WP + ".//wp:inline | .//wp:anchor")) {
      XmlObject[] docPr = contenedor.selectPath(WP + "./wp:docPr");
      if (docPr.length == 0) continue;
      String alt = atributo(docPr[0], "descr").replace("{", "").replace("}", "").trim();
      byte[] nueva = imagenes.get(alt);
      if (nueva == null) continue;
      XmlObject[] blips = contenedor.selectPath(A + ".//a:blip");
      if (blips.length == 0) continue;
      String rid = atributoCalificado(blips[0], R_EMBED);
      POIXMLDocumentPart imagen = parte.getRelationById(rid);
      if (imagen == null) continue;
      try (OutputStream out = imagen.getPackagePart().getOutputStream()) {
        out.write(nueva);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      ajustar(contenedor, nueva);
    }
  }

  private static void ajustar(XmlObject contenedor, byte[] imagen) {
    BufferedImage b;
    try { b = ImageIO.read(new ByteArrayInputStream(imagen)); } catch (IOException e) { return; }
    if (b == null || b.getWidth() == 0 || b.getHeight() == 0) return;
    XmlObject[] extents = contenedor.selectPath(WP + "./wp:extent");
    if (extents.length == 0) return;
    CTPositiveSize2D ext = (CTPositiveSize2D) extents[0];
    long anchoMax = ext.getCx(), altoMax = ext.getCy();
    double escala = Math.min((double) anchoMax / b.getWidth(), (double) altoMax / b.getHeight());
    long ancho = Math.round(b.getWidth() * escala), alto = Math.round(b.getHeight() * escala);
    ext.setCx(ancho);
    ext.setCy(alto);
    for (XmlObject o : contenedor.selectPath(A + ".//a:xfrm/a:ext")) {
      org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D e = (org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D) o;
      e.setCx(ancho);
      e.setCy(alto);
    }
  }

  private static String texto(CTP p) {
    StringBuilder sb = new StringBuilder();
    for (XmlObject t : p.selectPath(W + ".//w:t")) sb.append(((CTText) t).getStringValue());
    return sb.toString();
  }

  /**copia el párrafo justo antes de sí mismo y devuelve la copia (XmlObject.copy() daría un fragmento suelto)*/
  private static CTP duplicarAntes(CTP p) {
    try (XmlCursor origen = p.newCursor(); XmlCursor destino = p.newCursor()) {
      origen.copyXml(destino);
    }
    try (XmlCursor c = p.newCursor()) {
      c.toPrevSibling();
      return (CTP) c.getObject();
    }
  }

  private static void eliminar(XmlObject o) {
    try (XmlCursor c = o.newCursor()) { c.removeXml(); }
  }

  private static String atributo(XmlObject o, String nombre) {
    try (XmlCursor c = o.newCursor()) {
      String v = c.getAttributeText(new javax.xml.namespace.QName(nombre));
      return v == null ? "" : v;
    }
  }

  private static String atributoCalificado(XmlObject o, String clark) {
    try (XmlCursor c = o.newCursor()) {
      return c.getAttributeText(javax.xml.namespace.QName.valueOf(clark));
    }
  }
}
