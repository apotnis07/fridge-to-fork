package com.example.fridge_to_fork;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Allow everything for now so you can build your UI
                        .anyRequest().permitAll());

        // REMOVE OR COMMENT OUT THIS ENTIRE SECTION:
        /*
         * .oauth2ResourceServer(oauth2 -> oauth2
         * .jwt(Customizer.withDefaults())
         * );
         */

        return http.build();
    }

    // @Bean
    // public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // http
    // .csrf(csrf -> csrf.disable()) // Keep disabled for stateless APIs
    // .cors(cors -> cors.configurationSource(corsConfigurationSource()))
    // .sessionManagement(session -> session
    // .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    // )
    // .authorizeHttpRequests(auth -> auth
    // .requestMatchers("/actuator/health").permitAll()
    // .requestMatchers(
    // "/",
    // "/index.html",
    // "/journal.html",
    // "/find.html",
    // "/login.html",
    // "/*.css",
    // "/*.js"
    // ).permitAll()
    // .requestMatchers("/api/**").permitAll() // temp
    // // .requestMatchers("/api/**").authenticated()
    // .anyRequest().authenticated()
    // )
    // // Commented out temporarily for removing manual auth logic
    // // .oauth2ResourceServer(oauth2 -> oauth2
    // // .jwt(Customizer.withDefaults()) // Spring will still validate the Amplify
    // JWT against Cognito
    // // )
    // ;

    // return http.build();
    // }

    // CORS stays the same - ensure your Frontend URL (localhost:3000) is in
    // allowedOrigins
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type")); // Explicit is better
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
