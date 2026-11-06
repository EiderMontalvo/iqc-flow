package pe.com.iqc.flow.clientes.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import pe.com.iqc.flow.clientes.domain.Cliente;
import pe.com.iqc.flow.clientes.domain.ContactoCliente;
import pe.com.iqc.flow.clientes.domain.DireccionCliente;
import pe.com.iqc.flow.clientes.domain.EstadoCliente;
import pe.com.iqc.flow.clientes.domain.OrigenCliente;
import pe.com.iqc.flow.clientes.domain.RolContacto;
import pe.com.iqc.flow.clientes.domain.TipoDireccion;
import pe.com.iqc.flow.clientes.domain.TipoDocumento;

/**solicitudes y respuestas de la API de clientes*/
public final class DtosClientes {
  private static final String TELEFONO = "[0-9+()\\-\\s]{6,40}";
  private static final String PAIS = "[A-Za-z]{2}";
  private static final String UBIGEO = "[0-9]{6}";

  private DtosClientes() {
  }

  /**sin responsable se asigna a quien registra; elegir otro requiere el permiso clientes:reasignar*/
  public record SolicitudCrearCliente(
      @NotBlank @Size(max = 300) String razonSocial,
      @Size(max = 300) String nombreComercial,
      @Pattern(regexp = PAIS) String codigoPais,
      TipoDocumento tipoDocumento,
      @Size(max = 30) String numeroDocumento,
      EstadoCliente estado,
      UUID responsableId,
      @Size(max = 120) String sector,
      @Size(max = 300) String sitioWeb,
      OrigenCliente origen,
      @Size(max = 4000) String notas) {
  }

  /**los campos nulos no cambian; un texto vacío ("") borra el valor. La versión es obligatoria*/
  public record SolicitudActualizarCliente(
      @Size(min = 1, max = 300) String razonSocial,
      @Size(max = 300) String nombreComercial,
      @Pattern(regexp = PAIS) String codigoPais,
      TipoDocumento tipoDocumento,
      @Size(max = 30) String numeroDocumento,
      EstadoCliente estado,
      @Size(max = 120) String sector,
      @Size(max = 300) String sitioWeb,
      OrigenCliente origen,
      @Size(max = 4000) String notas,
      @NotNull Long version) {
  }

  public record SolicitudReasignarCliente(@NotNull UUID responsableId, @NotNull Long version) {
  }

  public record SolicitudArchivo(@NotNull Long version, @Size(max = 500) String motivo) {
  }

  public record SolicitudContacto(
      @NotBlank @Size(max = 200) String nombreCompleto,
      @Size(max = 120) String cargo,
      @Email @Size(max = 320) String correo,
      @Pattern(regexp = TELEFONO) String telefono,
      Set<RolContacto> roles,
      // Boolean y no boolean: Jackson 3 rechaza un primitivo ausente en el JSON
      Boolean esPrincipal,
      Boolean activo,
      @Size(max = 1000) String notas,
      /**obligatoria al modificar; se ignora al crear*/
      Long version) {
  }

  public record SolicitudDireccion(
      @NotNull TipoDireccion tipo,
      @Size(max = 120) String nombre,
      @NotBlank @Size(max = 300) String direccion,
      @Size(max = 100) String distrito,
      @Size(max = 100) String provincia,
      @Size(max = 100) String departamento,
      @Pattern(regexp = PAIS) String codigoPais,
      @Pattern(regexp = UBIGEO) String ubigeo,
      @Size(max = 300) String referencia,
      /**obligatoria al modificar; se ignora al crear*/
      Long version) {
  }

  public record ResponsableRespuesta(UUID id, String nombre) {
  }

  public record ClienteResumen(
      UUID id,
      String codigo,
      String razonSocial,
      String nombreComercial,
      String codigoPais,
      TipoDocumento tipoDocumento,
      String numeroDocumento,
      EstadoCliente estado,
      ResponsableRespuesta responsable,
      boolean archivado,
      Instant actualizadoEn) {
    static ClienteResumen de(Cliente cliente, ResponsableRespuesta responsable) {
      return new ClienteResumen(cliente.getId(), cliente.getCodigo(), cliente.getRazonSocial(),
          cliente.getNombreComercial(), cliente.getCodigoPais(), cliente.getTipoDocumento(),
          cliente.getNumeroDocumento(), cliente.getEstado(), responsable, cliente.estaArchivado(),
          cliente.getActualizadoEn());
    }
  }

  public record ClienteDetalle(
      UUID id,
      String codigo,
      String razonSocial,
      String nombreComercial,
      String codigoPais,
      TipoDocumento tipoDocumento,
      String numeroDocumento,
      EstadoCliente estado,
      ResponsableRespuesta responsable,
      String sector,
      String sitioWeb,
      OrigenCliente origen,
      String notas,
      List<ContactoRespuesta> contactos,
      List<DireccionRespuesta> direcciones,
      Instant creadoEn,
      Instant actualizadoEn,
      Instant archivadoEn,
      long version,
      /**si el usuario actual puede editar este cliente (útil para la interfaz)*/
      boolean puedeEditar) {
  }

  public record ContactoRespuesta(
      UUID id,
      String nombreCompleto,
      String cargo,
      String correo,
      String telefono,
      Set<RolContacto> roles,
      boolean esPrincipal,
      boolean activo,
      String notas,
      long version) {
    static ContactoRespuesta de(ContactoCliente contacto) {
      return new ContactoRespuesta(contacto.getId(), contacto.getNombreCompleto(), contacto.getCargo(),
          contacto.getCorreo(), contacto.getTelefono(), contacto.getRoles(), contacto.isEsPrincipal(),
          contacto.isActivo(), contacto.getNotas(), contacto.getVersion());
    }
  }

  public record DireccionRespuesta(
      UUID id,
      TipoDireccion tipo,
      String nombre,
      String direccion,
      String distrito,
      String provincia,
      String departamento,
      String codigoPais,
      String ubigeo,
      String referencia,
      long version) {
    static DireccionRespuesta de(DireccionCliente direccion) {
      return new DireccionRespuesta(direccion.getId(), direccion.getTipo(), direccion.getNombre(),
          direccion.getDireccion(), direccion.getDistrito(), direccion.getProvincia(), direccion.getDepartamento(),
          direccion.getCodigoPais(), direccion.getUbigeo(), direccion.getReferencia(), direccion.getVersion());
    }
  }

  /**resultado de comprobar un documento antes de registrar un cliente*/
  public record VerificacionDocumento(
      boolean valido,
      String numeroNormalizado,
      String mensaje,
      ClienteResumen existente,
      /**avisos que no impiden registrar (p. ej. dígito verificador dudoso de un RUC ecuatoriano)*/
      List<String> advertencias) {
  }
}
