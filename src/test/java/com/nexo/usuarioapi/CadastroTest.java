package com.nexo.usuarioapi;

import com.jayway.jsonpath.JsonPath;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CadastroTest extends TesteApiBase {

    private static final String PROFESSOR_VALIDO = """
            {"nome":"Marina Souza Lima","dataNascimento":"1985-03-10","sexo":"F","materiaIds":[1,2]}""";

    private MvcResult cadastrarAluno(String autorizacao, String nome) throws Exception {
        return mvc.perform(post("/api/alunos").header(HttpHeaders.AUTHORIZATION, autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"turmaId\":4}")).andReturn();
    }

    @Test
    void diretorCadastraAlunoComAcessoGeradoNoServidor() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));

        MvcResult r = cadastrarAluno(diretor, "Joana Prado Lima");

        status().isCreated().match(r);
        header().exists(HttpHeaders.LOCATION).match(r);
        jsonPath("$.emailInstitucional").value("joana.lima@nexo.escola.com").match(r);
        String senha = JsonPath.read(r.getResponse().getContentAsString(), "$.senhaProvisoria");
        assertThat(senha).startsWith("Nexo@");

        // A credencial gerada de fato abre uma sessão de ALUNO.
        MvcResult acesso = login("joana.lima@nexo.escola.com", senha);
        status().isOk().match(acesso);
        jsonPath("$.usuario.role").value("ALUNO").match(acesso);
        // Senha sorteada pelo sistema: o cliente deve prender a sessão na troca de senha.
        jsonPath("$.usuario.precisaTrocarSenha").value(true).match(acesso);
    }

    @Test
    void nomesIguaisGeramEmailsDiferentes() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));

        MvcResult a = cadastrarAluno(diretor, "Pedro Alves Reis");
        MvcResult b = cadastrarAluno(diretor, "Pedro Alves Reis");

        String emailA = JsonPath.read(a.getResponse().getContentAsString(), "$.emailInstitucional");
        String emailB = JsonPath.read(b.getResponse().getContentAsString(), "$.emailInstitucional");
        assertThat(emailA).isNotEqualTo(emailB);
    }

    @Test
    void alunoNaoPodeCadastrarAluno() throws Exception {
        status().isForbidden().match(cadastrarAluno(bearer(criarConta(Role.ALUNO)), "Intruso Teste"));
    }

    @Test
    void cadastroSemTokenDevolve401() throws Exception {
        status().isUnauthorized().match(cadastrarAluno("", "Sem Token"));
    }

    @Test
    void cadastroInvalidoDevolveErrosPorCampo() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));

        mvc.perform(post("/api/alunos").header(HttpHeaders.AUTHORIZATION, diretor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Al\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.nome").exists())
                .andExpect(jsonPath("$.fields.turmaId").value("Selecione a turma."));
    }

    @Test
    void cadastroNaoAceitaDadoPessoalDoAluno() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));

        // sexo e dataNascimento saíram do cadastro de aluno (LGPD): são ignorados, não gravados.
        mvc.perform(post("/api/alunos").header(HttpHeaders.AUTHORIZATION, diretor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Lia Santos Cruz\",\"turmaId\":4,\"sexo\":\"F\",\"dataNascimento\":\"2010-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sexo").doesNotExist())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist());
    }

    @Test
    void listagemDeAlunosSoParaPerfisDeGestaoEOcultaOLogin() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));
        cadastrarAluno(diretor, "Rita Gomes Neri");

        mvc.perform(get("/api/alunos").header(HttpHeaders.AUTHORIZATION, diretor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nome=='Rita Gomes Neri')]").exists())
                .andExpect(jsonPath("$[0].emailInstitucional").doesNotExist());
        mvc.perform(get("/api/alunos").header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ALUNO))))
                .andExpect(status().isForbidden());
    }

    @Test
    void alunoInexistenteDevolve404ComEnvelopePadrao() throws Exception {
        mvc.perform(get("/api/alunos/999999").header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.DIRETOR))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void diretorCadastraProfessorEGeraAcesso() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));

        MvcResult r = mvc.perform(post("/api/professores").header(HttpHeaders.AUTHORIZATION, diretor)
                .contentType(MediaType.APPLICATION_JSON).content(PROFESSOR_VALIDO)).andReturn();

        status().isCreated().match(r);
        header().exists(HttpHeaders.LOCATION).match(r);
        jsonPath("$.emailInstitucional").value("marina.lima@nexo.escola.com").match(r);
        String senha = JsonPath.read(r.getResponse().getContentAsString(), "$.senhaProvisoria");
        jsonPath("$.usuario").doesNotExist().match(r);

        MvcResult acesso = login("marina.lima@nexo.escola.com", senha);
        jsonPath("$.usuario.role").value("PROFESSOR").match(acesso);
        jsonPath("$.usuario.cargo").value("Professora").match(acesso);
    }

    @Test
    void professorMenorDeIdadeOuComMateriasDemaisDevolve400() throws Exception {
        String diretor = bearer(criarConta(Role.DIRETOR));
        int anoAtual = java.time.Year.now().getValue();

        mvc.perform(post("/api/professores").header(HttpHeaders.AUTHORIZATION, diretor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Jovem Demais\",\"dataNascimento\":\"" + (anoAtual - 10)
                                + "-01-01\",\"sexo\":\"M\",\"materiaIds\":[1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.dataNascimento").exists());

        mvc.perform(post("/api/professores").header(HttpHeaders.AUTHORIZATION, diretor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Tudo Leciona\",\"dataNascimento\":\"1980-01-01\",\"sexo\":\"M\",\"materiaIds\":[1,2,3,4]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.materiaIds").exists());
    }

    @Test
    void soODiretorCadastraProfessor() throws Exception {
        for (Role role : new Role[]{Role.PROFESSOR, Role.ALUNO, Role.ADMIN}) {
            Usuario u = criarConta(role);
            mvc.perform(post("/api/professores").header(HttpHeaders.AUTHORIZATION, bearer(u))
                            .contentType(MediaType.APPLICATION_JSON).content(PROFESSOR_VALIDO))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void jsonMalformadoDevolve400EmVezDe500() throws Exception {
        mvc.perform(post("/api/alunos").header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.DIRETOR)))
                        .contentType(MediaType.APPLICATION_JSON).content("{nome:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
