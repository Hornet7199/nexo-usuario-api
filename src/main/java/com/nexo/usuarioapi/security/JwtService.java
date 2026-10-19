package com.nexo.usuarioapi.security;

import com.nexo.usuarioapi.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    /** HMAC-SHA256 exige chave de pelo menos 256 bits — 32 caracteres ASCII. */
    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(@Value("${nexo.jwt.secret}") String secret,
                      @Value("${nexo.jwt.access-token-minutes}") long accessTokenMinutes) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException(
                    "nexo.jwt.secret tem " + bytes.length + " bytes; o mínimo é " + TAMANHO_MINIMO_SEGREDO
                    + " (requisito do HMAC-SHA, que precisa de uma chave de 256 bits). "
                    + "Defina NEXO_JWT_SECRET no ambiente — gere um com `openssl rand -base64 48`.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
    }

    public String gerarAccessToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                // jti: identidade própria deste token, para que o logout consiga invalidar
                // esta emissão específica sem tocar nas outras sessões do mesmo usuário
                // (ver AccessTokensRevogados).
                .id(UUID.randomUUID().toString())
                .subject(usuario.getLogin())
                .claim("uid", usuario.getId())
                .claim("role", usuario.getRole().name())
                .claim("nome", usuario.getNome())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** Retorna as claims se o token for válido e não expirado; vazio caso contrário. */
    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
