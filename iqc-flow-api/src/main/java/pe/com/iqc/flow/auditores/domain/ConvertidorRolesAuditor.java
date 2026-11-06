package pe.com.iqc.flow.auditores.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import pe.com.iqc.flow.auditores.RolAuditor;

/**guarda los roles de un auditor como texto separado por comas ("AUDITOR,LIDER")*/
@Converter
public class ConvertidorRolesAuditor implements AttributeConverter<Set<RolAuditor>, String> {
  @Override
  public String convertToDatabaseColumn(Set<RolAuditor> roles) {
    return roles == null ? "" : roles.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
  }

  @Override
  public Set<RolAuditor> convertToEntityAttribute(String valor) {
    if (valor == null || valor.isBlank()) return EnumSet.noneOf(RolAuditor.class);
    return Arrays.stream(valor.split(",")).map(RolAuditor::valueOf)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(RolAuditor.class)));
  }
}
