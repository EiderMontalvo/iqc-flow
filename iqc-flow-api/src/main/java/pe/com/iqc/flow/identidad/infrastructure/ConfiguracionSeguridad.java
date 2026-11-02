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
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 *autenticación por sesión con cookie y protección CSRF para el SPA. Todo lo que no se declara
 *explícitamente queda denegado. Los permisos de negocio se validan con @PreAuthorize en los servicios.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {
  public static final String RUTA_INICIO_SESION = "/api/v1/autenticacion/inicio-sesion";
  public static final String RUTA_CIERRE_SESION = "/api/v1/autenticacion/cierre-sesion";

  @Bean
  PasswordEncoder codificadorContrasenas() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

  @Bean
  SecurityFilterChain cadenaSeguridad(HttpSecurity http, ManejadoresSesion manejadores,
      ServicioDetallesUsuario detallesUsuario, LimitadorIntentosPorIp limitador,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutorErrores) throws Exception {
    // Los errores de seguridad se delegan al ManejadorGlobalErrores para responder con el mismo formato
    AuthenticationEntryPoint noAutenticado = (solicitud, respuesta, ex) ->
        resolutorErrores.resolveException(solicitud, respuesta, null, ex);
    AccessDeniedHandler denegado = (solicitud, respuesta, ex) ->
        resolutorErrores.resolveException(solicitud, respuesta, null, ex);

    return http
        .authorizeHttpRequests(autorizacion -> autorizacion
            .requestMatchers(HttpMethod.GET, "/api/v1/publico/**", "/api/v1/autenticacion/csrf", "/actuator/health")
            .permitAll()
            .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
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
        .httpBasic(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .addFilterBefore(new FiltroLimiteInicioSesion(limitador, resolutorErrores),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new FiltroUsuarioVigente(detallesUsuario, resolutorErrores), AuthorizationFilter.class)
        .build();
  }
}
