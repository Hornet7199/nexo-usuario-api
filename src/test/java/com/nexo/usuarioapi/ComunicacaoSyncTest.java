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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que o usuario-api avisa ao comunicacao-api, contra um servidor de mentira que registra as
 * chamadas: cadastro, edição de perfil e foto, privacidade do professor e a ressincronização
 * completa — e que a falha do chat não desfaz o que o usuário fez.
 */
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:teste-sync-comunicacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ComunicacaoSyncTest extends TesteApiBase {

    private record Chamada(String metodo, String caminho, String autorizacao, String corpo) {}

    private static final List<Chamada> CHAMADAS = new CopyOnWriteArrayList<>();
    private static volatile int statusDaResposta = 204;
    private static final HttpServer SERVIDOR = subir();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

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
        registro.add("nexo.comunicacao.enabled", () -> "true");
        registro.add("nexo.comunicacao.url", () -> "http://127.0.0.1:" + SERVIDOR.getAddress().getPort());
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

    private Chamada unicaChamada() throws Exception {
        // o aviso sai depois do commit, na mesma thread do request: já chegou quando o mvc retorna
        assertThat(CHAMADAS).hasSize(1);
        return CHAMADAS.get(0);
    }

    private JsonNode corpoDe(Chamada c) throws Exception {
        return JSON.readTree(c.corpo());
    }

    @Test
    @DisplayName("cadastrar aluno avisa o chat, com o token de quem cadastrou e o papel ALUNO")
    void cadastroDeAluno() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));

        var r = mvc.perform(post("/api/alunos").header(HttpHeaders.AUTHORIZATION, diretor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Aluna Chat Teste\",\"turmaId\":4}"))
                .andExpect(status().isCreated()).andReturn();

        Chamada c = unicaChamada();
        assertThat(c.metodo()).isEqualTo("PUT");
        assertThat(c.caminho()).startsWith("/api/sync/contatos/");
        assertThat(c.autorizacao()).isEqualTo(diretor);
        JsonNode corpo = corpoDe(c);
        assertThat(corpo.get("nome").asText()).isEqualTo("Aluna Chat Teste");
        assertThat(corpo.get("papel").asText()).isEqualTo("ALUNO");
        assertThat(corpo.get("perfilVisivelParaAlunos").asBoolean()).isTrue();
        assertThat(json(r)).isNotNull();
    }

    private JsonNode json(org.springframework.test.web.servlet.MvcResult r) throws Exception {
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("cadastrar professor avisa o chat com o papel PROFESSOR")
    void cadastroDeProfessor() throws Exception {
        mvc.perform(post("/api/professores").header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.DIRETOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Profa. Chat Teste\",\"dataNascimento\":\"1985-03-10\","
                                + "\"sexo\":\"F\",\"materiaIds\":[1]}"))
                .andExpect(status().isCreated());

        assertThat(corpoDe(unicaChamada()).get("papel").asText()).isEqualTo("PROFESSOR");
    }

    @Test
    @DisplayName("mudar o próprio nome avisa o chat com o nome novo")
    void mudarNome() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        String token = bearer(u);
        CHAMADAS.clear();

        mvc.perform(patch("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Nome Novo Chat\"}"))
                .andExpect(status().isOk());

        Chamada c = unicaChamada();
        assertThat(c.caminho()).isEqualTo("/api/sync/contatos/" + u.getId());
        assertThat(corpoDe(c).get("nome").asText()).isEqualTo("Nome Novo Chat");
        assertThat(c.autorizacao()).as("com o token do próprio dono").isEqualTo(token);
    }

    @Test
    @DisplayName("enviar e remover a foto avisam o chat, com e sem o caminho da foto")
    void fotoDoPerfil() throws Exception {
        String token = bearer(criarConta(Role.PROFESSOR));
        CHAMADAS.clear();

        mvc.perform(multipart("/api/usuarios/me/foto")
                        .file(new MockMultipartFile("arquivo", "p.jpg", "image/jpeg", PNG))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        assertThat(corpoDe(unicaChamada()).get("foto").asText()).startsWith("/api/fotos/");

        CHAMADAS.clear();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/usuarios/me/foto")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        assertThat(corpoDe(unicaChamada()).get("foto").isNull()).isTrue();
    }

    @Test
    @DisplayName("o professor que oculta o perfil dos alunos avisa o chat; o padrão é visível")
    void privacidadeDoProfessor() throws Exception {
        Usuario professor = criarConta(Role.PROFESSOR);
        String token = bearer(professor);
        CHAMADAS.clear();

        mvc.perform(patch("/api/configuracoes/privacidade").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfilVisivelAlunos\":false}"))
                .andExpect(status().isOk());

        JsonNode corpo = corpoDe(unicaChamada());
        assertThat(corpo.get("papel").asText()).isEqualTo("PROFESSOR");
        assertThat(corpo.get("perfilVisivelParaAlunos").asBoolean()).isFalse();

        CHAMADAS.clear();
        mvc.perform(patch("/api/configuracoes/privacidade").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfilVisivelAlunos\":true}"))
                .andExpect(status().isOk());
        assertThat(corpoDe(unicaChamada()).get("perfilVisivelParaAlunos").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("mudanças de configuração que o chat não usa não geram aviso")
    void configuracaoIrrelevanteNaoAvisa() throws Exception {
        String token = bearer(criarConta(Role.ALUNO));
        CHAMADAS.clear();

        mvc.perform(patch("/api/configuracoes/privacidade").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exibirNoRanking\":false}"))
                .andExpect(status().isOk());

        assertThat(CHAMADAS).isEmpty();
    }

    @Test
    @DisplayName("se o chat estiver fora do ar, o cadastro e a edição dão certo do mesmo jeito")
    void falhaDoChatNaoDesfazNada() throws Exception {
        statusDaResposta = 500;
        String diretor = bearer(criarConta(Role.DIRETOR));

        mvc.perform(post("/api/alunos").header(HttpHeaders.AUTHORIZATION, diretor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cadastro Com Chat Fora\",\"turmaId\":4}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, diretor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Diretor Renomeado\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ressincronização: só a gestão, e manda todas as contas num lote")
    void ressincronizacao() throws Exception {
        criarConta(Role.ALUNO);
        criarConta(Role.PROFESSOR);

        mvc.perform(post("/api/admin/contas/sincronizar-contatos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ALUNO))))
                .andExpect(status().isForbidden());
        CHAMADAS.clear();

        mvc.perform(post("/api/admin/contas/sincronizar-contatos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));

        Chamada c = unicaChamada();
        assertThat(c.metodo()).isEqualTo("PUT");
        assertThat(c.caminho()).isEqualTo("/api/sync/contatos");
        JsonNode lote = corpoDe(c);
        assertThat(lote.isArray()).isTrue();
        assertThat(lote.size()).isGreaterThanOrEqualTo(4);
        assertThat(lote.get(0).has("id")).isTrue();
        assertThat(lote.get(0).has("papel")).isTrue();
        assertThat(lote.toString()).as("nada de senha no que sai daqui").doesNotContain("senha").doesNotContain("Hash");
    }

    @Test
    @DisplayName("ressincronização com o chat fora do ar responde 503 (quem pediu precisa saber)")
    void ressincronizacaoFalha() throws Exception {
        statusDaResposta = 500;

        mvc.perform(post("/api/admin/contas/sincronizar-contatos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ADMIN))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }
}
