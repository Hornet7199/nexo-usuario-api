package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.domain.Aluno;
import com.nexo.usuarioapi.repository.AlunoRepository;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Mantém o gamificacao-api sabendo de quem joga. Reage às mudanças de conta <b>depois do
 * commit</b> e sabe refazer o trabalho todo, para quando uma notificação se perdeu.
 *
 * <p>Quem cadastra (diretor, admin) manda o aluno inteiro; o próprio aluno, ao editar o perfil
 * ou a privacidade, só pode mexer no que é dele — o gamificacao-api faz valer essa diferença.
 */
@Component
public class SincronizacaoJogadores {

    private final AlunoRepository alunos;
    private final ConfiguracaoService configuracoes;
    private final GamificacaoClient gamificacao;

    public SincronizacaoJogadores(AlunoRepository alunos, ConfiguracaoService configuracoes,
                                  GamificacaoClient gamificacao) {
        this.alunos = alunos;
        this.configuracoes = configuracoes;
        this.gamificacao = gamificacao;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAlterarConta(ContaAlterada evento) {
        if (!gamificacao.habilitado()) return;
        alunos.findByUsuarioId(evento.usuarioId()).ifPresent(a -> {
            if (operadorGere() || a.getUsuario() == null) {
                gamificacao.sincronizarAluno(estadoDe(a));
            } else {
                var u = a.getUsuario();
                gamificacao.sincronizarPerfil(evento.usuarioId(), u.getNome(), u.getFoto());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAlterarPrivacidade(PrivacidadeDoAlunoAlterada evento) {
        if (!gamificacao.habilitado()) return;
        var priv = configuracoes.privacidadeDoAluno(evento.usuarioId());
        gamificacao.sincronizarPrivacidade(evento.usuarioId(), priv.exibirNoRanking(), priv.perfilPublico());
    }

    /** Reenvia todos os alunos. Devolve quantos foram enviados; lança 503 se o serviço não responder. */
    @Transactional(readOnly = true)
    public int publicarTodos() {
        List<Aluno> todos = alunos.findAll();
        todos.forEach(a -> gamificacao.sincronizarAlunoEstrito(estadoDe(a)));
        return gamificacao.habilitado() ? todos.size() : 0;
    }

    private GamificacaoClient.Jogador estadoDe(Aluno a) {
        Long usuarioId = a.getUsuario() != null ? a.getUsuario().getId() : null;
        var priv = usuarioId == null ? new ConfiguracaoService.PrivacidadeDoAluno(true, true)
                : configuracoes.privacidadeDoAluno(usuarioId);
        // Nome e foto são da conta (é ela que o perfil edita); o registro do aluno só os herda.
        String nome = a.getUsuario() != null ? a.getUsuario().getNome() : a.getNome();
        String foto = a.getUsuario() != null ? a.getUsuario().getFoto() : a.getFoto();
        return new GamificacaoClient.Jogador(a.getId(), usuarioId, nome, foto,
                priv.exibirNoRanking(), priv.perfilPublico());
    }

    private static boolean operadorGere() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u
                && ("DIRETOR".equals(u.role()) || "ADMIN".equals(u.role()));
    }
}
