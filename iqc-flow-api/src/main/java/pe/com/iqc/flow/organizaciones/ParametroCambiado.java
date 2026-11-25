package pe.com.iqc.flow.organizaciones;

import java.util.UUID;

/**evento publicado en la misma transacción cuando MASTER cambia un parámetro; otros módulos reaccionan a él*/
public record ParametroCambiado(UUID organizacionId, ParametroOrganizacion parametro, String valorAnterior, String valor) {
}
