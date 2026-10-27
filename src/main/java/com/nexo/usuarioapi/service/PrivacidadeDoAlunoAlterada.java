package com.nexo.usuarioapi.service;

/**
 * O aluno mudou o que esconde do ranking (exibir no ranking, perfil público). Publicado de dentro
 * da transação que gravou a escolha; {@link SincronizacaoJogadores} só age depois do commit.
 */
public record PrivacidadeDoAlunoAlterada(Long usuarioId) {}
