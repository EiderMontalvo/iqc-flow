package pe.com.iqc.flow.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.importacion.application.ServicioImportacionLegado;
import pe.com.iqc.flow.importacion.application.ServicioImportacionLegado.Resultado;

/**importación del histórico desde un volcado ficticio con la estructura de la plataforma anterior*/
class ImportacionLegadoIT extends PruebaIntegracion {
  private static final String RESPONSABLE = "administracion@iqcperu.com";

  @Autowired ServicioImportacionLegado importacion;

  private Path volcado;

  @BeforeEach
  void preparar() throws Exception {
    crearUsuario(RESPONSABLE, false, CodigosRol.MASTER);
    volcado = Path.of(getClass().getResource("/legado/volcado-prueba.sql").toURI());
  }

  @Test
  void elModoInformeNoEscribeNada() {
    Resultado r = importacion.ejecutar(volcado, false, "IQC_PERU", null);

    assertThat(r.aplicado()).isFalse();
    assertThat(r.informe()).contains("Clientes fusionados por RUC (1)").contains("Carga duplicada");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM clientes", Long.class)).isZero();
  }

  @Test
  void aplicaElPlanYUnaSegundaEjecucionNoDuplica() throws Exception {
    Resultado r = importacion.ejecutar(volcado, true, "IQC_PERU", RESPONSABLE);

    assertThat(r.clientesCreados()).isEqualTo(4);
    assertThat(r.expedientesCreados()).isEqualTo(4);
    assertThat(r.certificadosCreados()).isEqualTo(7);
    assertThat(r.qrsCreados()).isEqualTo(4);
    assertThat(r.vigilancias()).isEqualTo(6);
    assertThat(r.tareas()).isEqualTo(3);

    // Sin RUC no puede quedar ACTIVO: queda como prospecto con la observación
    assertThat(jdbc.queryForObject("SELECT estado FROM clientes WHERE id_legado = 'cli-dos'", String.class)).isEqualTo("PROSPECTO");
    assertThat(jdbc.queryForObject("SELECT estado || ' ' || numero_documento FROM clientes WHERE id_legado = 'cli-uno'",
        String.class)).isEqualTo("ACTIVO 20600000013");
    assertThat(jdbc.queryForObject("SELECT codigo_pais FROM clientes WHERE id_legado = 'cli-tres'", String.class)).isEqualTo("EC");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expedientes WHERE estado = 'ENTREGADO' AND fuente_legado IS NOT NULL",
        Long.class)).isEqualTo(4);
    // Los QR de la carga duplicada y del registro eliminado llevan al certificado conservado
    assertThat(jdbc.queryForList("""
        SELECT q.encriptacion_id FROM qr_legado q JOIN certificados c ON c.id = q.certificado_id
        WHERE c.codigo = 'PE-INA-00120250111' ORDER BY 1""", String.class)).containsExactly("qr-1a", "qr-2b", "qr-3c");
    // Certificado integrado de India: un código, dos normas
    assertThat(jdbc.queryForObject("SELECT count(*) FROM certificados WHERE codigo = 'PER/IMS-QEO/JAS-C0001/RC1/0001'",
        Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT estado FROM certificados WHERE id_legado = 'k11'", String.class)).isEqualTo("SUSPENDIDO");
    assertThat(jdbc.queryForObject("SELECT edicion FROM certificados WHERE id_legado = 'k4'", String.class)).isEqualTo("2025");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'CLIENTE_IMPORTADO' AND origen = 'MIGRACION'",
        Long.class)).isEqualTo(4);

    Resultado otra = importacion.ejecutar(volcado, true, "IQC_PERU", RESPONSABLE);
    assertThat(otra.clientesCreados() + otra.expedientesCreados() + otra.certificadosCreados() + otra.qrsCreados()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM certificados", Long.class)).isEqualTo(7);

    // Las vigilancias ya vencidas quedan visibles para revisión, sin tareas automáticas
    mvc.perform(get("/api/v1/expedientes/vigilancias").param("vista", "VENCIDAS").session(iniciarSesion(RESPONSABLE)))
        .andExpect(jsonPath("$[*].cliente.razonSocial", hasItem("Ecuatoriana Tres S.A.")));
  }

  @Test
  void sinUnResponsableValidoNoSeAplicaNada() {
    assertThatThrownBy(() -> importacion.ejecutar(volcado, true, "IQC_PERU", "nadie@iqcperu.com"))
        .hasFieldOrPropertyWithValue("codigo", "RESPONSABLE_INVALIDO");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM clientes", Long.class)).isZero();
  }
}
