package com.nexo.usuarioapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que o usuario-api avisa ao gamificacao-api, contra um servidor de mentira que registra as
 * chamadas: cadastro do aluno, edição do perfil, privacidade do ranking, a ressincronização
 * completa — e que a falha da gamificação não desfaz o que o usuário fez.
 */
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:teste-sync-gamificacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class GamificacaoSyncTest extends TesteApiBase {

    private record Chamada(String metodo, String caminho, String autorizacao, String corpo) {}

    private static final List<Chamada> CHAMADAS = new CopyOnWriteArrayList<>();
    private static volatile int statusDaResposta = 204;
    private static final HttpServer SERVIDOR = subir();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static HttpServer subir() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/", troca -> {
                String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                CHAMADAS.add(new Chamada(troca.getRequestMethod(), troca.getRequestURI().getPath(),
                        troca.getRequestHeaders().getFirst("Authorization"), corpo));
                troca.sendResponseHeaders(statusDaResposta, -1);
                troca.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void apontarParaOServidorDeMentira(DynamicPropertyRegistry registro) {
        registro.add("nexo.gamificacao.enabled", () -> "true");
        registro.add("nexo.gamificacao.url", () -> "http://127.0.0.1:" + SERVIDOR.getAddress().getPort());
    }

    @AfterAll
    static void parar() {
        SERVIDOR.stop(0);
    }

    @BeforeEach
    void limpar() {
        CHAMADAS.clear();
        statusDaResposta = 204;
    }

    private Chamada unicaChamada() {
        // o aviso sai depois do commit, na mesma thread do request: já chegou quando o mvc retorna
        assertThat(CHAMADAS).hasSize(1);
        return CHAMADAS.get(0);
    }

    private JsonNode corpoDe(Chamada c) throws Exception {
        return JSON.readTree(c.corpo());
    }

    /** Cadastra um aluno de verdade (conta + registro de aluno) e devolve o token dele. */
    private String tokenDeUmAluno() throws Exception {
        var r = mvc.perform(post("/api/alunos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.DIRETOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluno Sync Teste\",\"turmaId\":4}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode criado = JSON.readTree(r.getResponse().getContentAsString());
        var login = login(criado.get("emailInstitucional").asText(), criado.get("senhaProvisoria").asText());
        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.token");
        CHAMADAS.clear();
        return "Bearer " + token;
    }

    @Test
    @DisplayName("cadastrar aluno manda o aluno inteiro, com o id do aluno e o token de quem cadastrou")
    void cadastroDeAluno() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));

        var r = mvc.perform(post("/api/alunos").header(HttpHeaders.AUTHORIZATION, diretor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluna Ranking Teste\",\"turmaId\":4}"))
                .andExpect(status().isCreated()).andReturn();
        long alunoId = JSON.readTree(r.getResponse().getContentAsString()).get("id").asLong();

        Chamada c = unicaChamada();
        assertThat(c.metodo()).isEqualTo("PUT");
        assertThat(c.caminho()).isEqualTo("/api/sync/alunos/" + alunoId);
        assertThat(c.autorizacao()).isEqualTo(diretor);
        JsonNode corpo = corpoDe(c);
        assertThat(corpo.get("nome").asText()).isEqualTo("Aluna Ranking Teste");
        assertThat(corpo.get("usuarioId").asLong()).isPositive();
        assertThat(corpo.get("exibirNoRanking").asBoolean()).isTrue();
        assertThat(corpo.get("perfilPublico").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("o aluno que muda o próprio nome avisa só o perfil (PATCH), com o próprio token")
    void mudarNome() throws Exception {
        String token = tokenDeUmAluno();

        mvc.perform(patch("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Nome Novo Ranking\"}"))
                .andExpect(status().isOk());

        Chamada c = unicaChamada();
        assertThat(c.metodo()).isEqualTo("PATCH");
        assertThat(c.caminho()).endsWith("/perfil");
        assertThat(c.autorizacao()).isEqualTo(token);
        assertThat(corpoDe(c).get("nome").asText()).isEqualTo("Nome Novo Ranking");
    }

    @Test
    @DisplayName("o aluno que muda a privacidade do ranking avisa as duas escolhas, com o próprio token")
    void privacidade() throws Exception {
        String token = tokenDeUmAluno();

        mvc.perform(patch("/api/configuracoes/privacidade").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exibirNoRanking\":false}"))
                .andExpect(status().isOk());

        Chamada c = unicaChamada();
        assertThat(c.metodo()).isEqualTo("PATCH");
        assertThat(c.caminho()).endsWith("/privacidade");
        assertThat(c.autorizacao()).isEqualTo(token);
        assertThat(corpoDe(c).get("exibirNoRanking").asBoolean()).isFalse();
        assertThat(corpoDe(c).get("perfilPublico").asBoolean()).isTrue();

        CHAMADAS.clear();
        mvc.perform(patch("/api/configuracoes/privacidade").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exibirNoRanking\":true}"))
                .andExpect(status().isOk());
        assertThat(corpoDe(unicaChamada()).get("exibirNoRanking").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("privacidade de professor não gera aviso à gamificação")
    void professorNaoAvisa() throws Exception {
        String token = bearer(criarConta(Role.PROFESSOR));
        CHAMADAS.clear();

        mvc.perform(patch("/api/configuracoes/privacidade").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfilVisivelAlunos\":false}"))
                .andExpect(status().isOk());

        assertThat(CHAMADAS).isEmpty();
    }

    @Test
    @DisplayName("se a gamificação estiver fora do ar, a escolha do aluno continua gravada")
    void falhaNaoDesfaz() throws Exception {
        statusDaResposta = 500;
        String token = tokenDeUmAluno();

        mvc.perform(patch("/api/configuracoes/privacidade").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfilPublico\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacidade.perfilPublico").value(false));

        statusDaResposta = 204;
        mvc.perform(patch("/api/configuracoes/privacidade").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfilPublico\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ressincronizar reenvia todos os alunos, só para a gestão; falha do serviço é 503")
    void ressincronizar() throws Exception {
        mvc.perform(post("/api/admin/contas/sincronizar-jogadores")
                        .header(HttpHeaders.AUTHORIZATION, tokenDeUmAluno()))
                .andExpect(status().isForbidden());
        assertThat(CHAMADAS).isEmpty();

        mvc.perform(post("/api/admin/contas/sincronizar-jogadores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jogadores").isNumber());
        assertThat(CHAMADAS).isNotEmpty();
        assertThat(CHAMADAS).allMatch(c -> c.metodo().equals("PUT") && c.caminho().startsWith("/api/sync/alunos/"));

        statusDaResposta = 500;
        mvc.perform(post("/api/admin/contas/sincronizar-jogadores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ADMIN))))
                .andExpect(status().isServiceUnavailable());
    }
}
