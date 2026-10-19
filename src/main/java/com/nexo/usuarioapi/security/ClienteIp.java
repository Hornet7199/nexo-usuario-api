package com.nexo.usuarioapi.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Endereço de quem realmente fez a requisição.
 *
 * <p>{@code request.getRemoteAddr()} devolve o peer da conexão TCP. Atrás de um proxy —
 * o caso em produção, onde o Render termina o TLS — esse peer é o próprio proxy, então
 * toda a trilha de auditoria registrava o mesmo endereço para todo mundo e qualquer
 * limite por origem trataria a internet inteira como um único cliente.
 *
 * <p>{@code server.forward-headers-strategy: framework} não resolve isto: o
 * {@code ForwardedHeaderFilter} corrige esquema, host e porta (é o que faz o
 * {@code isSecure()} funcionar, e portanto o cookie {@code Secure}), mas não mexe no
 * endereço remoto.
 *
 * <p><b>Por que o cabeçalho não é confiável por si só:</b> {@code X-Forwarded-For} é uma
 * lista em que cada proxy <i>acrescenta</i> o endereço de quem falou com ele. Um cliente
 * malicioso pode mandar a requisição já com o cabeçalho preenchido, e o proxy da frente
 * apenas anexa o endereço real depois do valor forjado:
 *
 * <pre>
 *   X-Forwarded-For: 1.2.3.4, &lt;ip real do cliente&gt;
 *                    ↑ inventado pelo cliente
 * </pre>
 *
 * <p>Por isso a leitura é <b>da direita para a esquerda</b>: com {@code saltosConfiaveis}
 * proxies entre o cliente e a aplicação, o endereço confiável é o que está a essa
 * distância do fim da lista — tudo à esquerda dele é texto que o cliente escolheu. Com
 * {@code saltosConfiaveis = 0} (padrão, e o caso do ambiente local) o cabeçalho é
 * ignorado por completo e vale o peer da conexão.
 */
@Component
public class ClienteIp {

    private static final String CABECALHO = "X-Forwarded-For";

    private final int saltosConfiaveis;

    public ClienteIp(@Value("${nexo.proxy.saltos-confiaveis:0}") int saltosConfiaveis) {
        this.saltosConfiaveis = Math.max(0, saltosConfiaveis);
    }

    public String de(HttpServletRequest requisicao) {
        if (saltosConfiaveis == 0) {
            return requisicao.getRemoteAddr();
        }
        String cabecalho = requisicao.getHeader(CABECALHO);
        if (cabecalho == null || cabecalho.isBlank()) {
            // Requisição que não passou pelo proxy esperado (health check interno, por
            // exemplo). O peer direto é o melhor dado disponível.
            return requisicao.getRemoteAddr();
        }

        String[] enderecos = cabecalho.split(",");
        // Um salto confiável ⇒ último item da lista; dois ⇒ penúltimo, e assim por diante.
        int indice = enderecos.length - saltosConfiaveis;
        if (indice < 0) {
            // A lista veio mais curta do que o esperado: algum proxy do caminho não
            // acrescentou nada. Fica com o item mais à esquerda, que é o mais próximo
            // do cliente entre os que existem.
            indice = 0;
        }

        String endereco = enderecos[indice].trim();
        return endereco.isEmpty() ? requisicao.getRemoteAddr() : endereco;
    }
}
