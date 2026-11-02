package pe.com.iqc.flow;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**falla si un módulo accede a internals de otro o si aparecen dependencias circulares. */
class ModulosTest {
  @Test
  void losModulosRespetanSusLimites() {
    ApplicationModules.of(IqcFlowApplication.class).verify();
  }
}
