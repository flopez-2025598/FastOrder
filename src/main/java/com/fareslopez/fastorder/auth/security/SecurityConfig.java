package com.fareslopez.fastorder.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String REPARTIDOR = "REPARTIDOR";
    private static final String CLIENTE = "CLIENTE";

    private final JwtAuthFilter jwtAuthFilter;
    private final JsonSecurityHandlers jsonSecurityHandlers;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API REST con JWT: no hay cookies de sesión, CSRF no aplica
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // STATELESS: el servidor no guarda sesión, cada petición trae su JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonSecurityHandlers)
                        .accessDeniedHandler(jsonSecurityHandlers))
                .authorizeHttpRequests(auth -> auth
                        // A. Autenticación (público)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()

                        // B. Comercios y productos
                        .requestMatchers(HttpMethod.GET, "/api/v1/comercios", "/api/v1/comercios/*/productos").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/comercios", "/api/v1/comercios/*/productos").hasRole(ADMIN)

                        // C. Pedidos
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos").hasRole(CLIENTE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/mis-pedidos").hasRole(CLIENTE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/disponibles").hasAnyRole(REPARTIDOR, ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/estado").hasAnyRole(REPARTIDOR, ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/cancelar").hasAnyRole(CLIENTE, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos").hasRole(ADMIN)
                        // Seguimiento de un pedido: el servicio valida que el CLIENTE sea el dueño
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/*").authenticated()

                        // Todo lo demás requiere token
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
