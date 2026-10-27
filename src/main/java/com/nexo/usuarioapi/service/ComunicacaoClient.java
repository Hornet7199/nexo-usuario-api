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
import java.util.List;
import java.util.Map;

/**
 * Único ponto em que este serviço fala com o comunicacao-api. O chat precisa saber quem existe,
 * com que papel, nome e foto, e se o professor quer a foto visível para alunos; este serviço é
 * a fonte de verdade e avisa quando algo disso muda.
 *
 * <p>Com {@code nexo.comunicacao.enabled=false} nenhuma chamada é feita: é o modo dos testes e
 * do serviço isolado.
 */
@Component
public class ComunicacaoClient {

    private static final Logger log = LoggerFactory.getLogger(ComunicacaoClient.class);

    /** Estado de uma conta como o chat a conhece. */
    public record Contato(Long id, String nome, String papel, String foto, boolean perfilVisivelParaAlunos) {
        Map<String, Object> comoCorpo() {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("nome", nome);
            corpo.put("papel", papel);
            corpo.put("foto", foto);
            corpo.put("perfilVisivelParaAlunos", perfilVisivelParaAlunos);
            return corpo;
        }

        Map<String, Object> comoItemDeLote() {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("id", id);
            corpo.putAll(comoCorpo());
            return corpo;
        }
    }

    private final boolean habilitado;
    private final RestClient http;

    public ComunicacaoClient(@Value("${nexo.comunicacao.enabled:true}") boolean habilitado,
                             @Value("${nexo.comunicacao.url:http://localhost:8083}") String url,
                             @Value("${nexo.comunicacao.timeout-ms:3000}") int timeoutMs) {
        this.habilitado = habilitado;
        this.http = ClienteHttp.criar(url, timeoutMs);
    }

    public boolean habilitado() {
        return habilitado;
    }

    /**
     * Uma conta nova ou alterada. Melhor esforço: o cadastro ou a edição já aconteceu, e uma
     * cópia atrasada se corrige na próxima alteração ou numa ressincronização — não vale
     * desfazer o que a pessoa fez porque o chat está fora do ar.
     */
    public void sincronizar(Contato contato) {
        if (!habilitado) return;
        try {
            http.put().uri("/api/sync/contatos/{id}", contato.id())
                    .header(HttpHeaders.AUTHORIZATION, ClienteHttp.tokenDaRequisicao())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(contato.comoCorpo())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | ApiException e) {
            log.warn("contato {} não entregue ao comunicacao-api: {}", contato.id(), e.getMessage());
        }
    }

    /** Ressincronização completa. Estrita: quem a pediu precisa saber se não funcionou. */
    public void sincronizarTodos(List<Contato> contatos) {
        if (!habilitado) return;
        try {
            http.put().uri("/api/sync/contatos")
                    .header(HttpHeaders.AUTHORIZATION, ClienteHttp.tokenDaRequisicao())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(contatos.stream().map(Contato::comoItemDeLote).toList())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("ressincronização de contatos falhou: {}", e.getMessage());
            throw ApiException.servicoIndisponivel(
                    "Não foi possível comunicar os contatos ao serviço de comunicação. Tente novamente em instantes.");
        }
    }
}
