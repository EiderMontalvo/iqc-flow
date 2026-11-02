package pe.com.iqc.flow.identidad.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.com.iqc.flow.compartido.bitacora.Bitacora;
import pe.com.iqc.flow.compartido.bitacora.EntradaBitacora;
import pe.com.iqc.flow.compartido.bitacora.OrigenOperacion;
import pe.com.iqc.flow.compartido.bitacora.TipoActor;
import pe.com.iqc.flow.identidad.domain.CodigosRol;
import pe.com.iqc.flow.identidad.domain.PoliticaContrasena;
import pe.com.iqc.flow.identidad.domain.Rol;
import pe.com.iqc.flow.identidad.domain.Usuario;
import pe.com.iqc.flow.identidad.infrastructure.RolRepository;
import pe.com.iqc.flow.identidad.infrastructure.UsuarioRepository;
import pe.com.iqc.flow.organizaciones.OrganizacionResumen;
import pe.com.iqc.flow.organizaciones.ServicioOrganizaciones;

/**
 *arranque inicial: con IQC_MASTER_INICIAL=true crea la organización y el primer usuario MASTER.
 *Si ya existe un usuario MASTER no hace nada. El usuario creado debe cambiar su contraseña al ingresar.
 */
@Component
@ConditionalOnProperty(prefix = "iqc.master-inicial", name = "habilitado", havingValue = "true")
class CreacionMasterInicial implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(CreacionMasterInicial.class);

  private final PropiedadesMasterInicial propiedades;
  private final ServicioOrganizaciones organizaciones;
  private final UsuarioRepository usuarios;
  private final RolRepository roles;
  private final PasswordEncoder codificador;
  private final Bitacora bitacora;
  private final Clock reloj;

  CreacionMasterInicial(PropiedadesMasterInicial propiedades, ServicioOrganizaciones organizaciones,
      UsuarioRepository usuarios, RolRepository roles, PasswordEncoder codificador, Bitacora bitacora, Clock reloj) {
    this.propiedades = propiedades;
    this.organizaciones = organizaciones;
    this.usuarios = usuarios;
    this.roles = roles;
    this.codificador = codificador;
    this.bitacora = bitacora;
    this.reloj = reloj;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments argumentos) {
    if (usuarios.existeConRol(CodigosRol.MASTER)) {
      log.warn("Ya existe un usuario MASTER; no se crea otro. Retira IQC_MASTER_INICIAL y las variables IQC_MASTER_*.");
      return;
    }
    if (vacio(propiedades.correo()) || vacio(propiedades.nombre()) || vacio(propiedades.contrasena())) {
      throw new IllegalStateException(
          "IQC_MASTER_INICIAL=true requiere IQC_MASTER_CORREO, IQC_MASTER_NOMBRE e IQC_MASTER_CONTRASENA");
    }
    String correo = Usuario.normalizarCorreo(propiedades.correo());
    if (usuarios.existsByCorreo(correo)) {
      throw new IllegalStateException("Ya existe un usuario con el correo " + correo + " sin el rol MASTER");
    }
    PoliticaContrasena.validar(propiedades.contrasena(), correo);

    OrganizacionResumen organizacion = organizaciones.asegurarExistencia(
        propiedades.organizacionCodigo(), propiedades.organizacionNombre());
    Rol master = roles.buscarDisponibles(Set.of(CodigosRol.MASTER), organizacion.id()).stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("Falta el rol MASTER; revisa las migraciones"));
    Usuario usuario = usuarios.save(Usuario.nuevo(organizacion.id(), correo, propiedades.nombre(),
        codificador.encode(propiedades.contrasena()), Set.of(master), Instant.now(reloj)));

    bitacora.registrar(EntradaBitacora.accion("USUARIO_CREADO")
        .entidad("USUARIO", usuario.getId())
        .organizacion(organizacion.id())
        .actor(null, TipoActor.SISTEMA)
        .origen(OrigenOperacion.CONSOLA)
        .estadoNuevo(Map.of("correo", correo, "nombre", usuario.getNombre(), "roles", Set.of(CodigosRol.MASTER)))
        .motivo("Creación del primer usuario MASTER"));
    log.info("Usuario MASTER {} creado en {}. Debe cambiar la contraseña en el primer acceso.",
        correo, organizacion.codigo());
  }

  private static boolean vacio(String valor) { return valor == null || valor.isBlank(); }
}
