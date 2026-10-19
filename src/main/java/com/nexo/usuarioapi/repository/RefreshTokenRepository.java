package com.nexo.usuarioapi.repository;

import com.nexo.usuarioapi.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Usado pela limpeza agendada. Apaga <b>somente</b> o que já venceu: a linha revogada
     * precisa continuar no banco até o fim da validade original, porque é ela que permite
     * distinguir "token roubado sendo reapresentado" de "token que nunca existiu" em
     * {@code AuthService.refresh}. Antes a condição incluía {@code revogado = true} e a
     * varredura das 03:00 apagava justamente esse rastro: a partir dali o replay de um
     * token vazado caía no ramo de "desconhecido" e levava só 401, sem revogar em cascata
     * as demais sessões do usuário. Na prática a detecção de reuso se reiniciava todo dia.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiraEm < :agora")
    int removerExpirados(@Param("agora") Instant agora);

    /**
     * Derruba todas as sessões ativas do usuário de uma vez. Acionado quando um refresh
     * token já rotacionado é reapresentado — sinal de que uma cópia vazou e não dá para
     * distinguir o dono legítimo de quem a obteve.
     */
    @Modifying
    @Query("update RefreshToken t set t.revogado = true where t.usuario.id = :usuarioId and t.revogado = false")
    int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Encerra as demais sessões do usuário, preservando a que apresentou
     * {@code hashPreservado}. Usado na troca de senha: as outras sessões caem, mas quem
     * acabou de trocar continua onde estava.
     *
     * <p>Para revogar <b>todas</b>, passe uma string vazia — nenhum SHA-256 é vazio, então
     * a condição não poupa nada. Evita um {@code is null} na consulta.
     */
    @Modifying
    @Query("update RefreshToken t set t.revogado = true "
         + "where t.usuario.id = :usuarioId and t.revogado = false and t.tokenHash <> :hashPreservado")
    int revogarOutrasSessoes(@Param("usuarioId") Long usuarioId,
                             @Param("hashPreservado") String hashPreservado);
}
