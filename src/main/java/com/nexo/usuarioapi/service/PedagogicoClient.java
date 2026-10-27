package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Único ponto em que este serviço fala com o pedagogico-api: turmas e matérias são dele,
 * aqui só existem como id. Também é por aqui que o pedagógico fica sabendo de quem é
 * cadastrado ou muda de nome, turma ou foto (as chamadas {@code sincronizar*}).
 *
 * <p>A chamada repassa o {@code Authorization} da requisição em curso. Os dois serviços
 * compartilham o segredo do JWT, então o token emitido aqui vale lá — e o recorte por
 * papel continua sendo o de lá (um professor só vê as turmas que leciona).
 *
 * <p>Com {@code nexo.pedagogico.enabled=false} o catálogo fica desligado: cadastros
 * aceitam qualquer id e os nomes vêm vazios. É o modo dos testes e do serviço isolado.
 */
@Component
public class PedagogicoClient {

    private static final Logger log = LoggerFactory.getLogger(PedagogicoClient.class);

    private record Item(Long id, String nome) {}

    private final boolean habilitado;
    private final RestClient http;

    public PedagogicoClient(@Value("${nexo.pedagogico.enabled:true}") boolean habilitado,
                            @Value("${nexo.pedagogico.url:http://localhost:8082}") String url,
                            @Value("${nexo.pedagogico.timeout-ms:3000}") int timeoutMs) {
        this.habilitado = habilitado;
        this.http = ClienteHttp.criar(url, timeoutMs);
    }

    public boolean habilitado() {
        return habilitado;
    }

    /** Turmas que o chamador enxerga (id → nome). Lança 503 se o pedagogico-api não responder. */
    public Map<Long, String> turmas() {
        return buscar("/api/turmas");
    }

    /** Matérias do catálogo (id → nome). Lança 503 se o pedagogico-api não responder. */
    public Map<Long, String> materias() {
        return buscar("/api/materias");
    }

    private Map<Long, String> buscar(String caminho) {
        if (!habilitado) return Map.of();
        try {
            List<Item> itens = http.get().uri(caminho)
                    .header(HttpHeaders.AUTHORIZATION, ClienteHttp.tokenDaRequisicao())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Item>>() {});
            Map<Long, String> porId = new LinkedHashMap<>();
            if (itens != null) itens.forEach(i -> porId.put(i.id(), i.nome()));
            return porId;
        } catch (RestClientException e) {
            log.warn("pedagogico-api indisponível em {}: {}", caminho, e.getMessage());
            throw ApiException.servicoIndisponivel(
                    "Não foi possível consultar o catálogo pedagógico. Tente novamente em instantes.");
        }
    }

    // ── Sincronização: o pedagogico-api guarda uma cópia enxuta de aluno e professor ──

    /**
     * Avisa o pedagógico de um aluno novo ou alterado. Estrita: se falhar, lança 503 e o
     * cadastro inteiro é desfeito — aluno que existe só aqui não teria nota nem frequência.
     */
    public void sincronizarAluno(Long id, Long usuarioId, String nome, Long turmaId, String foto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("usuarioId", usuarioId);
        corpo.put("nome", nome);
        corpo.put("turmaId", turmaId);
        corpo.put("foto", foto);
        enviar("PUT", "/api/sync/alunos/" + id, corpo, true);
    }

    /** Idem para professor, com as matérias que leciona. */
    public void sincronizarProfessor(Long id, Long usuarioId, String nome, String foto, java.util.Collection<Long> materiaIds) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("usuarioId", usuarioId);
        corpo.put("nome", nome);
        corpo.put("foto", foto);
        corpo.put("materiaIds", materiaIds == null ? List.of() : List.copyOf(materiaIds));
        enviar("PUT", "/api/sync/professores/" + id, corpo, true);
    }

    /**
     * Nome ou foto de uma conta que mudou. Melhor esforço: o usuário já fez o que queria
     * e a cópia atrasada se corrige na próxima mudança — não vale derrubar a ação dele.
     */
    public void sincronizarPerfil(Long usuarioId, String nome, String foto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("nome", nome);
        corpo.put("foto", foto);
        enviar("PATCH", "/api/sync/usuarios/" + usuarioId + "/perfil", corpo, false);
    }

    private void enviar(String metodo, String caminho, Map<String, Object> corpo, boolean estrito) {
        if (!habilitado) return;
        try {
            var requisicao = "PATCH".equals(metodo) ? http.patch() : http.put();
            requisicao.uri(caminho)
                    .header(HttpHeaders.AUTHORIZATION, ClienteHttp.tokenDaRequisicao())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | ApiException e) {
            if (estrito) {
                log.warn("falha ao sincronizar com o pedagogico-api em {}: {}", caminho, e.getMessage());
                if (e instanceof ApiException api) throw api;
                throw ApiException.servicoIndisponivel(
                        "Não foi possível comunicar o cadastro ao serviço pedagógico. Tente novamente em instantes.");
            }
            log.warn("sincronização de perfil não entregue ao pedagogico-api ({}): {}", caminho, e.getMessage());
        }
    }
}
