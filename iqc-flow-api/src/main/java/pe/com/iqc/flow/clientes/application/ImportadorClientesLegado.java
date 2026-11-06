package pe.com.iqc.flow.clientes.application;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.clientes.ImportacionClientes;
import pe.com.iqc.flow.clientes.domain.Cliente;
import pe.com.iqc.flow.clientes.domain.DireccionCliente;
import pe.com.iqc.flow.clientes.domain.EstadoCliente;
import pe.com.iqc.flow.clientes.domain.OrigenCliente;
import pe.com.iqc.flow.clientes.domain.TipoDireccion;
import pe.com.iqc.flow.clientes.domain.TipoDocumento;
import pe.com.iqc.flow.clientes.domain.ValidadorDocumento;
import pe.com.iqc.flow.clientes.infrastructure.ClienteRepository;
import pe.com.iqc.flow.clientes.infrastructure.DireccionClienteRepository;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.bitacora.OrigenOperacion;
import pe.com.iqc.flow.compartido.bitacora.TipoActor;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;

/**importación de clientes de un sistema anterior con las reglas del dominio (RUC, unicidad, estado)*/
@Service
class ImportadorClientesLegado implements ImportacionClientes {
  private final ClienteRepository clientes;
  private final DireccionClienteRepository direcciones;
  private final Bitacora bitacora;

  ImportadorClientesLegado(ClienteRepository clientes, DireccionClienteRepository direcciones, Bitacora bitacora) {
    this.clientes = clientes;
    this.direcciones = direcciones;
    this.bitacora = bitacora;
  }

  @Override
  @Transactional
  public Resultado importar(ClienteLegado c) {
    Optional<Cliente> existente = clientes.findByOrganizacionIdAndFuenteLegadoAndIdLegado(c.organizacionId(),
        c.fuente(), c.idLegado());
    if (existente.isPresent()) {
      return new Resultado(existente.get().getId(), false, existente.get().tieneDocumento(), "ya importado");
    }
    String pais = ValidadorDocumento.normalizarPais(c.codigoPais());
    String documento = null;
    String observacion = null;
    if (c.numeroDocumento() != null && !c.numeroDocumento().isBlank()) {
      try {
        documento = ValidadorDocumento.normalizar(pais, TipoDocumento.RUC, c.numeroDocumento());
      } catch (ReglaNegocioException e) {
        observacion = "RUC inválido en el sistema anterior (" + c.numeroDocumento().trim() + "): " + e.getMessage();
      }
    } else {
      observacion = "Sin RUC en el sistema anterior.";
    }
    if (documento != null) {
      Optional<Cliente> mismoDocumento = clientes.buscarVigentePorDocumento(c.organizacionId(), pais, TipoDocumento.RUC,
          documento);
      if (mismoDocumento.isPresent()) {
        Cliente cliente = mismoDocumento.get();
        if (cliente.getFuenteLegado() == null) cliente.marcarOrigenLegado(c.fuente(), c.idLegado());
        clientes.flush();
        return new Resultado(cliente.getId(), false, true, "vinculado a un cliente existente con el mismo RUC");
      }
    }
    Cliente cliente = Cliente.nuevo(c.organizacionId(), c.razonSocial(), pais, c.responsableId(), null);
    String notas = unir(c.notas(), observacion);
    cliente.actualizarDatos(c.razonSocial(), null, null, null, OrigenCliente.CLIENTE_ANTIGUO, notas);
    if (documento != null) cliente.asignarDocumento(pais, TipoDocumento.RUC, documento);
    EstadoCliente estado = EstadoCliente.valueOf(c.estado());
    if (estado == EstadoCliente.ACTIVO && documento == null) estado = EstadoCliente.PROSPECTO;
    cliente.cambiarEstado(estado);
    cliente.marcarOrigenLegado(c.fuente(), c.idLegado());
    clientes.saveAndFlush(cliente);
    if (c.direccion() != null && !c.direccion().isBlank()) {
      DireccionCliente direccion = new DireccionCliente(cliente.getId());
      direccion.actualizar(TipoDireccion.FISCAL, null, recortar(c.direccion().trim(), 300), null, null, null, pais, null, null);
      direcciones.save(direccion);
    }
    bitacora.registrar(EntradaBitacora.accion("CLIENTE_IMPORTADO").entidad("CLIENTE", cliente.getId())
        .organizacion(c.organizacionId()).actor(null, TipoActor.SISTEMA).origen(OrigenOperacion.MIGRACION)
        .estadoNuevo(Map.of("fuente", c.fuente(), "idLegado", c.idLegado(), "estado", estado.name(),
            "conDocumento", documento != null)));
    return new Resultado(cliente.getId(), true, documento != null, observacion);
  }

  private static String unir(String a, String b) {
    if (a == null || a.isBlank()) return b;
    if (b == null || b.isBlank()) return a;
    return recortar(a + "\n" + b, 4000);
  }

  private static String recortar(String texto, int maximo) {
    return texto.length() <= maximo ? texto : texto.substring(0, maximo);
  }
}
