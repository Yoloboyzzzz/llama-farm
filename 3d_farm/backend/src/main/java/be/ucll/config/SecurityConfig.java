package be.ucll.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthFilter jwtFilter,
            AuthenticationEntryPoint entryPoint
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth

                        /* ================= AUTH ================= */
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/auth/verify").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/profile").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/estimate").authenticated()

                        /* ================= ADMIN - USER MANAGEMENT ================= */
                        .requestMatchers(HttpMethod.GET, "/api/admin/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admin/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/users/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/users/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/users/*/estimate-access").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admin/users/*/reset-password").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/notifications*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admin/notifications*").hasRole("ADMIN")

                        /* ================= CORS PREFLIGHT ================= */
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        /* ================= JOBS ================= */
                        .requestMatchers(HttpMethod.POST, "/api/jobs/create").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/jobs/gcode/*/stl-files/download").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/jobs/gcode/*/stl-files").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/jobs/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/jobs/*/rename").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/jobs/*").hasRole("ADMIN")

                        /* ================= GCODE FILES ================= */
                        .requestMatchers(HttpMethod.POST, "/api/gcode-files/*/requeue").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/gcode-files/*").hasRole("ADMIN")

                        /* ================= PRINTERS ================= */
                        .requestMatchers(HttpMethod.POST, "/api/printers/start-now/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/printers/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/printers/*/set-idle").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/api/printers/*/in-use").hasRole("ADMIN")

                        /* ================= PRINTER PROFILES ================= */
                        .requestMatchers(HttpMethod.GET,    "/api/admin/printer-profiles").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,   "/api/admin/printer-profiles/upload").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/printer-profiles/*").hasRole("ADMIN")

                        /* ================= FILES ================= */
                        .requestMatchers("/files/**").authenticated()

                        /* ================= MATERIAL/COLOR ================= */
                        .requestMatchers("/api/material-colors").permitAll()

                        /* ================= FALLBACK ================= */
                        .anyRequest().authenticated()
                )

                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .addFilterBefore(
                        jwtFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /* ================= CORS ================= */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        cfg.setAllowedOriginPatterns(List.of(
            "https://llama-farm.be",
            "https://www.llama-farm.be",
            "http://localhost:*",           // Any localhost port
            "http://192.168.*.*:*",         // Any IP on 192.168.x.x network
            "http://10.*.*.*:*"             // Any IP on 10.x.x.x network (if needed)
        ));

        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /* ================= AUTH ENTRY POINT ================= */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthorized\"}");
        };
    }
}