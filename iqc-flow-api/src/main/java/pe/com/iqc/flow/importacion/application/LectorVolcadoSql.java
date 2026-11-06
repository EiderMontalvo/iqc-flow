package pe.com.iqc.flow.importacion.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *lee las sentencias INSERT de un volcado de MariaDB/MySQL generado por phpMyAdmin (o mysqldump) y devuelve sus
 *filas por tabla. NULL se devuelve como null. No ejecuta SQL: solo interpreta el texto
 */
public final class LectorVolcadoSql {
  private static final Pattern INSERT = Pattern.compile("INSERT INTO `([^`]+)` \\(([^)]*)\\) VALUES\\s*");

  private LectorVolcadoSql() {
  }

  public static Map<String, List<Map<String, String>>> leer(String sql) {
    Map<String, List<Map<String, String>>> tablas = new HashMap<>();
    Matcher m = INSERT.matcher(sql);
    int pos = 0;
    while (m.find(pos)) {
      String tabla = m.group(1);
      List<String> columnas = Arrays.stream(m.group(2).split(",")).map(c -> c.replace("`", "").trim()).toList();
      List<Map<String, String>> filas = tablas.computeIfAbsent(tabla, t -> new ArrayList<>());
      pos = m.end();
      while (true) {
        if (sql.charAt(pos) != '(') throw new IllegalArgumentException("Se esperaba una fila en la tabla " + tabla);
        pos++;
        List<String> valores = new ArrayList<>();
        StringBuilder valor = new StringBuilder();
        boolean enCadena = false;
        boolean fueCadena = false;
        while (true) {
          char c = sql.charAt(pos++);
          if (enCadena) {
            if (c == '\\') {
              char n = sql.charAt(pos++);
              switch (n) {
                case 'n' -> valor.append('\n');
                case 'r' -> valor.append('\r');
                case 't' -> valor.append('\t');
                case '0' -> { }
                default -> valor.append(n);
              }
            } else if (c == '\'') {
              if (sql.charAt(pos) == '\'') {
                valor.append('\'');
                pos++;
              } else {
                enCadena = false;
              }
            } else {
              valor.append(c);
            }
          } else if (c == '\'') {
            enCadena = true;
            fueCadena = true;
          } else if (c == ',' || c == ')') {
            String texto = valor.toString();
            valores.add(!fueCadena && texto.equals("NULL") ? null : texto);
            valor.setLength(0);
            fueCadena = false;
            if (c == ')') break;
          } else if (!Character.isWhitespace(c)) {
            valor.append(c);
          }
        }
        if (valores.size() != columnas.size()) {
          throw new IllegalArgumentException("La tabla " + tabla + " tiene una fila con " + valores.size()
              + " valores para " + columnas.size() + " columnas");
        }
        Map<String, String> fila = new LinkedHashMap<>();
        for (int i = 0; i < columnas.size(); i++) fila.put(columnas.get(i), valores.get(i));
        filas.add(fila);
        char siguiente = sql.charAt(pos);
        if (siguiente == ',') {
          pos++;
          while (Character.isWhitespace(sql.charAt(pos))) pos++;
        } else if (siguiente == ';') {
          pos++;
          break;
        } else {
          throw new IllegalArgumentException("Separador inesperado '" + siguiente + "' en la tabla " + tabla);
        }
      }
    }
    return tablas;
  }
}
