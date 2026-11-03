package pe.com.iqc.flow.clientes.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.clientes.application.DtosClientes.ContactoRespuesta;
import pe.com.iqc.flow.clientes.application.DtosClientes.DireccionRespuesta;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudContacto;
import pe.com.iqc.flow.clientes.application.DtosClientes.SolicitudDireccion;
import pe.com.iqc.flow.clientes.domain.Cliente;
import pe.com.iqc.flow.clientes.domain.ContactoCliente;
import pe.com.iqc.flow.clientes.domain.DireccionCliente;
import pe.com.iqc.flow.clientes.domain.TipoDireccion;
import pe.com.iqc.flow.clientes.infrastructure.ContactoClienteRepository;
import pe.com.iqc.flow.clientes.infrastructure.DireccionClienteRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.RecursoNoEncontradoException;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**
 *contactos y direcciones de un cliente. Quien puede editar el cliente puede editar sus contactos y
 *direcciones. Los cambios se registran en la bitácora del cliente, con correo y teléfono enmascarados.
 */
@Service
public class ServicioDatosContacto {
  private final ContactoClienteRepository contactos;
  private final DireccionClienteRepository direcciones;
  private final AccesoClientes acceso;
  private final Bitacora bitacora;

  public ServicioDatosContacto(ContactoClienteRepository contactos, DireccionClienteRepository direcciones,
      AccesoClientes acceso, Bitacora bitacora) {
    this.contactos = contactos;
    this.direcciones = direcciones;
    this.acceso = acceso;
    this.bitacora = bitacora;
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:escribir')")
  public ContactoRespuesta agregarContacto(UUID clienteId, SolicitudContacto solicitud) {
    Cliente cliente = acceso.cargarEditable(clienteId);
    ContactoCliente contacto = new ContactoCliente(cliente.getId());
    aplicar(contacto, solicitud);
    contactos.save(contacto);
    marcarPrincipal(contacto, Boolean.TRUE.equals(solicitud.esPrincipal()));
    contactos.flush();
    registrar("CONTACTO_AGREGADO", cliente, null, instantanea(contacto));
    return ContactoRespuesta.de(contacto);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:escribir')")
  public ContactoRespuesta actualizarContacto(UUID clienteId, UUID contactoId, SolicitudContacto solicitud) {
    Cliente cliente = acceso.cargarEditable(clienteId);
    ContactoCliente contacto = contactos.findByIdAndClienteId(contactoId, cliente.getId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Contacto", contactoId));
    ServicioClientes.verificarVersion(contacto.getVersion(), solicitud.version());
    Map<String, Object> antes = instantanea(contacto);
    aplicar(contacto, solicitud);
    marcarPrincipal(contacto, Boolean.TRUE.equals(solicitud.esPrincipal()));
    contactos.flush();
    registrar("CONTACTO_ACTUALIZADO", cliente, antes, instantanea(contacto));
    return ContactoRespuesta.de(contacto);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:escribir')")
  public void eliminarContacto(UUID clienteId, UUID contactoId) {
    Cliente cliente = acceso.cargarEditable(clienteId);
    ContactoCliente contacto = contactos.findByIdAndClienteId(contactoId, cliente.getId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Contacto", contactoId));
    Map<String, Object> antes = instantanea(contacto);
    contactos.delete(contacto);
    registrar("CONTACTO_ELIMINADO", cliente, antes, null);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:escribir')")
  public DireccionRespuesta agregarDireccion(UUID clienteId, SolicitudDireccion solicitud) {
    Cliente cliente = acceso.cargarEditable(clienteId);
    if (solicitud.tipo() == TipoDireccion.FISCAL && direcciones.existsByClienteIdAndTipo(cliente.getId(), TipoDireccion.FISCAL)) {
      throw fiscalDuplicada();
    }
    DireccionCliente direccion = new DireccionCliente(cliente.getId());
    aplicar(direccion, solicitud, cliente.getCodigoPais());
    direcciones.saveAndFlush(direccion);
    registrar("DIRECCION_AGREGADA", cliente, null, instantanea(direccion));
    return DireccionRespuesta.de(direccion);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:escribir')")
  public DireccionRespuesta actualizarDireccion(UUID clienteId, UUID direccionId, SolicitudDireccion solicitud) {
    Cliente cliente = acceso.cargarEditable(clienteId);
    DireccionCliente direccion = direcciones.findByIdAndClienteId(direccionId, cliente.getId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Dirección", direccionId));
    ServicioClientes.verificarVersion(direccion.getVersion(), solicitud.version());
    if (solicitud.tipo() == TipoDireccion.FISCAL
        && direcciones.existsByClienteIdAndTipoAndIdNot(cliente.getId(), TipoDireccion.FISCAL, direccion.getId())) {
      throw fiscalDuplicada();
    }
    Map<String, Object> antes = instantanea(direccion);
    aplicar(direccion, solicitud, direccion.getCodigoPais());
    direcciones.flush();
    registrar("DIRECCION_ACTUALIZADA", cliente, antes, instantanea(direccion));
    return DireccionRespuesta.de(direccion);
  }

  @Transactional
  @PreAuthorize("hasAuthority('clientes:escribir')")
  public void eliminarDireccion(UUID clienteId, UUID direccionId) {
    Cliente cliente = acceso.cargarEditable(clienteId);
    DireccionCliente direccion = direcciones.findByIdAndClienteId(direccionId, cliente.getId())
        .orElseThrow(() -> new RecursoNoEncontradoException("Dirección", direccionId));
    Map<String, Object> antes = instantanea(direccion);
    direcciones.delete(direccion);
    registrar("DIRECCION_ELIMINADA", cliente, antes, null);
  }

  private static void aplicar(ContactoCliente contacto, SolicitudContacto solicitud) {
    contacto.actualizar(solicitud.nombreCompleto(), ServicioClientes.limpio(solicitud.cargo()),
        ServicioClientes.limpio(solicitud.correo()), ServicioClientes.limpio(solicitud.telefono()), solicitud.roles(),
        solicitud.activo() == null || solicitud.activo(), ServicioClientes.limpio(solicitud.notas()));
  }

  private static void aplicar(DireccionCliente direccion, SolicitudDireccion solicitud, String paisPorDefecto) {
    direccion.actualizar(solicitud.tipo(), ServicioClientes.limpio(solicitud.nombre()), solicitud.direccion(),
        ServicioClientes.limpio(solicitud.distrito()), ServicioClientes.limpio(solicitud.provincia()),
        ServicioClientes.limpio(solicitud.departamento()),
        solicitud.codigoPais() != null ? solicitud.codigoPais() : paisPorDefecto,
        ServicioClientes.limpio(solicitud.ubigeo()), ServicioClientes.limpio(solicitud.referencia()));
  }

  /**solo un contacto principal por cliente: al marcar uno se desmarcan los demás*/
  private void marcarPrincipal(ContactoCliente contacto, boolean principal) {
    if (principal) contactos.quitarPrincipal(contacto.getClienteId(), contacto.getId());
    contacto.marcarPrincipal(principal);
  }

  private void registrar(String accion, Cliente cliente, Map<String, Object> antes, Map<String, Object> despues) {
    bitacora.registrar(EntradaBitacora.accion(accion)
        .entidad(ServicioClientes.ENTIDAD, cliente.getId())
        .estadoAnterior(antes)
        .estadoNuevo(despues));
  }

  private static ReglaNegocioException fiscalDuplicada() {
    return new ReglaNegocioException("DIRECCION_FISCAL_DUPLICADA",
        "El cliente ya tiene una dirección fiscal. Modifícala en lugar de agregar otra.");
  }

  private static Map<String, Object> instantanea(ContactoCliente contacto) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("contactoId", contacto.getId());
    datos.put("nombreCompleto", contacto.getNombreCompleto());
    datos.put("cargo", contacto.getCargo());
    datos.put("correo", enmascararCorreo(contacto.getCorreo()));
    datos.put("telefono", enmascararTelefono(contacto.getTelefono()));
    datos.put("roles", contacto.getRoles().stream().map(Enum::name).sorted().toList());
    datos.put("esPrincipal", contacto.isEsPrincipal());
    datos.put("activo", contacto.isActivo());
    return datos;
  }

  private static Map<String, Object> instantanea(DireccionCliente direccion) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("direccionId", direccion.getId());
    datos.put("tipo", direccion.getTipo().name());
    datos.put("nombre", direccion.getNombre());
    datos.put("direccion", direccion.getDireccion());
    datos.put("distrito", direccion.getDistrito());
    datos.put("provincia", direccion.getProvincia());
    datos.put("departamento", direccion.getDepartamento());
    datos.put("codigoPais", direccion.getCodigoPais());
    datos.put("ubigeo", direccion.getUbigeo());
    return datos;
  }

  /**"ana.perez@empresa.com" → "a***@empresa.com"*/
  static String enmascararCorreo(String correo) {
    if (correo == null) return null;
    int arroba = correo.indexOf('@');
    return arroba <= 0 ? "***" : correo.charAt(0) + "***" + correo.substring(arroba);
  }

  /**"+51 987 654 321" → "***321"*/
  static String enmascararTelefono(String telefono) {
    if (telefono == null) return null;
    String digitos = telefono.replaceAll("\\D", "");
    return digitos.length() <= 3 ? "***" : "***" + digitos.substring(digitos.length() - 3);
  }
}
