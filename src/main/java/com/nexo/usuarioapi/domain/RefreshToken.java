package com.nexo.usuarioapi.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens",
       // revogarTodosDoUsuario/revogarOutrasSessoes: um UPDATE por usuário a cada
       // logout e a cada conta desativada.
       indexes = @Index(name = "idx_refresh_tokens_usuario", columnList = "usuario_id"))
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Hash SHA-256 do refresh token (nunca o valor em claro). Se o banco vazar,
     * o valor aqui não dá acesso — só o hash comparável ao que o cliente envia.
     * Mapeado na coluna física "token" para não exigir migração de schema.
     */
    @Column(name = "token", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Usuario usuario;

    @Column(nullable = false)
    private Instant expiraEm;

    private boolean revogado = false;
}
