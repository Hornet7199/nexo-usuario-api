package com.nexo.usuarioapi.security;

import com.nexo.usuarioapi.repository.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Remove diariamente os refresh tokens vencidos — sem isso a tabela cresce sem limite,
 * já que a rotação nunca apaga o token antigo, só o marca como revogado.
 *
 * <p>Os revogados que ainda não venceram ficam de propósito: são eles que sustentam a
 * detecção de reuso (ver {@code RefreshTokenRepository.removerExpirados}). O teto de
 * crescimento passa a ser o número de rotações dentro da janela de validade do refresh
 * token — uma linha por renovação, por usuário, nos últimos 7 dias.
 */
@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "0 0 3 * * *") // 03:00 — fora do horário de uso da escola
    @Transactional
    public void limpar() {
        refreshTokens.removerExpirados(Instant.now());
    }
}
