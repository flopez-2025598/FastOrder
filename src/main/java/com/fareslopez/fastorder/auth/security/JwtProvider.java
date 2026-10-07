package com.fareslopez.fastorder.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Genera y valida los JWT. El token lleva el email (subject) y el rol (claim "rol"),
 * así cada petición se autentica sin sesión en el servidor (STATELESS).
 */
@Component
public class JwtProvider {

    public static final String CLAIM_ROL = "rol";

    /**
     * Formato estricto de un JWS compacto: header.payload.firma en Base64URL sin padding.
     * Cada segmento debe tener una longitud Base64 válida (longitud % 4 != 1); así se rechazan
     * tokens con caracteres extra que el decodificador Base64 ignoraría silenciosamente.
     */
    private static final Pattern SEGMENTO_BASE64URL = Pattern.compile("^[A-Za-z0-9_-]+$");

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret debe tener al menos 32 bytes (256 bits)");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String email, String rol) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_ROL, rol)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + jwtExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida formato, firma y expiración. Lanza JwtException si el token no es válido.
     */
    public Claims parseClaims(String token) {
        validarFormato(token);
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private void validarFormato(String token) {
        if (token == null) {
            throw new MalformedJwtException("Token vacío");
        }
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3) {
            throw new MalformedJwtException("El token debe tener 3 segmentos (header.payload.firma)");
        }
        for (String parte : partes) {
            if (parte.isEmpty() || parte.length() % 4 == 1 || !SEGMENTO_BASE64URL.matcher(parte).matches()) {
                throw new MalformedJwtException("Segmento del token con formato Base64URL inválido");
            }
        }
    }
}