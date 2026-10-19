package com.nexo.usuarioapi.web;

import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.dto.AuthDtos.LoginRequest;
import com.nexo.usuarioapi.dto.AuthDtos.SessaoEmitida;
import com.nexo.usuarioapi.dto.AuthDtos.TokenResponse;
import com.nexo.usuarioapi.dto.AuthDtos.UsuarioDTO;
import com.nexo.usuarioapi.security.ClienteIp;
import com.nexo.usuarioapi.security.RefreshTokenCookie;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import com.nexo.usuarioapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshCookie;
    private final ClienteIp clienteIp;

    public AuthController(AuthService authService, RefreshTokenCookie refreshCookie, ClienteIp clienteIp) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
        this.clienteIp = clienteIp;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        return comCookie(authService.login(request.login(), request.senha(), clienteIp.de(http)), http);
    }

    /**
     * O refresh token vem do cookie HttpOnly, não do corpo: o cliente não tem como lê-lo
     * para montar um JSON, e é exatamente essa a intenção. O cookie é enviado pelo
     * navegador só nesta rota (ver {@link RefreshTokenCookie}).
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookie.NOME, required = false) String refreshToken,
            HttpServletRequest http) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("Refresh token ausente.");
        }
        return comCookie(authService.refresh(refreshToken, clienteIp.de(http)), http);
    }

    /**
     * Encerra só esta sessão. O cookie identifica qual delas — daí ele ser lido aqui
     * também, e não apenas em {@link #refresh}.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @CookieValue(name = RefreshTokenCookie.NOME, required = false) String refreshToken,
            HttpServletRequest http) {
        authService.logout(usuario, refreshToken, clienteIp.de(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.limpar(http.isSecure()))
                .build();
    }

    @GetMapping("/me")
    public UsuarioDTO me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return authService.me(usuario.id());
    }

    /** Devolve o corpo da sessão e manda o refresh token recém-emitido no cookie. */
    private ResponseEntity<TokenResponse> comCookie(SessaoEmitida sessao, HttpServletRequest http) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie.emitir(sessao.refreshTokenPlano(), http.isSecure()))
                .body(sessao.resposta());
    }
}
