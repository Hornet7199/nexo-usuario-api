package com.nexo.usuarioapi.security;

/**
 * Principal leve extraído do JWT — evita ida ao banco a cada request.
 *
 * @param jti identidade do access token que autenticou esta requisição. É o que permite
 *            ao logout invalidar exatamente esta emissão (ver {@link AccessTokensRevogados}).
 */
public record UsuarioAutenticado(Long id, String login, String nome, String role, String jti) {
}
