package pe.com.iqc.flow.identidad.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;
import java.time.Duration;
import java.util.List;

/**
 *autenticación por sesión con cookie y protección CSRF para el SPA. Todo lo que no se declara
 *explícitamente queda denegado. Los permisos de negocio se validan con @PreAuthorize en los servicios.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {
  public static final String RUTA_INICIO_SESION = "/api/v1/autenticacion/inicio-sesion";
  public static final String RUTA_CIERRE_SESION = "/api/v1/autenticacion/cierre-sesion";
  /**pública: «olvidé mi contraseña» (misma respuesta exista o no la cuenta; limitada por IP como el inicio de sesión)*/
  public static final String RUTA_OLVIDO_CONTRASENA = "/api/v1/autenticacion/olvido-contrasena";
  private static final RequestMatcher PDF_PUBLICOS = new OrRequestMatcher(
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/publico/consultas/*/certificados/*/pdf"),
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/publico/consultas/*/personal-pdf/*"));

  @Bean
  PasswordEncoder codificadorContrasenas() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

  /**
   *CORS para el panel alojado en otro dominio (BanaHosting) con la API aquí: solo los orígenes configurados, con
   *cookies, y la cabecera X-XSRF-TOKEN. Sin orígenes configurados no se acepta ningún origen cruzado
   */
  @Bean
  CorsConfigurationSource origenesPanel(PropiedadesSeguridad propiedades) {
    UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
    if (propiedades.origenesPermitidos().isEmpty()) return fuente;
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(propiedades.origenesPermitidos());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-Id"));
    cors.setExposedHeaders(List.of("Content-Disposition", "X-Correlation-Id"));
    cors.setAllowCredentials(true);
    cors.setMaxAge(Duration.ofHours(1));
    fuente.registerCorsConfiguration("/api/**", cors);
    return fuente;
  }

  @Bean
  SecurityFilterChain cadenaSeguridad(HttpSecurity http, ManejadoresSesion manejadores,
      ServicioDetallesUsuario detallesUsuario, LimitadorIntentosPorIp limitador,
      CorsConfigurationSource origenesPanel, PropiedadesSeguridad propiedades,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutorErrores) throws Exception {
    // Los errores de seguridad se delegan al ManejadorGlobalErrores para responder con el mismo formato
    AuthenticationEntryPoint noAutenticado = (solicitud, respuesta, ex) ->
        resolutorErrores.resolveException(solicitud, respuesta, null, ex);
    AccessDeniedHandler denegado = (solicitud, respuesta, ex) ->
        resolutorErrores.resolveException(solicitud, respuesta, null, ex);

    return http
        .cors(cors -> cors.configurationSource(origenesPanel))
        .authorizeHttpRequests(autorizacion -> autorizacion
            .requestMatchers(HttpMethod.GET, "/api/v1/publico/**", "/api/v1/autenticacion/csrf", "/actuator/health")
            .permitAll()
            .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .requestMatchers(HttpMethod.POST, RUTA_OLVIDO_CONTRASENA).permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().denyAll())
        // Token legible por el SPA en la cookie XSRF-TOKEN; se devuelve en la cabecera X-XSRF-TOKEN
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .formLogin(formulario -> formulario
            .loginPage(RUTA_INICIO_SESION)
            .loginProcessingUrl(RUTA_INICIO_SESION)
            .usernameParameter("correo")
            .passwordParameter("contrasena")
            .successHandler(manejadores)
            .failureHandler(manejadores))
        .logout(cierre -> cierre
            .logoutUrl(RUTA_CIERRE_SESION)
            .logoutSuccessHandler(manejadores))
        .exceptionHandling(errores -> errores
            .authenticationEntryPoint(noAutenticado)
            .accessDeniedHandler(denegado))
        // Los PDF públicos se pueden mostrar dentro del portal de consultas (alojado en otro dominio); nada más se enmarca
        .headers(cabeceras -> cabeceras
            .frameOptions(opciones -> opciones.disable())
            .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(PDF_PUBLICOS),
                new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.DENY)))
            .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(PDF_PUBLICOS,
                new StaticHeadersWriter("Content-Security-Policy", "frame-ancestors " + String.join(" ",
                    java.util.stream.Stream.concat(java.util.stream.Stream.of("'self'"), propiedades.origenesPermitidos().stream()).toList())))))
        .httpBasic(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .addFilterBefore(new FiltroLimiteInicioSesion(limitador, resolutorErrores),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new FiltroUsuarioVigente(detallesUsuario, resolutorErrores), AuthorizationFilter.class)
        .build();
  }
}
