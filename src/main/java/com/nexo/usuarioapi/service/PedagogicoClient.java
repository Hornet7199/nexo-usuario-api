package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Único ponto em que este serviço fala com o pedagogico-api (hoje, o monólito): turmas e
 * matérias são dele, aqui só existem como id.
 *
 * <p>A chamada repassa o {@code Authorization} da requisição em curso. Os dois serviços
 * compartilham o segredo do JWT, então o token emitido aqui vale lá — e o recorte por
 * papel continua sendo o de lá (um professor só vê as turmas que leciona).
 *
 * <p>Com {@code nexo.pedagogico.enabled=false} o catálogo fica desligado: cadastros
 * aceitam qualquer id e os nomes vêm vazios. É o modo dos testes e do serviço isolado.
 */
@Component
public class PedagogicoClient {

    private static final Logger log = LoggerFactory.getLogger(PedagogicoClient.class);

    private record Item(Long id, String nome) {}

    private final boolean habilitado;
    private final RestClient http;

    public PedagogicoClient(@Value("${nexo.pedagogico.enabled:true}") boolean habilitado,
                            @Value("${nexo.pedagogico.url:http://localhost:8080}") String url,
                            @Value("${nexo.pedagogico.timeout-ms:3000}") int timeoutMs) {
        this.habilitado = habilitado;
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(timeoutMs));
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
    }

    public boolean habilitado() {
        return habilitado;
    }

    /** Turmas que o chamador enxerga (id → nome). Lança 503 se o pedagogico-api não responder. */
    public Map<Long, String> turmas() {
        return buscar("/api/turmas");
    }

    /** Matérias do catálogo (id → nome). Lança 503 se o pedagogico-api não responder. */
    public Map<Long, String> materias() {
        return buscar("/api/materias");
    }

    private Map<Long, String> buscar(String caminho) {
        if (!habilitado) return Map.of();
        try {
            List<Item> itens = http.get().uri(caminho)
                    .header(HttpHeaders.AUTHORIZATION, tokenDaRequisicao())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Item>>() {});
            Map<Long, String> porId = new LinkedHashMap<>();
            if (itens != null) itens.forEach(i -> porId.put(i.id(), i.nome()));
            return porId;
        } catch (RestClientException e) {
            log.warn("pedagogico-api indisponível em {}: {}", caminho, e.getMessage());
            throw ApiException.servicoIndisponivel(
                    "Não foi possível consultar o catálogo pedagógico. Tente novamente em instantes.");
        }
    }

    private static String tokenDaRequisicao() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String header = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && !header.isBlank()) return header;
        }
        throw ApiException.unauthorized("Sessão ausente para consultar o catálogo pedagógico.");
    }
}
