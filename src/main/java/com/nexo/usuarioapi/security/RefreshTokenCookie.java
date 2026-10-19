package com.nexo.usuarioapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Monta o cookie que transporta o refresh token.
 *
 * <p>O refresh token é o ativo de vida longa da sessão (7 dias): antes ele voltava no
 * corpo JSON do login e o cliente o guardava em {@code localStorage}, ou seja, qualquer
 * script da página conseguia lê-lo e exfiltrá-lo. Aqui ele sai do alcance do JavaScript:
 * o navegador guarda e reenvia o cookie sozinho, e a página nunca vê o valor.
 *
 * <p>Os atributos, e o motivo de cada um:
 * <ul>
 *   <li><b>HttpOnly</b> — {@code document.cookie} não enxerga; é o ponto do exercício.</li>
 *   <li><b>Secure</b> — acompanha o esquema da conexão. Em produção o Render termina o
 *       TLS e {@code forward-headers-strategy: framework} faz {@code isSecure()} devolver
 *       true, então o cookie fica restrito a HTTPS; em dev sobre http://localhost ele
 *       precisa sair sem a flag, senão o navegador descarta e o login não persiste.</li>
 *   <li><b>SameSite=Strict</b> — requisição partida de outro site não leva o cookie, o
 *       que fecha o CSRF contra {@code /api/auth/refresh} (a proteção CSRF do Spring está
 *       desligada porque o resto da API se autentica por header Bearer, não por cookie).
 *       Strict não incomoda aqui porque a renovação é XHR depois do carregamento, nunca
 *       uma navegação de entrada vinda de link externo.</li>
 *   <li><b>Path</b> — restrito a {@code /api/auth}: o resto da API, que é quase tudo,
 *       nunca vê o cookie passar. Não dá para apertar mais porque dois endpoints precisam
 *       lê-lo — {@code /refresh}, que o rotaciona, e {@code /logout}, que revoga só a
 *       sessão que o apresentou.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookie {

    public static final String NOME = "nexo_refresh";

    /** Ver a nota sobre Path acima: {@code /refresh} e {@code /logout} consomem o cookie. */
    private static final String CAMINHO = "/api/auth";

    private final Duration ttl;

    public RefreshTokenCookie(@Value("${nexo.jwt.refresh-token-days}") long refreshTokenDays) {
        this.ttl = Duration.ofDays(refreshTokenDays);
    }

    /** Cookie que entrega um refresh token recém-emitido (login ou rotação). */
    public String emitir(String refreshTokenPlano, boolean conexaoSegura) {
        return base(conexaoSegura).value(refreshTokenPlano).maxAge(ttl).build().toString();
    }

    /**
     * Cookie que apaga o anterior no navegador. Complementa o logout — que já revoga a
     * linha no banco —, para não deixar no cliente um valor que só renderia 401.
     */
    public String limpar(boolean conexaoSegura) {
        return base(conexaoSegura).value("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(boolean conexaoSegura) {
        return ResponseCookie.from(NOME)
                .httpOnly(true)
                .secure(conexaoSegura)
                .sameSite("Strict")
                .path(CAMINHO);
    }
}
