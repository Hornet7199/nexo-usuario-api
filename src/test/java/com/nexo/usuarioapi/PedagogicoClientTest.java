package com.nexo.usuarioapi;

import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.service.PedagogicoClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O cliente do pedagogico-api, contra um servidor HTTP de mentira. Existe sobretudo por causa
 * do PATCH: o cliente padrão do JDK (HttpURLConnection) não o suporta, e a sincronização de
 * perfil falhava em silêncio, porque é "melhor esforço".
 */
class PedagogicoClientTest {

    private record Chamada(String metodo, String caminho, String autorizacao, String corpo) {}

    private HttpServer servidor;
    private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
    private int status = 204;
    private String respostaJson = "";

    @BeforeEach
    void subirServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            chamadas.add(new Chamada(troca.getRequestMethod(), troca.getRequestURI().getPath(),
                    troca.getRequestHeaders().getFirst("Authorization"), corpo));
            byte[] resposta = respostaJson.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, resposta.length == 0 ? -1 : resposta.length);
            if (resposta.length > 0) troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();

        // A chamada repassa o Authorization da requisição em curso.
        var requisicao = new MockHttpServletRequest();
        requisicao.addHeader("Authorization", "Bearer token-do-operador");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));
    }

    @AfterEach
    void pararServidor() {
        RequestContextHolder.resetRequestAttributes();
        servidor.stop(0);
    }

    private PedagogicoClient cliente() {
        return new PedagogicoClient(true, "http://127.0.0.1:" + servidor.getAddress().getPort(), 2000);
    }

    @Test
    void sincronizarPerfilUsaPatchEReencaminhaOToken() {
        cliente().sincronizarPerfil(7L, "Nome Novo", "/api/fotos/abc");

        assertThat(chamadas).hasSize(1);
        Chamada c = chamadas.get(0);
        assertThat(c.metodo()).isEqualTo("PATCH");
        assertThat(c.caminho()).isEqualTo("/api/sync/usuarios/7/perfil");
        assertThat(c.autorizacao()).isEqualTo("Bearer token-do-operador");
        assertThat(c.corpo()).contains("\"nome\":\"Nome Novo\"").contains("\"foto\":\"/api/fotos/abc\"");
    }

    @Test
    void sincronizarAlunoUsaPutComOIdDoUsuarioApi() {
        cliente().sincronizarAluno(12L, 99L, "Aluno Teste", 4L, null);

        Chamada c = chamadas.get(0);
        assertThat(c.metodo()).isEqualTo("PUT");
        assertThat(c.caminho()).isEqualTo("/api/sync/alunos/12");
        assertThat(c.corpo()).contains("\"usuarioId\":99").contains("\"turmaId\":4");
    }

    @Test
    void sincronizarProfessorEnviaAsMaterias() {
        cliente().sincronizarProfessor(3L, 50L, "Profa. Teste", null, List.of(2L, 9L));

        Chamada c = chamadas.get(0);
        assertThat(c.caminho()).isEqualTo("/api/sync/professores/3");
        assertThat(c.corpo()).contains("\"materiaIds\":[2,9]");
    }

    @Test
    void falhaNoCadastroEEstritaEViraServicoIndisponivel() {
        status = 500;

        assertThatThrownBy(() -> cliente().sincronizarAluno(1L, 2L, "Aluno", 4L, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("serviço pedagógico");
    }

    @Test
    void falhaNaSincronizacaoDePerfilNaoDerrubaAAcaoDoUsuario() {
        status = 500;

        assertThatCode(() -> cliente().sincronizarPerfil(7L, "Nome", null)).doesNotThrowAnyException();
    }

    @Test
    void turmasVoltamComoMapaIdParaNome() {
        status = 200;
        respostaJson = "[{\"id\":1,\"nome\":\"9º Ano A\",\"anoLetivo\":2026},{\"id\":4,\"nome\":\"2º Ano EM A\"}]";

        var turmas = cliente().turmas();

        assertThat(turmas).containsEntry(1L, "9º Ano A").containsEntry(4L, "2º Ano EM A");
        assertThat(chamadas.get(0).metodo()).isEqualTo("GET");
        assertThat(chamadas.get(0).caminho()).isEqualTo("/api/turmas");
    }

    @Test
    void desligadoNaoFazNenhumaChamada() {
        var desligado = new PedagogicoClient(false, "http://127.0.0.1:" + servidor.getAddress().getPort(), 2000);

        desligado.sincronizarAluno(1L, 2L, "Aluno", 4L, null);
        assertThat(desligado.turmas()).isEmpty();

        assertThat(chamadas).isEmpty();
    }
}
