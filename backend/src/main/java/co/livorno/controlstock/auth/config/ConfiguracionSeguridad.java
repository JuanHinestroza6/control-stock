package co.livorno.controlstock.auth.config;

import co.livorno.controlstock.auth.seguridad.ConvertidorUsuarioJwt;
import co.livorno.controlstock.auth.seguridad.ManejadorErroresSeguridad;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({PropiedadesJwt.class, PropiedadesLogin.class})
public class ConfiguracionSeguridad {

    public static final String ISSUER = "control-stock";

    public static SecretKey clave(PropiedadesJwt props) {
        return new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    static AuthorizationDecision autenticadoSinCambioPendiente(Supplier<? extends Authentication> auth,
            RequestAuthorizationContext contexto) {
        Authentication a = auth.get();
        boolean autenticado = a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
        boolean debeCambiar = a != null && a.getPrincipal() instanceof UsuarioAutenticado u && u.debeCambiarPassword();
        return new AuthorizationDecision(autenticado && !debeCambiar);
    }

    @Bean
    public JwtDecoder jwtDecoder(PropiedadesJwt props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave(props)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(PropiedadesJwt props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(props)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http, UsuarioRepository usuarios) throws Exception {
        ManejadorErroresSeguridad manejador = new ManejadorErroresSeguridad();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Pueden usarse aunque haya un cambio de contraseña pendiente
                        .requestMatchers(HttpMethod.GET, "/api/auth/yo").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/yo/password").authenticated()
                        // Resto: autenticado y sin cambio de contraseña pendiente (si no, 403 con codigo)
                        .anyRequest().access(ConfiguracionSeguridad::autenticadoSinCambioPendiente))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(manejador)
                        .accessDeniedHandler(manejador))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(new ConvertidorUsuarioJwt(usuarios)))
                        .authenticationEntryPoint(manejador)
                        .accessDeniedHandler(manejador));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.origenes:http://localhost:5173}") List<String> origenes) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origenes.stream().map(String::trim).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cfg);
        return fuente;
    }
}
