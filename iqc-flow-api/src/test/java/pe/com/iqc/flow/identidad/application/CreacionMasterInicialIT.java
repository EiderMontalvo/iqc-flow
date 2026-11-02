package pe.com.iqc.flow.identidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.com.iqc.flow.PruebaIntegracion;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.Usuario;

class CreacionMasterInicialIT extends PruebaIntegracion {
  @Autowired private Bitacora bitacora;
  @Autowired private Clock reloj;

  @Test
  void creaElPrimerMasterQueDebeCambiarSuContrasena() throws Exception {
    creacion("Master@IQCperu.com", "Frase-Inicial-Segura-2026").run(null);

    Usuario master = usuarios.findByCorreo("master@iqcperu.com").orElseThrow();
    assertThat(master.codigosRoles()).containsExactly(CodigosRol.MASTER);
    assertThat(master.isDebeCambiarContrasena()).isTrue();
    assertThat(jdbc.queryForObject(
        "SELECT origen FROM bitacora WHERE accion = 'USUARIO_CREADO'", String.class)).isEqualTo("CONSOLA");

    iniciarSesion("master@iqcperu.com", "Frase-Inicial-Segura-2026");
  }

  @Test
  void noCreaOtroSiYaExisteUnMaster() {
    crearUsuario("ana@iqcperu.com", false, CodigosRol.MASTER);

    creacion("otro@iqcperu.com", "Frase-Inicial-Segura-2026").run(null);

    assertThat(usuarios.findByCorreo("otro@iqcperu.com")).isEmpty();
  }

  @Test
  void exigeCorreoNombreYContrasena() {
    assertThatThrownBy(() -> creacion("master@iqcperu.com", "").run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("IQC_MASTER_CONTRASENA");
  }

  private CreacionMasterInicial creacion(String correo, String contrasena) {
    PropiedadesMasterInicial propiedades =
        new PropiedadesMasterInicial(true, "IQC_PERU", "IQC Perú", correo, "Usuario Master", contrasena);
    return new CreacionMasterInicial(propiedades, organizaciones, usuarios, roles, codificador, bitacora, reloj);
  }
}
