package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import com.nexo.usuarioapi.repository.UsuarioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Mantém o comunicacao-api sabendo de quem existe. Reage às mudanças de conta <b>depois do
 * commit</b> — assim o chat nunca fica sabendo de algo que este serviço acabou desfazendo — e
 * sabe refazer o trabalho todo, para quando uma notificação se perdeu.
 */
@Component
public class SincronizacaoContatos {

    private final UsuarioRepository usuarios;
    private final ConfiguracaoService configuracoes;
    private final ComunicacaoClient comunicacao;

    public SincronizacaoContatos(UsuarioRepository usuarios, ConfiguracaoService configuracoes,
                                 ComunicacaoClient comunicacao) {
        this.usuarios = usuarios;
        this.configuracoes = configuracoes;
        this.comunicacao = comunicacao;
    }

    /** {@code fallbackExecution}: fora de uma transação (um teste, por exemplo) também age. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAlterarConta(ContaAlterada evento) {
        if (!comunicacao.habilitado()) return;
        usuarios.findById(evento.usuarioId()).ifPresent(u -> comunicacao.sincronizar(estadoDe(u)));
    }

    /** Reenvia todas as contas. Devolve quantas foram enviadas; lança 503 se o serviço não responder. */
    @Transactional(readOnly = true)
    public int publicarTodos() {
        List<ComunicacaoClient.Contato> todos = usuarios.findAll().stream().map(this::estadoDe).toList();
        comunicacao.sincronizarTodos(todos);
        return comunicacao.habilitado() ? todos.size() : 0;
    }

    private ComunicacaoClient.Contato estadoDe(Usuario u) {
        // A preferência é do professor; para os demais papéis o valor não tem efeito.
        boolean visivel = u.getRole() != Role.PROFESSOR || configuracoes.perfilVisivelParaAlunos(u.getId());
        return new ComunicacaoClient.Contato(u.getId(), u.getNome(), u.getRole().name(), u.getFoto(), visivel);
    }
}
