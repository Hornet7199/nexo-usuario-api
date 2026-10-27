package com.nexo.usuarioapi.dto;

import com.nexo.usuarioapi.domain.Usuario;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class ContaDtos {

    private ContaDtos() {}

    /** Uma conta como o administrador precisa vê-la: sem dado pessoal, só identidade. */
    public record ContaDto(Long id, String login, String nome, String cargo, String papel, boolean ativo,
                           Instant criadoEm) {
        public static ContaDto of(Usuario u) {
            return new ContaDto(u.getId(), u.getLogin(), u.getNome(), u.getCargo(), u.getRole().name(),
                    u.isAtivo(), u.getCriadoEm());
        }
    }

    public record AtualizarAtivoRequest(@NotNull(message = "Informe se a conta fica ativa.") Boolean ativo) {}

    /** Quantas contas há, por papel, e quantas estão desativadas — o lado de contas do painel admin. */
    public record ResumoContasDto(long contas, long contasInativas, long alunos, long professores,
                                  long diretores, long admins) {}

    /** A senha em claro existe só nesta resposta. */
    public record SenhaRedefinidaDto(String login, String senhaProvisoria) {}
}
