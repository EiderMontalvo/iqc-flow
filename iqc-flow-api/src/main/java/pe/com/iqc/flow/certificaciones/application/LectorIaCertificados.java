package pe.com.iqc.flow.certificaciones.application;

import java.util.Optional;
import pe.com.iqc.flow.certificaciones.domain.AnalizadorTextoCertificado.Resultado;

/**
 *puerto para leer con IA los PDF que la lectura local no reconoce (escaneados o de formato nuevo). El proveedor está
 *por decidir: sin implementación registrada, la IA no se usa aunque el parámetro IA_HABILITADA esté en SI
 */
public interface LectorIaCertificados {
  enum Tipo { EMPRESARIAL, PERSONAL }

  Optional<Resultado> leer(byte[] pdf, Tipo tipo);
}
