package com.nexo.usuarioapi.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/** Conta de acesso ao sistema: quem é, com que papel entra e se ainda pode entrar. */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String login;

    @Column(nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    private String cargo;

    private String foto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private boolean ativo = true;

    /**
     * Ligada quando a senha foi sorteada pelo sistema (cadastro ou redefinição pelo
     * administrador) e, portanto, já passou pela mão de outra pessoa. O cliente lê a flag no
     * login e manda direto para a troca de senha; ela só desliga quando o próprio usuário
     * define uma senha nova.
     *
     * <p>{@code @ColumnDefault}: com {@code ddl-auto: update}, a coluna nasce em tabela já
     * povoada — sem default, o {@code NOT NULL} falharia nas linhas existentes.
     */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean precisaTrocarSenha = false;

    private Instant criadoEm = Instant.now();
}
