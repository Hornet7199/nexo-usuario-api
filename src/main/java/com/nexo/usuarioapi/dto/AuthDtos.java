package com.nexo.usuarioapi.dto;

import com.nexo.usuarioapi.domain.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    /**
     * O teto de 128 caracteres só impede hashear um corpo gigante: o BCrypt ignora tudo
     * depois de 72 bytes. É maior que o limite da política (50) de propósito — contas
     * criadas quando o limite era 72 precisam continuar entrando.
     */
    public record LoginRequest(@NotBlank @Size(max = 254) String login,
                               @NotBlank @Size(max = 128) String senha) {}

    /** {@code precisaTrocarSenha}: o cliente desvia para a troca de senha antes de qualquer tela. */
    public record UsuarioDTO(Long id, String nome, String cargo, String foto, String role,
                             boolean precisaTrocarSenha) {
        public static UsuarioDTO of(Usuario u) {
            return new UsuarioDTO(u.getId(), u.getNome(), u.getCargo(), u.getFoto(), u.getRole().name(),
                    u.isPrecisaTrocarSenha());
        }
    }

    /**
     * O que o cliente recebe no corpo. De propósito <b>sem</b> o refresh token: ele viaja
     * em cookie HttpOnly (ver {@code RefreshTokenCookie}) justamente para ficar fora do
     * alcance do JavaScript da página. O access token continua no corpo porque o cliente
     * precisa montar o header {@code Authorization} — mas vive só na memória da aba e
     * expira em 15 minutos.
     */
    public record TokenResponse(String token, UsuarioDTO usuario) {}

    /**
     * Resultado interno de um login ou de uma rotação: o corpo da resposta mais o refresh
     * token em claro, que o controller converte em cookie e não deixa chegar ao JSON.
     */
    public record SessaoEmitida(TokenResponse resposta, String refreshTokenPlano) {}

    public record TrocaSenhaRequest(@NotBlank @Size(max = 128) String senhaAtual,
                                    @NotBlank @Size(min = 8, message = "A nova senha deve ter ao menos 8 caracteres")
                                    String novaSenha) {}

    /** Pergunta "essa senha seria aceita?" — nada é gravado. */
    public record ValidarSenhaRequest(@Size(max = 128) String novaSenha) {}

    /** {@code codigo} e {@code motivo} vêm nulos quando a senha é válida. */
    public record ValidacaoSenhaResponse(boolean valida, String codigo, String motivo) {}

    public record AtualizarPerfilRequest(@Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.") String nome) {}
}
