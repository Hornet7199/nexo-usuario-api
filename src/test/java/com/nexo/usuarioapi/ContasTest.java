package com.nexo.usuarioapi;

import com.jayway.jsonpath.JsonPath;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContasTest extends TesteApiBase {

    private MvcResult alterarAtivo(String autorizacao, Long id, String corpo) throws Exception {
        return mvc.perform(patch("/api/admin/contas/" + id + "/ativo").header(HttpHeaders.AUTHORIZATION, autorizacao)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    @Test
    void adminListaContasPaginadasComFiltroPorPapelEBusca() throws Exception {
        String admin = bearer(criarConta(Role.ADMIN));
        Usuario alvo = criarConta(Role.PROFESSOR);

        mvc.perform(get("/api/admin/contas").param("papel", "PROFESSOR").param("busca", alvo.getLogin())
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].login").value(alvo.getLogin()))
                .andExpect(jsonPath("$.content[0].papel").value("PROFESSOR"))
                .andExpect(jsonPath("$.content[0].senhaHash").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void papelInvalidoNoFiltroDevolve400() throws Exception {
        mvc.perform(get("/api/admin/contas").param("papel", "SECRETARIA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alunoEProfessorNaoAcessamAGestaoDeContas() throws Exception {
        for (Role role : new Role[]{Role.ALUNO, Role.PROFESSOR}) {
            mvc.perform(get("/api/admin/contas").header(HttpHeaders.AUTHORIZATION, bearer(criarConta(role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void desativarContaImpedeLoginDerrubaSessaoEReativarDevolveOAcesso() throws Exception {
        String admin = bearer(criarConta(Role.ADMIN));
        Usuario alvo = criarConta(Role.ALUNO);
        Cookie refresh = cookieDeRefresh(login(alvo.getLogin(), SENHA));

        status().isOk().match(alterarAtivo(admin, alvo.getId(), "{\"ativo\":false}"));

        status().isUnauthorized().match(login(alvo.getLogin(), SENHA));
        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());

        status().isOk().match(alterarAtivo(admin, alvo.getId(), "{\"ativo\":true}"));
        status().isOk().match(login(alvo.getLogin(), SENHA));
    }

    @Test
    void desativarDuasVezesEIdempotente() throws Exception {
        String admin = bearer(criarConta(Role.ADMIN));
        Usuario alvo = criarConta(Role.ALUNO);

        status().isOk().match(alterarAtivo(admin, alvo.getId(), "{\"ativo\":false}"));
        status().isOk().match(alterarAtivo(admin, alvo.getId(), "{\"ativo\":false}"));
    }

    @Test
    void adminNaoDesativaAPropriaConta() throws Exception {
        Usuario admin = criarConta(Role.ADMIN);

        MvcResult r = alterarAtivo(bearer(admin), admin.getId(), "{\"ativo\":false}");

        status().isBadRequest().match(r);
        jsonPath("$.message").value("Você não pode desativar a própria conta.").match(r);
    }

    @Test
    void ativoAusenteOuContaInexistente() throws Exception {
        String admin = bearer(criarConta(Role.ADMIN));
        Usuario alvo = criarConta(Role.ALUNO);

        MvcResult semCampo = alterarAtivo(admin, alvo.getId(), "{}");
        status().isBadRequest().match(semCampo);
        jsonPath("$.fields.ativo").value("Informe se a conta fica ativa.").match(semCampo);

        status().isNotFound().match(alterarAtivo(admin, 999999L, "{\"ativo\":false}"));
    }

    @Test
    void redefinirSenhaGeraProvisoriaEDerrubaAsSessoesAbertas() throws Exception {
        String admin = bearer(criarConta(Role.ADMIN));
        Usuario alvo = criarConta(Role.PROFESSOR);
        Cookie refresh = cookieDeRefresh(login(alvo.getLogin(), SENHA));

        MvcResult r = mvc.perform(post("/api/admin/contas/" + alvo.getId() + "/senha-provisoria")
                .header(HttpHeaders.AUTHORIZATION, admin)).andReturn();

        status().isOk().match(r);
        jsonPath("$.login").value(alvo.getLogin()).match(r);
        String provisoria = JsonPath.read(r.getResponse().getContentAsString(), "$.senhaProvisoria");

        status().isUnauthorized().match(login(alvo.getLogin(), SENHA));
        status().isOk().match(login(alvo.getLogin(), provisoria));
        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }
}
