package com.nexo.usuarioapi;

import com.jayway.jsonpath.JsonPath;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PerfilTest extends TesteApiBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private MvcResult trocarSenha(String autorizacao, Cookie refresh, String atual, String nova) throws Exception {
        var req = post("/api/usuarios/me/senha").header(HttpHeaders.AUTHORIZATION, autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + atual + "\",\"novaSenha\":\"" + nova + "\"}");
        if (refresh != null) req.cookie(refresh);
        return mvc.perform(req).andReturn();
    }

    @Test
    void atualizaONomeDoPerfil() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        mvc.perform(patch("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(u))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"  Novo Nome  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"));
    }

    @Test
    void trocaDeSenhaGravaApenasOHashEEncerraOutrasSessoesPoupandoAAtual() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        MvcResult sessaoAtual = login(u.getLogin(), SENHA);
        String token = "Bearer " + JsonPath.read(sessaoAtual.getResponse().getContentAsString(), "$.token");
        Cookie atual = cookieDeRefresh(sessaoAtual);
        Cookie outra = cookieDeRefresh(login(u.getLogin(), SENHA));

        status().isNoContent().match(trocarSenha(token, atual, SENHA, "Nova#Senha2026"));

        status().isUnauthorized().match(login(u.getLogin(), SENHA));
        status().isOk().match(login(u.getLogin(), "Nova#Senha2026"));
        mvc.perform(post("/api/auth/refresh").cookie(outra)).andExpect(status().isUnauthorized());
    }

    @Test
    void senhaAtualErradaDevolveCodigoECampoProprios() throws Exception {
        MvcResult r = trocarSenha(bearer(criarConta(Role.ALUNO)), null, "errada", "Nova#Senha2026");

        status().isBadRequest().match(r);
        jsonPath("$.error").value("SENHA_ATUAL_INCORRETA").match(r);
        jsonPath("$.fields.senhaAtual").exists().match(r);
    }

    @Test
    void politicaDeSenhaRecusaSequenciaESenhaIgualAAtual() throws Exception {
        String token = bearer(criarConta(Role.ALUNO));

        MvcResult sequencia = trocarSenha(token, null, SENHA, "12345678");
        status().isBadRequest().match(sequencia);
        jsonPath("$.error").value("SENHA_SEQUENCIAL").match(sequencia);
        jsonPath("$.fields.novaSenha").exists().match(sequencia);

        jsonPath("$.error").value("SENHA_IGUAL_A_ATUAL").match(trocarSenha(token, null, SENHA, SENHA));
    }

    @Test
    void validarSenhaResponde200ComOVereditoSemGravarNada() throws Exception {
        Usuario u = criarConta(Role.ALUNO);
        String token = bearer(u);

        mvc.perform(post("/api/usuarios/me/senha/validar").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"novaSenha\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.codigo").value("SENHA_CURTA"));
        mvc.perform(post("/api/usuarios/me/senha/validar").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"novaSenha\":\"Outra#Senha77\"}"))
                .andExpect(jsonPath("$.valida").value(true));

        status().isOk().match(login(u.getLogin(), SENHA));
    }

    @Test
    void fotoEnviadaFicaDisponivelPublicamenteESubstituiAAnterior() throws Exception {
        String token = bearer(criarConta(Role.ALUNO));

        MvcResult r = mvc.perform(multipart("/api/usuarios/me/foto")
                .file(new MockMultipartFile("arquivo", "perfil.jpg", "image/jpeg", PNG))
                .header(HttpHeaders.AUTHORIZATION, token)).andReturn();
        status().isOk().match(r);
        String caminho = JsonPath.read(r.getResponse().getContentAsString(), "$.foto");

        // <img src> não manda Authorization: a rota é pública, e o tipo vem do conteúdo.
        mvc.perform(get(caminho))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mvc.perform(delete("/api/usuarios/me/foto").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").doesNotExist());
        mvc.perform(get(caminho)).andExpect(status().isNotFound());
    }

    @Test
    void arquivoQueNaoEImagemERecusadoMesmoRotuladoComoPng() throws Exception {
        mvc.perform(multipart("/api/usuarios/me/foto")
                        .file(new MockMultipartFile("arquivo", "x.png", "image/png", "<script>alert(1)</script>".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(criarConta(Role.ALUNO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formato inválido. Envie uma imagem JPEG, PNG ou WebP."));
    }

    @Test
    void fotoDeUsuarioInexistenteDevolve404() throws Exception {
        mvc.perform(get("/api/fotos/naoexiste")).andExpect(status().isNotFound());
    }
}
