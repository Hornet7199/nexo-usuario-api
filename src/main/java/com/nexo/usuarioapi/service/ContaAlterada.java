package com.nexo.usuarioapi.service;

/**
 * Algo que o chat mostra mudou numa conta: ela foi criada, ou seu nome, foto ou preferência de
 * privacidade mudou. Publicado de dentro da transação que gravou a mudança; quem reage a ele
 * ({@link SincronizacaoContatos}) só age depois do commit.
 */
public record ContaAlterada(Long usuarioId) {}
