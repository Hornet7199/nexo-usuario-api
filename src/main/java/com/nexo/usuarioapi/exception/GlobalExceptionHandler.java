package com.nexo.usuarioapi.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte exceções no envelope de erro padrão consumido pelo interceptor do Angular:
 * { "timestamp": "...", "status": 400, "error": "VALIDATION_ERROR", "message": "...", "fields": { ... } }
 *
 * <p>Os handlers de erro do cliente existem para devolver o status HTTP correto. Sem
 * eles tudo caía no {@link #handleGeneric}: um enum inválido na query string, um JSON
 * malformado, um verbo errado ou um upload acima do limite respondiam <b>500</b> —
 * dizendo ao cliente que a falha era do servidor, além de encher o log de ERROR com
 * stack trace de requisição malfeita.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErroPadrao(Instant timestamp, int status, String error, String message, Map<String, String> fields) {}

    private static ResponseEntity<ErroPadrao> erro(HttpStatus status, String codigo, String mensagem,
                                                   Map<String, String> fields) {
        return ResponseEntity.status(status)
                .body(new ErroPadrao(Instant.now(), status.value(), codigo, mensagem, fields));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErroPadrao> handleApi(ApiException ex) {
        var resposta = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfterSegundos() != null) {
            resposta = resposta.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSegundos()));
        }
        return resposta.body(new ErroPadrao(Instant.now(), ex.getStatus().value(), ex.getError(),
                ex.getMessage(), ex.getFields()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroPadrao> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErroPadrao(Instant.now(), 400, "VALIDATION_ERROR", "Dados inválidos.", fields));
    }

    /** Corpo ausente, JSON malformado ou incompatível com o record de destino. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroPadrao> handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        return erro(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Corpo da requisição ausente ou mal formado.", null);
    }

    /** Ex.: ?status=INEXISTENTE num parâmetro tipado como enum, ou ?turma=abc num Long. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroPadrao> handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return erro(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parâmetro com valor inválido.",
                Map.of(ex.getName(), "Valor não aceito para este parâmetro."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroPadrao> handleParametroAusente(MissingServletRequestParameterException ex) {
        return erro(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parâmetro obrigatório ausente.",
                Map.of(ex.getParameterName(), "Este parâmetro é obrigatório."));
    }

    /** Ex.: POST /api/usuarios/me/foto sem a parte "arquivo" no multipart. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErroPadrao> handleParteAusente(MissingServletRequestPartException ex) {
        return erro(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Arquivo obrigatório ausente.",
                Map.of(ex.getRequestPartName(), "Esta parte do formulário é obrigatória."));
    }

    /**
     * O limite do multipart corta antes de chegar ao controller, então a checagem de
     * 2 MB de lá nem chega a rodar — sem este handler, o usuário recebia 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroPadrao> handleUploadGrande(MaxUploadSizeExceededException ex) {
        return erro(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "A imagem deve ter no máximo 2 MB.", null);
    }

    /** O 405 obriga o cabeçalho Allow com os métodos aceitos (RFC 9110, 15.5.6). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroPadrao> handleMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        var resposta = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        var suportados = ex.getSupportedHttpMethods();
        if (suportados != null && !suportados.isEmpty()) {
            resposta = resposta.header(HttpHeaders.ALLOW,
                    suportados.stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        return resposta.body(new ErroPadrao(Instant.now(), 405, "METHOD_NOT_ALLOWED",
                "Método HTTP não suportado por este recurso.", null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroPadrao> handleMediaTypeNaoSuportado(HttpMediaTypeNotSupportedException ex) {
        return erro(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Formato de conteúdo não suportado por este recurso.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErroPadrao> handleMediaTypeNaoAceitavel(HttpMediaTypeNotAcceptableException ex) {
        return erro(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "Nenhum formato de resposta compatível com o cabeçalho Accept.", null);
    }

    /** Rota de API inexistente — o fallback de SPA só vale fora de /api e /ws (ver WebConfig). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroPadrao> handleRotaInexistente(NoResourceFoundException ex) {
        return erro(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado.", null);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErroPadrao> handleForbidden(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErroPadrao(Instant.now(), 403, "FORBIDDEN", "Acesso negado para o seu perfil.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroPadrao> handleGeneric(Exception ex) {
        log.error("Erro interno não tratado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroPadrao(Instant.now(), 500, "INTERNAL_ERROR", "Erro interno inesperado.", null));
    }
}
