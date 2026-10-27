package com.nexo.usuarioapi;

import com.nexo.usuarioapi.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Configurações por usuário, por seção — vieram do monólito junto com o resto da conta. */
class ConfiguracoesTest extends TesteApiBase {

    @Test
    void usuarioNovoRecebeAsConfiguracoesPadraoDoSeuPapel() throws Exception {
        String token = bearer(criarConta(Role.ALUNO));

        mvc.perform(get("/api/configuracoes").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacidade").exists());
    }

    @Test
    void patchDeSecaoFazMergeEPersiste() throws Exception {
        String token = bearer(criarConta(Role.ALUNO));

        mvc.perform(patch("/api/configuracoes/privacidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exibirNoRanking\":false}")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacidade.exibirNoRanking").value(false));

        mvc.perform(get("/api/configuracoes").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.privacidade.exibirNoRanking").value(false));
    }

    @Test
    void chaveDePrivacidadeSoAceitaBooleanoDeVerdade() throws Exception {
        mvc.perform(patch("/api/configuracoes/privacidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exibirNoRanking\":\"false\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ALUNO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.exibirNoRanking").exists());
    }

    @Test
    void chaveDePrivacidadeDoProfessorTambemSoAceitaBooleano() throws Exception {
        String professor = bearer(criarConta(Role.PROFESSOR));

        mvc.perform(patch("/api/configuracoes/privacidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfilVisivelAlunos\":\"false\"}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/configuracoes/privacidade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfilVisivelAlunos\":false}")
                        .header(HttpHeaders.AUTHORIZATION, professor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacidade.perfilVisivelAlunos").value(false));
    }

    @Test
    void chavesSemNadaParaControlarSairam() throws Exception {
        mvc.perform(get("/api/configuracoes").header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.PROFESSOR))))
                .andExpect(jsonPath("$.privacidade.exibirContatoResponsaveis").doesNotExist());
        mvc.perform(get("/api/configuracoes").header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.DIRETOR))))
                .andExpect(jsonPath("$.privacidade").doesNotExist());
    }

    @Test
    void secaoDesconhecidaDevolve400() throws Exception {
        mvc.perform(patch("/api/configuracoes/inexistente").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":1}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ALUNO))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semTokenDevolve401() throws Exception {
        mvc.perform(get("/api/configuracoes")).andExpect(status().isUnauthorized());
    }
}
