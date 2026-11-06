package pe.com.iqc.flow.importacion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.importacion.application.PlanImportacion.CertificadoPlan;
import pe.com.iqc.flow.importacion.application.PlanImportacion.ClientePlan;
import pe.com.iqc.flow.importacion.application.PlanImportacion.Descarte;
import pe.com.iqc.flow.importacion.application.PlanImportacion.GrupoPlan;

class AnalizadorLegadoTest {
  private static PlanImportacion plan;

  @BeforeAll
  static void analizar() throws IOException {
    String sql;
    try (var entrada = AnalizadorLegadoTest.class.getResourceAsStream("/legado/volcado-prueba.sql")) {
      sql = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
    }
    plan = AnalizadorLegado.analizar(LectorVolcadoSql.leer(sql), LocalDate.of(2026, 11, 6));
  }

  @Test
  void elLectorRespetaNulosComillasYCadenasVacias() {
    Map<String, List<Map<String, String>>> tablas = LectorVolcadoSql.leer(
        "INSERT INTO `T` (`a`, `b`, `c`, `d`) VALUES\n(1, 'O''Brien \\'x\\'', NULL, ''),\n(2, 'NULL', 'a,b)', '');");
    assertThat(tablas.get("T")).hasSize(2);
    assertThat(tablas.get("T").get(0)).containsEntry("b", "O'Brien 'x'").containsEntry("c", null).containsEntry("d", "");
    assertThat(tablas.get("T").get(1)).containsEntry("b", "NULL").containsEntry("c", "a,b)");
  }

  @Test
  void fusionaClientesPorRucYDescartaLosEliminadosSinCertificados() {
    assertThat(plan.clientes()).extracting(ClientePlan::idLegado)
        .containsExactlyInAnyOrder("cli-uno", "cli-dos", "cli-tres", "cli-prospecto");
    ClientePlan uno = cliente("cli-uno");
    assertThat(uno.idsFusionados()).containsExactly("cli-uno-bis");
    assertThat(uno.estado()).isEqualTo("ACTIVO");
    assertThat(uno.notas()).contains("Representante legal").contains("cli-uno-bis");
    assertThat(cliente("cli-tres").codigoPais()).isEqualTo("EC");
    assertThat(cliente("cli-dos").ruc()).isNull();
    assertThat(cliente("cli-prospecto").estado()).isEqualTo("PROSPECTO");
    assertThat(plan.descartes()).extracting(Descarte::idLegado).contains("cli-borrado");
  }

  @Test
  void descartaPruebasEliminadosYCargasDuplicadas() {
    assertThat(plan.descartes()).filteredOn(d -> d.tabla().equals("Certificaciones"))
        .extracting(Descarte::idLegado).containsExactlyInAnyOrder("k3", "k8", "k9", "k10");
    assertThat(plan.certificados()).isEqualTo(7);
  }

  @Test
  void agrupaEnExpedientesYLeeRevisionCicloYEdicionDelCodigo() {
    assertThat(plan.grupos()).hasSize(4);
    GrupoPlan sae = plan.grupos().stream().filter(g -> g.esquema().equals("SAE")).findFirst().orElseThrow();
    assertThat(sae.certificados()).extracting(CertificadoPlan::estado).containsExactlyInAnyOrder("EMITIDO", "SUSPENDIDO");
    CertificadoPlan antisoborno = sae.certificados().stream().filter(c -> c.norma().equals("ISO_37001")).findFirst().orElseThrow();
    // La letra A del código manda: 37001:2025 aunque el campo norma dijera 2016
    assertThat(antisoborno.edicion()).isEqualTo("2025");
    assertThat(sae.limiteVigilancia1()).isEqualTo(LocalDate.of(2027, 3, 29));

    GrupoPlan india = plan.grupos().stream().filter(g -> g.esquema().equals("IQC_GLOBAL")).findFirst().orElseThrow();
    assertThat(india.certificados()).hasSize(2).allMatch(c -> c.codigo().equals("PER/IMS-QEO/JAS-C0001/RC1/0001"));
    assertThat(india.tipoServicio()).isEqualTo("RECERTIFICACION");
    assertThat(india.certificados().get(0).ciclo()).isEqualTo(2);

    GrupoPlan anulado = plan.grupos().stream().filter(g -> !g.vigente()).findFirst().orElseThrow();
    assertThat(anulado.certificados().get(0).estado()).isEqualTo("RETIRADO");
    assertThat(anulado.limiteVigilancia1()).isNull();

    CertificadoPlan iso9001 = plan.grupos().stream().flatMap(g -> g.certificados().stream())
        .filter(c -> c.codigo().equals("PE-INA-00120250211")).findFirst().orElseThrow();
    assertThat(iso9001.edicion()).isEqualTo("2015");
    assertThat(iso9001.anioDecision()).isEqualTo(2025);
    // Sin fecha de otorgamiento (0000-00-00) se usa la de certificación
    assertThat(iso9001.fechaOtorgamiento()).isEqualTo(LocalDate.of(2025, 1, 10));
  }

  @Test
  void losQrDeCargasDuplicadasOEliminadasSiguenLlevandoAlCertificadoConservado() {
    CertificadoPlan k1 = plan.grupos().stream().flatMap(g -> g.certificados().stream())
        .filter(c -> c.idLegado().equals("k1")).findFirst().orElseThrow();
    assertThat(k1.qrs()).extracting(PlanImportacion.QrPlan::encriptacionId).containsExactlyInAnyOrder("qr-1a", "qr-2b", "qr-3c");
    assertThat(plan.qrs()).isEqualTo(4);
    assertThat(plan.descartes()).extracting(Descarte::idLegado).contains("qr-5");
  }

  private static ClientePlan cliente(String id) {
    return plan.clientes().stream().filter(c -> c.idLegado().equals(id)).findFirst().orElseThrow();
  }
}
