package com.nexo.usuarioapi;

import com.jayway.jsonpath.JsonPath;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import com.nexo.usuarioapi.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe a aplicação com H2 em memória (perfil "teste") e dá aos testes contas prontas.
 * Sem transação no teste: cada requisição commita e usa o próprio contexto de
 * persistência, como em produção (a revogação de sessões é um UPDATE em lote que não
 * atualiza entidades já carregadas). Os logins são únicos porque o limite de
 * tentativas por login e o banco em memória atravessam os testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("teste")
public abstract class TesteApiBase {

    protected static final String SENHA = "Senha@Forte9";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected PasswordEncoder encoder;

    protected Usuario criarConta(Role role) {
        int n = SEQUENCIA.incrementAndGet();
        Usuario u = new Usuario();
        u.setLogin("conta" + n + "@teste.com");
        u.setSenhaHash(encoder.encode(SENHA));
        u.setNome("Conta Teste " + n);
        u.setCargo("Cargo " + role);
        u.setRole(role);
        return usuarios.save(u);
    }

    protected MvcResult login(String login, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}")).andReturn();
    }

    /** Faz login com a senha padrão dos testes e devolve o access token. */
    protected String tokenDe(Usuario u) throws Exception {
        MvcResult r = login(u.getLogin(), SENHA);
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("login falhou: " + r.getResponse().getStatus());
        }
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    protected String bearer(Usuario u) throws Exception {
        return "Bearer " + tokenDe(u);
    }

    protected static Cookie cookieDeRefresh(MvcResult r) {
        return r.getResponse().getCookie("nexo_refresh");
    }

    protected void exigirStatus(MvcResult r, int esperado) throws Exception {
        status().is(esperado).match(r);
    }
}
