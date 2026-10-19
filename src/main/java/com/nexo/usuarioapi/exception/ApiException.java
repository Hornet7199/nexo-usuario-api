package com.nexo.usuarioapi.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Exceção de negócio mapeada para o envelope de erro padrão. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final Map<String, String> fields;

    /** Segundos até a próxima tentativa ser aceita; vira o cabeçalho Retry-After. */
    private final Long retryAfterSegundos;

    public ApiException(HttpStatus status, String error, String message) {
        this(status, error, message, null);
    }

    public ApiException(HttpStatus status, String error, String message, Map<String, String> fields) {
        this(status, error, message, fields, null);
    }

    public ApiException(HttpStatus status, String error, String message, Map<String, String> fields,
                        Long retryAfterSegundos) {
        super(message);
        this.status = status;
        this.error = error;
        this.fields = fields;
        this.retryAfterSegundos = retryAfterSegundos;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public static ApiException validation(String message, Map<String, String> fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, fields);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    /**
     * 503 para falha em serviço de terceiro (hoje a consulta de CEP). Diferente de 500:
     * diz ao cliente que o pedido estava correto e que vale tentar de novo — nada foi
     * feito de errado aqui dentro.
     */
    public static ApiException servicoIndisponivel(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    /**
     * 429 acompanhado de Retry-After. Sem esse cabeçalho o cliente só sabe que foi
     * barrado, não quando pode voltar — e acaba repetindo em laço (RFC 9110, 15.5.30).
     */
    public static ApiException tooManyRequests(String error, String message, long retryAfterSegundos) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, error, message, null, retryAfterSegundos);
    }

    public HttpStatus getStatus() { return status; }
    public String getError() { return error; }
    public Map<String, String> getFields() { return fields; }
    public Long getRetryAfterSegundos() { return retryAfterSegundos; }
}
