package pe.com.iqc.flow.clientes.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**guarda los roles de un contacto como texto separado por comas ("CALIDAD,GERENCIA"). */
@Converter
public class ConvertidorRolesContacto implements AttributeConverter<Set<RolContacto>, String> {
  @Override
  public String convertToDatabaseColumn(Set<RolContacto> roles) {
    return roles == null ? "" : roles.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
  }

  @Override
  public Set<RolContacto> convertToEntityAttribute(String valor) {
    if (valor == null || valor.isBlank()) return EnumSet.noneOf(RolContacto.class);
    return Arrays.stream(valor.split(",")).map(RolContacto::valueOf)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(RolContacto.class)));
  }
}
