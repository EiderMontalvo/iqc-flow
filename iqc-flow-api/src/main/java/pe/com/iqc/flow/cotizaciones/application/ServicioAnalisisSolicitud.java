package pe.com.iqc.flow.cotizaciones.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.catalogos.ServicioCatalogos;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.EdicionCatalogo;
import pe.com.iqc.flow.catalogos.ServicioCatalogos.NormaCatalogo;
import pe.com.iqc.flow.clientes.ClienteReferencia;
import pe.com.iqc.flow.clientes.ConsultaClientes;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.domain.ReglaNegocioException;
import pe.com.iqc.flow.compartido.seguridad.ActorActual;
import pe.com.iqc.flow.compartido.seguridad.UsuarioAutenticado;
import pe.com.iqc.flow.cotizaciones.AnalistaIaSolicitudes;
import pe.com.iqc.flow.cotizaciones.AnalistaIaSolicitudes.PropuestaIa;
import pe.com.iqc.flow.cotizaciones.domain.TipoServicioCotizacion;
import pe.com.iqc.flow.organizaciones.ParametroOrganizacion;
import pe.com.iqc.flow.organizaciones.ParametrosOrganizacion;
import pe.com.iqc.flow.padron.ConsultaPadron;
import pe.com.iqc.flow.padron.ConsultaPadron.DatosRuc;

/**
 *analiza la solicitud de un cliente y propone los datos de la cotización: primero reglas locales (siempre), luego la
 *IA si está encendida para la persona y hay proveedor. El RUC se completa con SUNAT y se busca entre los clientes.
 *No guarda nada: devuelve una propuesta que la persona revisa en el formulario
 */
@Service
public class ServicioAnalisisSolicitud {
  private static final Pattern NUMERO = Pattern.compile("(\\d{4,5})(?:\\s*[:\\-]\\s*(\\d{4}))?");

  public record EmpresaPropuesta(String ruc, String razonSocial, String direccion, String distrito, String provincia,
      String departamento, UUID clienteId, String clienteRazonSocial) {
  }

  public record ContactoPropuesto(String nombre, String cargo, String correo, String telefono) {
  }

  public record NormaPropuesta(String codigo, String nombre, String edicion) {
  }

  /**origen: LOCAL (solo reglas) o IA; faltantes: lo que conviene pedir al cliente antes de cotizar*/
  public record AnalisisSolicitud(String origen, String resumen, List<String> puntosClave, List<String> faltantes,
      List<String> advertencias, EmpresaPropuesta empresa, ContactoPropuesto contacto, TipoServicioCotizacion tipoServicio,
      List<NormaPropuesta> normas, String alcance, Integer numeroSedes, String sedesDetalle, Integer numeroTrabajadores, String plazo,
      String texto) {
  }

  private final ObjectProvider<AnalistaIaSolicitudes> ia;
  private final ParametrosOrganizacion parametros;
  private final ConsultaPadron padron;
  private final ConsultaClientes clientes;
  private final ServicioCatalogos catalogos;
  private final ActorActual actorActual;
  private final Bitacora bitacora;

  public ServicioAnalisisSolicitud(ObjectProvider<AnalistaIaSolicitudes> ia, ParametrosOrganizacion parametros, ConsultaPadron padron,
      ConsultaClientes clientes, ServicioCatalogos catalogos, ActorActual actorActual, Bitacora bitacora) {
    this.ia = ia;
    this.parametros = parametros;
    this.padron = padron;
    this.clientes = clientes;
    this.catalogos = catalogos;
    this.actorActual = actorActual;
    this.bitacora = bitacora;
  }

  @Transactional
  @PreAuthorize("hasAuthority('cotizaciones:gestionar')")
  public AnalisisSolicitud analizar(byte[] archivo, String textoPegado) {
    UsuarioAutenticado actor = actorActual.requerido();
    String texto = String.join("\n\n", List.of(LectorSolicitud.texto(archivo), textoPegado == null ? "" : textoPegado.trim())).trim();
    List<String> avisos = new ArrayList<>();

    AnalistaIaSolicitudes analista = ia.getIfAvailable();
    boolean iaEncendida = parametros.activoPara(actor.organizacionId(), actor.id(), ParametroOrganizacion.IA_HABILITADA);
    Optional<PropuestaIa> p = Optional.empty();
    if (!iaEncendida) avisos.add("La IA está apagada: el análisis se hizo solo con reglas. Revisa y completa los datos.");
    else if (analista == null || !analista.disponible()) avisos.add("La IA está encendida, pero aún no hay un proveedor configurado: el análisis se hizo solo con reglas.");
    else {
      byte[] pdf = texto.isBlank() && archivo != null && LectorSolicitud.esPdf(archivo) ? archivo : null;
      if (!texto.isBlank() || pdf != null) {
        p = analista.analizar(texto, pdf);
        if (p.isEmpty()) avisos.add("La IA no pudo analizar la solicitud esta vez: se usaron solo las reglas.");
      }
    }
    if (texto.isBlank() && p.isEmpty()) {
      throw new ReglaNegocioException("SOLICITUD_SIN_TEXTO", "La solicitud no tiene texto legible (parece escaneada). Pega el texto del correo o activa la IA.");
    }

    AnalisisLocalSolicitud.Resultado local = AnalisisLocalSolicitud.analizar(texto);
    PropuestaIa ai = p.orElse(null);

    // Empresa: RUC (reglas o IA) → SUNAT → ¿ya es cliente?
    String ruc = primero(local.ruc(), soloDigitos(ai == null ? null : ai.ruc()));
    DatosRuc sunat = null;
    if (ruc != null && ruc.matches("(10|15|16|17|20)\\d{9}")) {
      try { sunat = padron.ruc(ruc).orElse(null); }
      catch (ReglaNegocioException e) { avisos.add("No se pudo consultar el RUC en SUNAT: " + e.getMessage()); }
    }
    ClienteReferencia cliente = ruc == null ? null : clientes.idsPorTexto(actor.organizacionId(), ruc).stream()
        .map(id -> clientes.buscar(actor.organizacionId(), id).orElse(null)).filter(Objects::nonNull)
        .filter(c -> ruc.equals(c.numeroDocumento())).findFirst().orElse(null);
    EmpresaPropuesta empresa = new EmpresaPropuesta(ruc,
        primero(sunat == null ? null : sunat.razonSocial(), ai == null ? null : limpio(ai.razonSocial())),
        sunat == null ? null : sunat.direccion(), sunat == null ? null : sunat.distrito(),
        sunat == null ? null : sunat.provincia(), sunat == null ? null : sunat.departamento(),
        cliente == null ? null : cliente.id(), cliente == null ? null : cliente.razonSocial());

    ContactoPropuesto contacto = new ContactoPropuesto(primero(ai == null ? null : limpio(ai.contactoNombre()), local.contactoNombre()), ai == null ? null : limpio(ai.contactoCargo()),
        primero(local.correo(), ai == null ? null : limpio(ai.contactoCorreo())), primero(local.telefono(), ai == null ? null : limpio(ai.contactoTelefono())));

    TipoServicioCotizacion tipo = local.tipoServicio();
    if (ai != null && ai.tipoServicio() != null) {
      try { tipo = TipoServicioCotizacion.valueOf(ai.tipoServicio().trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { /* se queda la regla */ }
    }

    // Normas: las de las reglas y las de la IA, traducidas al catálogo (con su edición si existe)
    Map<String, String> pedidas = new LinkedHashMap<>();
    local.normas().forEach(n -> pedidas.putIfAbsent(n.numero(), n.anio()));
    if (ai != null && ai.normas() != null) for (String n : ai.normas()) {
      Matcher m = NUMERO.matcher(n == null ? "" : n);
      if (m.find()) pedidas.putIfAbsent(m.group(1), m.group(2));
    }
    List<NormaPropuesta> normas = new ArrayList<>();
    List<NormaCatalogo> catalogo = catalogos.normas();
    pedidas.forEach((numero, anio) -> catalogo.stream().filter(c -> c.nombreCorto().replaceAll("\\D", "").equals(numero)).findFirst()
        .ifPresentOrElse(c -> normas.add(new NormaPropuesta(c.codigo(), c.nombreCorto(), edicion(c, anio))),
            () -> avisos.add("La norma ISO " + numero + " no está en el catálogo: agrégala como «Otro servicio» si corresponde.")));

    Integer sedes = local.sedes() != null ? local.sedes() : entero(ai == null ? null : ai.numeroSedes());
    Integer trabajadores = local.trabajadores() != null ? local.trabajadores() : entero(ai == null ? null : ai.numeroTrabajadores());
    String alcance = ai == null ? null : limpio(ai.alcance());

    List<String> puntos = ai != null && ai.puntosClave() != null && !ai.puntosClave().isEmpty() ? limpios(ai.puntosClave()) : puntosLocales(empresa, normas, trabajadores, sedes, tipo);
    List<String> faltantes = ai != null && ai.informacionFaltante() != null ? limpios(ai.informacionFaltante()) : new ArrayList<>();
    if (ai == null) {
      if (ruc == null) faltantes.add("RUC de la empresa");
      if (normas.isEmpty()) faltantes.add("Normas a certificar");
      if (trabajadores == null) faltantes.add("Número de trabajadores (define los días auditor)");
      if (sedes == null) faltantes.add("Número de sedes y sus direcciones");
      faltantes.add("Alcance de la certificación (actividades que cubre)");
      if (contacto.correo() == null) faltantes.add("Correo de la persona de contacto");
      if (contacto.nombre() == null) faltantes.add("Nombre de la persona de contacto");
    }

    bitacora.registrar(EntradaBitacora.accion("SOLICITUD_ANALIZADA").entidad("COTIZACION", "analisis")
        .estadoNuevo(Map.of("origen", ai == null ? "LOCAL" : "IA", "caracteres", texto.length())));
    return new AnalisisSolicitud(ai == null ? "LOCAL" : "IA", ai == null ? null : limpio(ai.resumen()), puntos, faltantes, avisos, empresa,
        contacto, tipo, normas, alcance, sedes, ai == null ? null : limpio(ai.sedesDetalle()), trabajadores, ai == null ? null : limpio(ai.plazo()), texto);
  }

  private static List<String> puntosLocales(EmpresaPropuesta e, List<NormaPropuesta> normas, Integer trabajadores, Integer sedes, TipoServicioCotizacion tipo) {
    List<String> p = new ArrayList<>();
    if (e.razonSocial() != null) p.add("Empresa: " + e.razonSocial() + (e.ruc() != null ? " (RUC " + e.ruc() + ")" : ""));
    if (e.clienteId() != null) p.add("Ya está registrada como cliente.");
    else if (e.ruc() != null) p.add("Aún no es cliente: regístrala antes de cotizar.");
    p.add("Servicio: " + tipo.name().toLowerCase(Locale.ROOT).replace('_', ' '));
    if (!normas.isEmpty()) p.add("Normas: " + String.join(", ", normas.stream().map(NormaPropuesta::nombre).toList()));
    if (trabajadores != null) p.add(trabajadores + " trabajadores");
    if (sedes != null) p.add(sedes + (sedes == 1 ? " sede" : " sedes"));
    return p;
  }

  private static String edicion(NormaCatalogo c, String anio) {
    if (anio != null && c.ediciones().stream().anyMatch(e -> e.edicion().equals(anio))) return anio;
    return c.ediciones().stream().filter(EdicionCatalogo::vigente).map(EdicionCatalogo::edicion).findFirst()
        .orElse(c.ediciones().isEmpty() ? null : c.ediciones().get(0).edicion());
  }

  private static String primero(String... valores) { return Arrays.stream(valores).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null); }

  private static String soloDigitos(String s) { return s == null ? null : s.replaceAll("\\D", ""); }

  private static String limpio(String s) { return s == null || s.isBlank() || s.trim().equals("-") ? null : s.trim(); }

  private static List<String> limpios(List<String> l) { return new ArrayList<>(l.stream().map(ServicioAnalisisSolicitud::limpio).filter(Objects::nonNull).toList()); }

  private static Integer entero(String s) {
    if (s == null) return null;
    Matcher m = Pattern.compile("\\d{1,6}").matcher(s);
    return m.find() ? Integer.valueOf(m.group()) : null;
  }
}
