package com.nexo.usuarioapi.security;

import com.nexo.usuarioapi.domain.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AccessTokensRevogados revogados;

    public JwtAuthFilter(JwtService jwtService, AccessTokensRevogados revogados) {
        this.jwtService = jwtService;
        this.revogados = revogados;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            jwtService.validar(token).ifPresent(this::autenticar);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Só autentica com o conjunto completo de claims esperado, e com um papel que existe
     * de fato em {@link Role}. Sem essa checagem, um token assinado mas malformado passava
     * adiante com {@code uid} nulo (quebrando toda regra que compara o dono do recurso)
     * ou com a autoridade literal {@code ROLE_null}. Faltando qualquer claim, a requisição
     * segue sem autenticação e o endpoint protegido responde 401.
     *
     * <p>O {@code jti} entra nessa exigência: sem ele o token não é revogável, e um token
     * emitido antes desta versão simplesmente força uma renovação — o cliente toma 401,
     * o interceptor chama /refresh e segue com um token novo.
     */
    private void autenticar(Claims claims) {
        String login = claims.getSubject();
        String role = claims.get("role", String.class);
        Long uid = claims.get("uid", Long.class);
        String jti = claims.getId();
        if (login == null || login.isBlank() || uid == null || jti == null || !papelConhecido(role)) return;

        // Assinatura boa e prazo em dia não bastam: o logout invalida a emissão na hora.
        if (revogados.revogado(jti)) return;

        var principal = new UsuarioAutenticado(uid, login, claims.get("nome", String.class), role, jti);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** A role precisa ser uma das do enum — não basta ser uma string qualquer. */
    private static boolean papelConhecido(String role) {
        if (role == null) return false;
        for (Role r : Role.values()) {
            if (r.name().equals(role)) return true;
        }
        return false;
    }
}
