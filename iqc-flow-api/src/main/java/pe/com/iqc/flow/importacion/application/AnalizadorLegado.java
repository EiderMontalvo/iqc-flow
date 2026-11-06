package pe.com.iqc.flow.importacion.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import pe.com.iqc.flow.importacion.application.PlanImportacion.CertificadoPlan;
import pe.com.iqc.flow.importacion.application.PlanImportacion.ClientePlan;
import pe.com.iqc.flow.importacion.application.PlanImportacion.Descarte;
import pe.com.iqc.flow.importacion.application.PlanImportacion.GrupoPlan;
import pe.com.iqc.flow.importacion.application.PlanImportacion.QrPlan;

/**
 *convierte las tablas de la plataforma anterior (Certificaciones, Clientes, generarQR) en un plan de importación:
 *descarta eliminados y pruebas, normaliza normas y esquemas, fusiona clientes con el mismo RUC, resuelve códigos
 *duplicados (redirigiendo sus QR), y agrupa los certificados en expedientes históricos. No escribe nada
 */
public final class AnalizadorLegado {
  static final String VIGENTE = "2";
  private static final ZoneId LIMA = ZoneId.of("America/Lima");
  private static final Pattern NORMA = Pattern.compile("(\\d{4,5})\\s*(?:[:\\-]\\s*(\\d{4}))?");
  private static final Pattern CODIGO_PROPIO = Pattern.compile("^PE-?(?:(?:INA|SAE)-)?(\\d{3})(\\d{4})(\\d{2})(\\d)(\\d)([A-Z]?)$");
  private static final Pattern CICLO_INDIA = Pattern.compile("/RC(\\d+)(?:/|$)");
  private static final Pattern REVISION_INDIA = Pattern.compile("/R(\\d+)(?:/|$)");
  private static final Map<String, String> NORMAS = Map.of("9001", "ISO_9001", "14001", "ISO_14001",
      "45001", "ISO_45001", "37001", "ISO_37001", "27001", "ISO_IEC_27001");
  private static final Map<String, Set<String>> EDICIONES = Map.of("ISO_9001", Set.of("2015"),
      "ISO_14001", Set.of("2015"), "ISO_45001", Set.of("2018"), "ISO_37001", Set.of("2016", "2025"),
      "ISO_IEC_27001", Set.of("2013", "2022"));
  private static final Map<String, String> EDICION_POR_DEFECTO = Map.of("ISO_9001", "2015", "ISO_14001", "2015",
      "ISO_45001", "2018", "ISO_37001", "2016", "ISO_IEC_27001", "2022");
  /**fin de la transición internacional de ISO/IEC 27001:2013 a 2022*/
  private static final LocalDate TRANSICION_27001 = LocalDate.of(2025, 10, 31);

  private AnalizadorLegado() {
  }

  public static PlanImportacion analizar(Map<String, List<Map<String, String>>> tablas, LocalDate hoy) {
    List<Descarte> descartes = new ArrayList<>();
    List<String> advertencias = new ArrayList<>();
    List<Map<String, String>> filasCert = tablas.getOrDefault("Certificaciones", List.of());
    List<Map<String, String>> filasCli = tablas.getOrDefault("Clientes", List.of());
    List<Map<String, String>> filasQr = tablas.getOrDefault("generarQR", List.of());
    Map<String, Map<String, String>> clientesPorId = filasCli.stream()
        .collect(Collectors.toMap(f -> f.get("idCliente"), f -> f, (a, b) -> a, LinkedHashMap::new));

    // 1. Certificados importables
    List<Cert> certs = new ArrayList<>();
    for (Map<String, String> f : filasCert) {
      String id = f.get("idCertificacion");
      String codigo = limpio(f.get("codigo"));
      if (!VIGENTE.equals(f.get("estado"))) {
        descartes.add(new Descarte("Certificaciones", id, codigo, "Eliminado en la plataforma anterior"));
        continue;
      }
      if (!clientesPorId.containsKey(f.get("idCliente"))) {
        descartes.add(new Descarte("Certificaciones", id, codigo, "Su cliente no existe en el volcado"));
        continue;
      }
      String estado = switch (Objects.toString(f.get("status"), "")) {
        case "2" -> "EMITIDO";
        case "4" -> "SUSPENDIDO";
        case "1" -> "RETIRADO";
        default -> null;
      };
      if (estado == null) {
        descartes.add(new Descarte("Certificaciones", id, codigo, "Estado 'En proceso' (status " + f.get("status") + ")"));
        continue;
      }
      if (codigo == null || !codigo.matches(".*\\d{3,}.*")) {
        descartes.add(new Descarte("Certificaciones", id, codigo, "Código sin formato de certificado (prueba)"));
        continue;
      }
      String esquema = esquema(codigo, f.get("acreditadoras"), f.get("pais"));
      if (esquema == null) {
        descartes.add(new Descarte("Certificaciones", id, codigo, "No se pudo determinar el esquema de acreditación"));
        continue;
      }
      LocalDate emision = fecha(f.get("fechaCertificacion"));
      LocalDate expiracion = fecha(f.get("fechaExpiracion"));
      if (emision == null || expiracion == null) {
        descartes.add(new Descarte("Certificaciones", id, codigo, "Sin fecha de certificación o de expiración"));
        continue;
      }
      Optional<String[]> norma = norma(f.get("norma"), codigo, esquema, emision, advertencias, id);
      if (norma.isEmpty()) {
        descartes.add(new Descarte("Certificaciones", id, codigo, "Norma desconocida: '" + f.get("norma") + "' (prueba)"));
        continue;
      }
      if (expiracion.isBefore(emision)) {
        advertencias.add("Certificación " + id + " (" + codigo + "): la expiración " + expiracion
            + " es anterior a la certificación " + emision + ".");
      } else {
        long meses = java.time.temporal.ChronoUnit.MONTHS.between(emision, expiracion);
        if (meses < 30 || meses > 38) {
          advertencias.add("Certificación " + id + " (" + codigo + "): plazo irregular de " + meses
              + " meses entre la certificación (" + emision + ") y la expiración (" + expiracion + "); el ciclo normal es de 36.");
        }
      }
      int revision = 1;
      int ciclo = 1;
      Integer anio = null;
      boolean india = "IQC_GLOBAL".equals(esquema);
      if (india) {
        Matcher rc = CICLO_INDIA.matcher(codigo);
        if (rc.find()) ciclo = Math.min(Integer.parseInt(rc.group(1)) + 1, 9);
        Matcher r = REVISION_INDIA.matcher(codigo);
        if (r.find()) revision = Math.min(Integer.parseInt(r.group(1)) + 1, 9);
      } else {
        Matcher m = CODIGO_PROPIO.matcher(codigo);
        if (m.matches()) {
          anio = Integer.parseInt(m.group(2));
          revision = Math.max(1, Integer.parseInt(m.group(4)));
          ciclo = Math.max(1, Integer.parseInt(m.group(5)));
        } else {
          advertencias.add("Certificación " + id + " (" + codigo + "): el código no sigue el formato DDC-PRO-003;"
              + " se importa con revisión 1 y ciclo 1.");
        }
      }
      LocalDate otorgamiento = fecha(f.get("fechaOtorgamiento"));
      certs.add(new Cert(id, f.get("idCliente"), codigo, norma.get()[0], norma.get()[1], estado, esquema, revision,
          ciclo, anio, otorgamiento != null ? otorgamiento : emision, emision, expiracion,
          limpio(f.get("urlCertificado")), india, limpio(f.get("alcance")), fecha(f.get("fechaSeguimientoUno")),
          fecha(f.get("fechaSeguimientoDos")), "Ecuador".equalsIgnoreCase(limpio(f.get("pais"))),
          Objects.toString(f.get("fechaModificacion"), "")));
    }

    // 2. Códigos repetidos: se conserva la carga más reciente y sus QR quedan redirigidos a ella
    Map<String, String> redireccion = new HashMap<>();
    Map<String, List<Cert>> porCodigo = certs.stream()
        .collect(Collectors.groupingBy(c -> c.codigo + "|" + c.norma, LinkedHashMap::new, Collectors.toList()));
    List<Cert> unicos = new ArrayList<>();
    for (List<Cert> grupo : porCodigo.values()) {
      grupo.sort(Comparator.comparing((Cert c) -> c.modificado).reversed().thenComparing(c -> c.id));
      Cert conservado = grupo.get(0);
      unicos.add(conservado);
      for (Cert repetido : grupo.subList(1, grupo.size())) {
        redireccion.put(repetido.id, conservado.id);
        descartes.add(new Descarte("Certificaciones", repetido.id, repetido.codigo,
            repetido.cliente.equals(conservado.cliente) ? "Carga duplicada (se conservó la más reciente)"
                : "Código repetido en otro cliente (se conservó la carga más reciente): revisar"));
      }
    }
    Map<String, String> codigoNormaImportado = unicos.stream()
        .collect(Collectors.toMap(c -> c.codigo + "|" + c.norma, c -> c.id));
    Map<String, List<String>> idsPorCodigo = unicos.stream()
        .collect(Collectors.groupingBy(c -> c.codigo, Collectors.mapping(c -> c.id, Collectors.toList())));
    // Las certificaciones eliminadas con el mismo código que una importada: sus QR siguen siendo válidos
    for (Map<String, String> f : filasCert) {
      String id = f.get("idCertificacion");
      if (redireccion.containsKey(id) || codigoNormaImportado.containsValue(id)) continue;
      List<String> mismos = idsPorCodigo.getOrDefault(limpio(f.get("codigo")), List.of());
      if (mismos.size() == 1) redireccion.put(id, mismos.get(0));
    }

    // 3. Clientes: los que tienen certificados importables (aunque estén marcados como eliminados) y los vigentes
    Map<String, List<Cert>> certsPorCliente = unicos.stream().collect(Collectors.groupingBy(c -> c.cliente));
    List<Map<String, String>> candidatos = new ArrayList<>();
    for (Map<String, String> f : filasCli) {
      String id = f.get("idCliente");
      if (VIGENTE.equals(f.get("estado")) || certsPorCliente.containsKey(id)) {
        candidatos.add(f);
        if (!VIGENTE.equals(f.get("estado"))) {
          advertencias.add("Cliente " + id + " estaba eliminado pero tiene certificados importables: se importa.");
        }
      } else {
        descartes.add(new Descarte("Clientes", id, f.get("razonSocial"), "Eliminado en la plataforma anterior"));
      }
    }
    // Fusión por RUC: el registro principal es el de más certificados (y, a igualdad, el más antiguo)
    Map<String, List<Map<String, String>>> porRuc = new LinkedHashMap<>();
    List<List<Map<String, String>>> conjuntos = new ArrayList<>();
    for (Map<String, String> f : candidatos) {
      String ruc = soloDigitos(f.get("ruc"));
      if (ruc.length() >= 8) porRuc.computeIfAbsent(ruc, r -> new ArrayList<>()).add(f);
      else conjuntos.add(new ArrayList<>(List.of(f)));
    }
    conjuntos.addAll(porRuc.values());
    Map<String, String> principalDe = new HashMap<>();
    List<ClientePlan> clientes = new ArrayList<>();
    for (List<Map<String, String>> conjunto : conjuntos) {
      conjunto.sort(Comparator.comparing((Map<String, String> f) -> -certsPorCliente.getOrDefault(f.get("idCliente"), List.of()).size())
          .thenComparing(f -> Objects.toString(f.get("fechaCreacion"), ""))
          .thenComparing(f -> f.get("idCliente")));
      Map<String, String> principal = conjunto.get(0);
      List<String> fusionados = conjunto.stream().skip(1).map(f -> f.get("idCliente")).toList();
      conjunto.forEach(f -> principalDe.put(f.get("idCliente"), principal.get("idCliente")));
      List<Cert> suyos = conjunto.stream().flatMap(f -> certsPorCliente.getOrDefault(f.get("idCliente"), List.of()).stream()).toList();
      String ruc = soloDigitos(principal.get("ruc"));
      boolean ecuador = ruc.length() == 13 || suyos.stream().anyMatch(c -> c.ecuador);
      String direccion = conjunto.stream().map(f -> limpio(f.get("direccion"))).filter(Objects::nonNull).findFirst().orElse(null);
      String representante = conjunto.stream().map(f -> limpio(f.get("representante_legal"))).filter(Objects::nonNull)
          .findFirst().orElse(null);
      boolean vigente = suyos.stream().anyMatch(c -> c.vigente(hoy));
      String estado = vigente ? "ACTIVO" : suyos.isEmpty() ? "PROSPECTO" : "INACTIVO";
      List<String> notas = new ArrayList<>();
      if (representante != null) notas.add("Representante legal (plataforma anterior): " + representante);
      if (!fusionados.isEmpty()) {
        notas.add("Fusionado con " + fusionados.size() + " registro(s) duplicado(s) de la plataforma anterior: "
            + String.join(", ", fusionados));
      }
      clientes.add(new ClientePlan(principal.get("idCliente"), fusionados, limpio(principal.get("razonSocial")),
          ecuador ? "EC" : "PE", ruc.isEmpty() ? null : ruc, direccion, String.join("\n", notas), estado));
    }
    clientes.sort(Comparator.comparing(ClientePlan::razonSocial, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
    // Posibles duplicados por razón social con distinto RUC: solo se informan
    Map<String, List<ClientePlan>> porNombre = clientes.stream().collect(Collectors.groupingBy(
        c -> Objects.toString(c.razonSocial(), "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")));
    List<List<ClientePlan>> posiblesDuplicados = porNombre.entrySet().stream()
        .filter(e -> !e.getKey().isEmpty() && e.getValue().size() > 1).map(Map.Entry::getValue).toList();

    // 4. QR: se asocian al certificado importado (o al que conservó su código)
    Map<String, List<QrPlan>> qrPorCert = new HashMap<>();
    Set<String> importados = unicos.stream().map(c -> c.id).collect(Collectors.toSet());
    for (Map<String, String> f : filasQr) {
      String destino = f.get("idCertificacion");
      if (destino != null && !importados.contains(destino)) destino = redireccion.get(destino);
      if (destino == null || !importados.contains(destino)) {
        descartes.add(new Descarte("generarQR", f.get("idqr"), f.get("encriptacionID"),
            "Apunta a una certificación no importada y sin equivalente"));
        continue;
      }
      qrPorCert.computeIfAbsent(destino, d -> new ArrayList<>()).add(new QrPlan(f.get("idqr"), f.get("encriptacionID"),
          VIGENTE.equals(f.get("estado")), fechaHora(f.get("fecha_register"))));
    }

    // 5. Expedientes históricos: certificados del mismo cliente, esquema, fecha y ciclo
    Map<String, List<Cert>> porGrupo = new TreeMap<>();
    for (Cert c : unicos) {
      String cliente = principalDe.get(c.cliente);
      porGrupo.computeIfAbsent(cliente + "|" + c.esquema + "|" + c.emision + "|" + c.ciclo, k -> new ArrayList<>()).add(c);
    }
    List<GrupoPlan> grupos = new ArrayList<>();
    for (Map.Entry<String, List<Cert>> e : porGrupo.entrySet()) {
      List<Cert> lista = e.getValue();
      lista.sort(Comparator.comparing((Cert c) -> c.norma).thenComparing(c -> c.codigo));
      Cert primero = lista.get(0);
      boolean vigente = lista.stream().anyMatch(c -> c.vigente(hoy));
      LocalDate expiracion = lista.stream().map(c -> c.expiracion).max(LocalDate::compareTo).orElseThrow();
      LocalDate vig1 = vigente ? limite(lista.stream().map(c -> c.vigilancia1).toList(), expiracion) : null;
      LocalDate vig2 = vigente ? limite(lista.stream().map(c -> c.vigilancia2).toList(), expiracion) : null;
      grupos.add(new GrupoPlan(e.getKey(), principalDe.get(primero.cliente), primero.esquema,
          primero.ciclo > 1 ? "RECERTIFICACION" : "CERTIFICACION_INICIAL",
          lista.stream().map(c -> c.alcance).filter(Objects::nonNull).findFirst().orElse(null), primero.emision,
          expiracion, vig1, vig2, vigente,
          lista.stream().map(c -> new CertificadoPlan(c.id, c.norma, c.edicion, c.codigo, c.estado, c.revision, c.ciclo,
              c.anio, c.otorgamiento, c.emision, c.expiracion, c.url, c.india, c.alcance,
              qrPorCert.getOrDefault(c.id, List.of()))).toList()));
    }
    return new PlanImportacion(clientes, grupos, descartes, advertencias, posiblesDuplicados, filasCert.size(),
        filasCli.size(), filasQr.size());
  }

  static String esquema(String codigo, String acreditadoras, String pais) {
    if (codigo.startsWith("PE-INA-") || codigo.matches("^PE\\d{11}[A-Z]?$")) return "INACAL";
    if (codigo.startsWith("PE-SAE-")) return "SAE";
    if (codigo.startsWith("PER/") || codigo.contains("JAS")) return "IQC_GLOBAL";
    String a = Objects.toString(acreditadoras, "").toUpperCase(Locale.ROOT);
    if (a.contains("INACAL")) return "INACAL";
    if (a.contains("ECUATORIANO") || a.contains("SAE")) return "SAE";
    if (a.contains("IQCGLOBAL") || a.contains("JAS") || a.contains("IAS")) return "IQC_GLOBAL";
    return null;
  }

  /**[código de norma, edición]; corrige variantes conocidas y avisa*/
  static Optional<String[]> norma(String texto, String codigo, String esquema, LocalDate emision, List<String> avisos,
      String id) {
    if (texto == null) return Optional.empty();
    Matcher m = NORMA.matcher(texto);
    if (!m.find()) return Optional.empty();
    String norma = NORMAS.get(m.group(1));
    if (norma == null) return Optional.empty();
    String anio = m.group(2);
    String edicion;
    if ("ISO_37001".equals(norma) && !"IQC_GLOBAL".equals(esquema) && codigo.matches(".*\\d[A-Z]$")) {
      edicion = "2025";
    } else if (anio != null && EDICIONES.get(norma).contains(anio)) {
      edicion = anio;
    } else if ("ISO_IEC_27001".equals(norma) && anio == null) {
      edicion = emision.isAfter(TRANSICION_27001) ? "2022" : "2013";
      avisos.add("Certificación " + id + " (" + codigo + "): norma '" + texto.trim() + "' sin año; se asumió la edición " + edicion + ".");
    } else {
      edicion = EDICION_POR_DEFECTO.get(norma);
      avisos.add("Certificación " + id + " (" + codigo + "): edición '" + texto.trim() + "' inexistente; se asumió " + edicion + ".");
    }
    if (anio != null && !anio.equals(edicion) && EDICIONES.get(norma).contains(anio)) {
      avisos.add("Certificación " + id + " (" + codigo + "): figuraba como " + texto.trim()
          + ", pero la letra del código indica la edición " + edicion + ".");
    }
    return Optional.of(new String[] {norma, edicion});
  }

  /**fecha límite de una vigilancia: la menor de las registradas, si no supera la expiración*/
  private static LocalDate limite(List<LocalDate> fechas, LocalDate expiracion) {
    return fechas.stream().filter(Objects::nonNull).filter(f -> !f.isAfter(expiracion)).min(LocalDate::compareTo).orElse(null);
  }

  static LocalDate fecha(String valor) {
    if (valor == null || valor.isBlank() || valor.startsWith("0000")) return null;
    try {
      return LocalDate.parse(valor.trim().substring(0, Math.min(10, valor.trim().length())));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static java.time.Instant fechaHora(String valor) {
    if (valor == null || valor.isBlank() || valor.startsWith("0000")) return null;
    try {
      return LocalDateTime.parse(valor.trim().replace(' ', 'T')).atZone(LIMA).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String limpio(String valor) {
    if (valor == null) return null;
    String t = valor.replace(' ', ' ').trim();
    return t.isEmpty() || t.equals("-") ? null : t;
  }

  private static String soloDigitos(String valor) { return valor == null ? "" : valor.replaceAll("\\D", ""); }

  private record Cert(String id, String cliente, String codigo, String norma, String edicion, String estado,
      String esquema, int revision, int ciclo, Integer anio, LocalDate otorgamiento, LocalDate emision,
      LocalDate expiracion, String url, boolean india, String alcance, LocalDate vigilancia1, LocalDate vigilancia2,
      boolean ecuador, String modificado) {
    boolean vigente(LocalDate hoy) { return "EMITIDO".equals(estado) && !expiracion.isBefore(hoy); }
  }
}
