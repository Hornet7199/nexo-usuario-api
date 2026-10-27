package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.exception.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/** O que os clientes dos outros serviços (pedagogico-api, comunicacao-api) têm em comum. */
final class ClienteHttp {

    private ClienteHttp() {}

    /**
     * Cliente do JDK, e não o HttpURLConnection do SimpleClientHttpRequestFactory: este não
     * suporta o método PATCH. HTTP/1.1 fixo porque os serviços falam texto puro, sem TLS, e a
     * tentativa de h2c só atrapalha.
     */
    static RestClient criar(String url, int timeoutMs) {
        var cliente = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        var fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
    }

    /**
     * O {@code Authorization} da requisição em curso. Os serviços compartilham o segredo do
     * JWT, então o token emitido aqui vale lá — e a autorização continua sendo a da pessoa.
     */
    static String tokenDaRequisicao() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String header = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && !header.isBlank()) return header;
        }
        throw ApiException.unauthorized("Sessão ausente para falar com outro serviço.");
    }
}
