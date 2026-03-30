package com.example.fridge_to_fork;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URL;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String USER_POOL_ID = "us-east-1_E8orXN70T";
    private static final String REGION = "us-east-1";
    private static final String JWKS_URL = "https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID
            + "/.well-known/jwks.json";

    // Build JWKSource once — not on every request
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

        // Only protect API routes — let everything else through
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
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

            // Pass user ID to controllers via header
            request.setAttribute("userId", userId);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}