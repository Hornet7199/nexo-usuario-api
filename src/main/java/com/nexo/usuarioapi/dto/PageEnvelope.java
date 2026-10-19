package com.nexo.usuarioapi.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope padrão de listagem paginada:
 * { "content": [...], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
 */
public record PageEnvelope<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageEnvelope<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageEnvelope<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <T> PageEnvelope<T> of(List<T> content) {
        return new PageEnvelope<>(content, 0, content.size(), content.size(), 1);
    }
}
