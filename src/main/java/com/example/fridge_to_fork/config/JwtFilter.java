package com.example.fridge_to_fork.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URL;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String USER_POOL_ID = "us-east-1_E8orXN70T";
    private static final String REGION = "us-east-1";
    private static final String JWKS_URL = "https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID
            + "/.well-known/jwks.json";

    private final JWKSource<SecurityContext> jwkSource;

    public JwtFilter() throws Exception {
        this.jwkSource = JWKSourceBuilder
                .create(new URL(JWKS_URL))
                .retrying(true)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        log.debug("JWT filter intercepted: {}", path);

        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Rejected request to {} — missing or invalid Authorization header", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = authHeader.substring(7);

        try {
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                    com.nimbusds.jose.JWSAlgorithm.RS256, jwkSource));

            var claims = jwtProcessor.process(token, null);
            String userId = claims.getSubject();
            log.debug("JWT validated for userId: {}", userId);

            request.setAttribute("userId", userId);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("JWT validation failed for {}: {} — {}", path, e.getClass().getSimpleName(), e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}