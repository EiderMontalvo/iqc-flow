package pe.com.iqc.flow.importacion.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.com.iqc.flow.auditorias.ImportacionCertificaciones;
import pe.com.iqc.flow.certificaciones.ImportacionCertificadosPersonales;
import pe.com.iqc.flow.certificaciones.ImportacionCertificadosPersonales.PersonaLegado;
import pe.com.iqc.flow.auditorias.ImportacionCertificaciones.CertificadoLegado;
import pe.com.iqc.flow.auditorias.ImportacionCertificaciones.ExpedienteLegado;
import pe.com.iqc.flow.auditorias.ImportacionCertificaciones.QrLegado;
import pe.com.iqc.flow.auditorias.ImportacionCertificaciones.ResultadoExpediente;
import pe.com.iqc.flow.clientes.ImportacionClientes;
import pe.com.iqc.flow.clientes.ImportacionClientes.ClienteLegado;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.identidad.DirectorioUsuarios;
import pe.com.iqc.flow.identidad.UsuarioResumen;
import pe.com.iqc.flow.importacion.application.PlanImportacion.ClientePlan;
import pe.com.iqc.flow.importacion.application.PlanImportacion.Descarte;
import pe.com.iqc.flow.importacion.application.PlanImportacion.GrupoPlan;
import pe.com.iqc.flow.organizaciones.OrganizacionResumen;
import pe.com.iqc.flow.organizaciones.ServicioOrganizaciones;

/**
 *importación del histórico de la plataforma anterior de certificaciones desde un volcado (ADR-019, ADR-038).
 *En modo informe solo analiza; en modo aplicar escribe todo en una sola transacción (todo o nada)
 */
@Service
public class ServicioImportacionLegado {
  public static final String FUENTE = "PLATAFORMA_CERTIFICACIONES";
  private static final Logger log = LoggerFactory.getLogger(ServicioImportacionLegado.class);

  private final ImportacionClientes clientes;
  private final ImportacionCertificaciones certificaciones;
  private final ImportacionCertificadosPersonales personales;
  private final ServicioOrganizaciones organizaciones;
  private final DirectorioUsuarios directorio;
  private final TransactionTemplate transaccion;
  private final Clock reloj;

  public ServicioImportacionLegado(ImportacionClientes clientes, ImportacionCertificaciones certificaciones,
      ImportacionCertificadosPersonales personales, ServicioOrganizaciones organizaciones, DirectorioUsuarios directorio,
      TransactionTemplate transaccion, Clock reloj) {
    this.clientes = clientes;
    this.certificaciones = certificaciones;
    this.personales = personales;
    this.organizaciones = organizaciones;
    this.directorio = directorio;
    this.transaccion = transaccion;
    this.reloj = reloj;
  }

  public record Resultado(PlanImportacion plan, boolean aplicado, int clientesCreados, int expedientesCreados,
      int certificadosCreados, int qrsCreados, int vigilancias, int tareas, String informe) {
  }

  public Resultado ejecutar(Path volcado, boolean aplicar, String codigoOrganizacion, String correoResponsable) {
    String sql;
    try {
      sql = Files.readString(volcado, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo leer el volcado " + volcado, e);
    }
    Map<String, List<Map<String, String>>> tablas = LectorVolcadoSql.leer(sql);
    PlanImportacion plan = AnalizadorLegado.analizar(tablas, LocalDate.now(reloj));
    PlanPersonales personas = planPersonales(tablas);
    if (!aplicar) return new Resultado(plan, false, 0, 0, 0, 0, 0, 0, informe(plan, null, volcado) + informePersonales(personas, -1));

    OrganizacionResumen organizacion = organizaciones.buscarPorCodigo(codigoOrganizacion)
        .orElseThrow(() -> new ReglaNegocioException("ORGANIZACION_INVALIDA", "No existe la organización " + codigoOrganizacion));
    UsuarioResumen responsable = directorio.buscarActivoPorCorreo(organizacion.id(), correoResponsable)
        .orElseThrow(() -> new ReglaNegocioException("RESPONSABLE_INVALIDO",
            "Indica el correo de un usuario activo como responsable de lo importado (iqc.importacion-legado.responsable)."));
    int[] cuenta = new int[7];
    transaccion.executeWithoutResult(estado -> {
      for (PersonalPlan pp : personas.certificados()) {
        if (personales.importar(pp.persona(), pp.certificado(), organizacion.id())) cuenta[6]++;
      }
      Map<String, UUID> clientePorLegado = new HashMap<>();
      for (ClientePlan c : plan.clientes()) {
        ImportacionClientes.Resultado r = clientes.importar(new ClienteLegado(organizacion.id(), FUENTE, c.idLegado(),
            c.razonSocial(), c.codigoPais(), c.ruc(), c.direccion(), c.notas(), c.estado(), responsable.id()));
        clientePorLegado.put(c.idLegado(), r.clienteId());
        if (r.creado()) cuenta[0]++;
      }
      for (GrupoPlan g : plan.grupos()) {
        ResultadoExpediente r = certificaciones.importar(new ExpedienteLegado(organizacion.id(), FUENTE, g.clave(),
            clientePorLegado.get(g.idClienteLegado()), g.esquema(), g.tipoServicio(), g.alcance(),
            g.fechaCertificacion(), g.fechaExpiracion(), g.limiteVigilancia1(), g.limiteVigilancia2(), g.vigente(),
            responsable.id(), g.certificados().stream().map(c -> new CertificadoLegado(c.idLegado(), c.norma(),
                c.edicion(), c.codigo(), c.estado(), c.revisionAlcance(), c.ciclo(), c.anioDecision(),
                c.fechaOtorgamiento(), c.fechaEmision(), c.fechaExpiracion(), c.url(), c.emitidoPorIndia(), c.alcance(),
                c.qrs().stream().map(q -> new QrLegado(q.idQr(), q.encriptacionId(), q.activo(), q.registradoEn())).toList(),
                c.paisMarca()))
                .toList()));
        if (r.creado()) {
          cuenta[1]++;
          cuenta[2] += r.certificados();
          cuenta[3] += r.qrs();
          cuenta[4] += r.vigilancias();
          cuenta[5] += r.tareas();
        }
      }
    });
    log.info("Importación aplicada: {} clientes, {} expedientes, {} certificados, {} QR y {} certificados personales nuevos",
        cuenta[0], cuenta[1], cuenta[2], cuenta[3], cuenta[6]);
    return new Resultado(plan, true, cuenta[0], cuenta[1], cuenta[2], cuenta[3], cuenta[4], cuenta[5],
        informe(plan, cuenta, volcado) + informePersonales(personas, cuenta[6]));
  }

  // Certificados de personas (tablas «personas» y «certificados_personales» de la plataforma anterior)

  record PersonalPlan(PersonaLegado persona, ImportacionCertificadosPersonales.CertificadoLegado certificado) {
  }

  record PlanPersonales(int filas, List<PersonalPlan> certificados, List<String> descartes) {
  }

  private static final java.util.regex.Pattern TOKEN_PDF =
      java.util.regex.Pattern.compile("personal-pdf/([0-9a-fA-F-]{36})");

  static PlanPersonales planPersonales(Map<String, List<Map<String, String>>> tablas) {
    Map<String, Map<String, String>> personas = new HashMap<>();
    tablas.getOrDefault("personas", List.of()).forEach(f -> personas.put(f.get("id_persona"), f));
    List<Map<String, String>> filas = tablas.getOrDefault("certificados_personales", List.of());
    List<PersonalPlan> lista = new java.util.ArrayList<>();
    List<String> descartes = new java.util.ArrayList<>();
    for (Map<String, String> f : filas) {
      String id = f.get("id_certificado_personal");
      String codigo = f.get("codigo");
      Map<String, String> p = personas.get(f.get("id_persona"));
      if ("1".equals(f.get("estado"))) { descartes.add(codigo + ": eliminado en la plataforma anterior"); continue; }
      if (p == null) { descartes.add(codigo + ": la persona no está en el volcado"); continue; }
      if ("1".equals(p.get("estado"))) { descartes.add(codigo + ": la persona fue eliminada en la plataforma anterior"); continue; }
      String errorDocumento = ImportacionCertificadosPersonales.errorDocumento(p.get("pais"), p.get("tipo_documento"),
          p.get("numero_documento"));
      if (errorDocumento != null) { descartes.add(codigo + ": " + errorDocumento); continue; }
      String url = f.get("url_certificado");
      java.util.regex.Matcher m = url == null ? null : TOKEN_PDF.matcher(url);
      UUID token = m != null && m.find() ? UUID.fromString(m.group(1)) : null;
      lista.add(new PersonalPlan(
          new PersonaLegado(FUENTE, p.get("id_persona"), p.get("pais"), p.get("tipo_documento"), p.get("numero_documento"),
              p.get("nombres_apellidos"), vacio(p.get("email")), vacio(p.get("telefono"))),
          new ImportacionCertificadosPersonales.CertificadoLegado(id, codigo, f.get("curso"), vacio(f.get("norma_referencia")), vacio(f.get("norma_anio")),
              vacio(f.get("subtitulo")), vacio(f.get("descripcion")),
              vacio(f.get("duracion_horas")) == null ? null : new java.math.BigDecimal(f.get("duracion_horas")),
              vacio(f.get("modalidad")), fecha(f.get("fecha_inicio")), fecha(f.get("fecha_fin")), fecha(f.get("fecha_emision")),
              vacio(f.get("entrenador")), vacio(url), token, !"2".equals(f.get("status")),
              "2".equals(f.get("status")) ? null : "Anulado en la plataforma anterior")));
    }
    return new PlanPersonales(filas.size(), lista, descartes);
  }

  private static String vacio(String v) { return v == null || v.isBlank() || "NULL".equals(v) ? null : v.trim(); }

  private static LocalDate fecha(String v) {
    String limpio = vacio(v);
    return limpio == null || limpio.startsWith("0000") ? null : LocalDate.parse(limpio.substring(0, 10));
  }

  static String informePersonales(PlanPersonales p, int aplicados) {
    StringBuilder s = new StringBuilder("\n## Certificados de personas\n\n");
    s.append("Leídos: ").append(p.filas()).append(". A importar: ").append(p.certificados().size())
        .append(" (anulados: ").append(p.certificados().stream().filter(c -> c.certificado().anulado()).count()).append(").");
    if (aplicados >= 0) s.append(" **Aplicado:** ").append(aplicados).append(" nuevos.");
    s.append("\n\n");
    if (!p.descartes().isEmpty()) {
      s.append("### Descartes (").append(p.descartes().size()).append(")\n\n");
      p.descartes().forEach(d -> s.append("- ").append(d).append('\n'));
    }
    return s.toString();
  }

  /**informe en Markdown. Contiene razones sociales: se guarda junto al volcado, fuera del repositorio*/
  static String informe(PlanImportacion p, int[] aplicado, Path volcado) {
    StringBuilder s = new StringBuilder();
    s.append("# Informe de importación de la plataforma anterior\n\n");
    s.append("- **Fecha:** ").append(LocalDateTime.now().withNano(0)).append('\n');
    s.append("- **Volcado:** ").append(volcado.getFileName()).append('\n');
    s.append("- **Modo:** ").append(aplicado == null ? "INFORME (no se escribió nada)" : "APLICAR").append("\n\n");
    s.append("## Resumen\n\n| | Leídos | A importar |\n|---|---|---|\n");
    s.append("| Clientes | ").append(p.filasClientes()).append(" | ").append(p.clientes().size()).append(" |\n");
    s.append("| Certificaciones | ").append(p.filasCertificaciones()).append(" | ").append(p.certificados()).append(" |\n");
    s.append("| QR | ").append(p.filasQr()).append(" | ").append(p.qrs()).append(" |\n");
    s.append("| Expedientes históricos | — | ").append(p.grupos().size()).append(" (vigentes: ")
        .append(p.grupos().stream().filter(GrupoPlan::vigente).count()).append(") |\n\n");
    Map<String, Long> porEstado = p.clientes().stream().collect(Collectors.groupingBy(ClientePlan::estado, Collectors.counting()));
    s.append("Clientes por estado: ").append(porEstado).append(". Clientes sin RUC: ")
        .append(p.clientes().stream().filter(c -> c.ruc() == null).count()).append(".\n\n");
    if (aplicado != null) {
      s.append("**Aplicado:** ").append(aplicado[0]).append(" clientes, ").append(aplicado[1]).append(" expedientes, ")
          .append(aplicado[2]).append(" certificados, ").append(aplicado[3]).append(" QR, ").append(aplicado[4])
          .append(" vigilancias y ").append(aplicado[5]).append(" tareas nuevas (lo ya importado no se repite).\n\n");
    }
    List<ClientePlan> fusionados = p.clientes().stream().filter(c -> !c.idsFusionados().isEmpty()).toList();
    s.append("## Clientes fusionados por RUC (").append(fusionados.size()).append(")\n\n");
    fusionados.forEach(c -> s.append("- ").append(c.razonSocial()).append(" (RUC ").append(c.ruc()).append("): ")
        .append(c.idsFusionados().size() + 1).append(" registros\n"));
    s.append("\n## Posibles duplicados por razón social, con distinto RUC (revisar a mano: ")
        .append(p.posiblesDuplicados().size()).append(")\n\n");
    p.posiblesDuplicados().forEach(g -> s.append("- ").append(g.get(0).razonSocial()).append(": RUC ")
        .append(g.stream().map(c -> String.valueOf(c.ruc())).collect(Collectors.joining(", "))).append('\n'));
    Map<String, List<Descarte>> porMotivo = p.descartes().stream()
        .collect(Collectors.groupingBy(d -> d.tabla() + " — " + d.motivo(), java.util.TreeMap::new, Collectors.toList()));
    s.append("\n## Descartes (").append(p.descartes().size()).append(")\n\n");
    porMotivo.forEach((motivo, lista) -> {
      s.append("### ").append(motivo).append(" (").append(lista.size()).append(")\n\n");
      lista.forEach(d -> s.append("- `").append(d.idLegado()).append("` ").append(d.referencia() == null ? "" : d.referencia())
          .append('\n'));
      s.append('\n');
    });
    s.append("## Advertencias (").append(p.advertencias().size()).append(")\n\n");
    p.advertencias().forEach(a -> s.append("- ").append(a).append('\n'));
    return s.toString();
  }
}
