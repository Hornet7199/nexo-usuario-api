package com.nexo.usuarioapi;

import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutenticacaoTest extends TesteApiBase {

    @Test
    void loginValidoDevolveTokenECookieHttpOnlyDeRefresh() throws Exception {
        Usuario u = criarConta(Role.PROFESSOR);

        MvcResult r = login(u.getLogin(), SENHA);

        status().isOk().match(r);
        jsonPath("$.token").isNotEmpty().match(r);
        jsonPath("$.usuario.role").value("PROFESSOR").match(r);
        // O refresh token não vai no corpo: só no cookie.
        jsonPath("$.refreshToken").doesNotExist().match(r);
        assertThat(r.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("nexo_refresh=").contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/auth");
    }

    @Test
    void loginIgnoraMaiusculasNoLogin() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        status().isOk().match(login(u.getLogin().toUpperCase(), SENHA));
    }

    @Test
    void senhaErradaOuContaInexistenteDaMesmaRespostaGenerica() throws Exception {
        Usuario u = criarConta(Role.ALUNO);

        MvcResult errada = login(u.getLogin(), "outra-senha");
        MvcResult inexistente = login("ninguem@teste.com", SENHA);

        status().isUnauthorized().match(errada);
        status().isUnauthorized().match(inexistente);
        // Mesma mensagem nos dois casos: o erro não revela quais logins existem.
        jsonPath("$.message").value("Credenciais inválidas.").match(errada);
        jsonPath("$.message").value("Credenciais inválidas.").match(inexistente);
    }

    @Test
    void contaInativaNaoEntraComAMesmaRespostaGenerica() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        u.setAtivo(false);
        usuarios.save(u);

        MvcResult r = login(u.getLogin(), SENHA);

        status().isUnauthorized().match(r);
        jsonPath("$.message").value("Credenciais inválidas.").match(r);
    }

    @Test
    void corpoSemSenhaDevolve400ComCampo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alguem@teste.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.senha").exists());
    }

    @Test
    void rotaProtegidaSemTokenDevolve401() throws Exception {
        mvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInventadoNaoAutentica() throws Exception {
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer nexo-token-usuario-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meDevolveOUsuarioDoToken() throws Exception {
        Usuario u = criarConta(Role.DIRETOR);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(u.getId()))
                .andExpect(jsonPath("$.role").value("DIRETOR"));
    }

    @Test
    void refreshRotacionaOCookieEReuseDoAntigoDerrubaTodasAsSessoes() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        Cookie primeiro = cookieDeRefresh(login(u.getLogin(), SENHA));

        MvcResult renovado = mvc.perform(post("/api/auth/refresh").cookie(primeiro))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        Cookie segundo = cookieDeRefresh(renovado);
        assertThat(segundo.getValue()).isNotEqualTo(primeiro.getValue());

        // Reapresentar o token já rotacionado é tratado como vazamento…
        mvc.perform(post("/api/auth/refresh").cookie(primeiro)).andExpect(status().isUnauthorized());
        // …e o token novo, que era legítimo, também cai.
        mvc.perform(post("/api/auth/refresh").cookie(segundo)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSemCookieDevolve401() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidaOAccessTokenNoMesmoInstanteERevogaORefresh() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        MvcResult sessao = login(u.getLogin(), SENHA);
        String token = com.jayway.jsonpath.JsonPath.read(sessao.getResponse().getContentAsString(), "$.token");
        Cookie refresh = cookieDeRefresh(sessao);

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).cookie(refresh))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void cincoErrosNaMesmaContaBloqueiamComRetryAfter() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        for (int i = 0; i < 5; i++) {
            status().isUnauthorized().match(login(u.getLogin(), "errada" + i));
        }

        MvcResult bloqueado = login(u.getLogin(), SENHA);

        status().isTooManyRequests().match(bloqueado);
        assertThat(bloqueado.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
        jsonPath("$.error").value("LOGIN_BLOQUEADO").match(bloqueado);
    }
}
