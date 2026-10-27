package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Único ponto em que este serviço fala com o gamificacao-api. O ranking precisa conhecer cada
 * aluno (nome, foto) e o que ele escolheu esconder; este serviço é a fonte de verdade e avisa
 * quando algo disso muda. XP, desafios e o resto da gamificação são de lá, e nunca viajam
 * nesta direção.
 *
 * <p>Com {@code nexo.gamificacao.enabled=false} nenhuma chamada é feita: é o modo dos testes e
 * do serviço isolado. Todas as chamadas são melhor esforço (o aluno já foi cadastrado ou já
 * editou o perfil); o que se perder volta com a ressincronização.
 */
@Component
public class GamificacaoClient {

    private static final Logger log = LoggerFactory.getLogger(GamificacaoClient.class);

    /** Um aluno como o ranking o conhece. */
    public record Jogador(Long id, Long usuarioId, String nome, String foto,
                          boolean exibirNoRanking, boolean perfilPublico) {
        Map<String, Object> comoCorpo() {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("usuarioId", usuarioId);
            corpo.put("nome", nome);
            corpo.put("foto", foto);
            corpo.put("exibirNoRanking", exibirNoRanking);
            corpo.put("perfilPublico", perfilPublico);
            return corpo;
        }
    }

    private final boolean habilitado;
    private final RestClient http;

    public GamificacaoClient(@Value("${nexo.gamificacao.enabled:true}") boolean habilitado,
                             @Value("${nexo.gamificacao.url:http://localhost:8084}") String url,
                             @Value("${nexo.gamificacao.timeout-ms:3000}") int timeoutMs) {
        this.habilitado = habilitado;
        this.http = ClienteHttp.criar(url, timeoutMs);
    }

    public boolean habilitado() {
        return habilitado;
    }

    /** Aluno novo ou alterado, com todos os campos — só a gestão pode (quem cadastra). */
    public void sincronizarAluno(Jogador jogador) {
        enviar("PUT", "/api/sync/alunos/" + jogador.id(), jogador.comoCorpo());
    }

    /** Nome e foto: o próprio aluno pode, e é o que ele usa ao editar o perfil. */
    public void sincronizarPerfil(Long usuarioId, String nome, String foto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nome", nome);
        corpo.put("foto", foto);
        enviar("PATCH", "/api/sync/usuarios/" + usuarioId + "/perfil", corpo);
    }

    public void sincronizarPrivacidade(Long usuarioId, boolean exibirNoRanking, boolean perfilPublico) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("exibirNoRanking", exibirNoRanking);
        corpo.put("perfilPublico", perfilPublico);
        enviar("PATCH", "/api/sync/usuarios/" + usuarioId + "/privacidade", corpo);
    }

    /** Ressincronização: estrita, quem a pediu precisa saber se não funcionou. */
    public void sincronizarAlunoEstrito(Jogador jogador) {
        if (!habilitado) return;
        try {
            http.put().uri("/api/sync/alunos/{id}", jogador.id())
                    .header(HttpHeaders.AUTHORIZATION, ClienteHttp.tokenDaRequisicao())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jogador.comoCorpo())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("ressincronização do aluno {} falhou: {}", jogador.id(), e.getMessage());
            throw ApiException.servicoIndisponivel(
                    "Não foi possível comunicar os alunos ao serviço de gamificação. Tente novamente em instantes.");
        }
    }

    private void enviar(String metodo, String caminho, Map<String, Object> corpo) {
        if (!habilitado) return;
        try {
            var requisicao = "PATCH".equals(metodo) ? http.patch() : http.put();
            requisicao.uri(caminho)
                    .header(HttpHeaders.AUTHORIZATION, ClienteHttp.tokenDaRequisicao())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | ApiException e) {
            log.warn("aviso a {} não entregue ao gamificacao-api: {}", caminho, e.getMessage());
        }
    }
}
