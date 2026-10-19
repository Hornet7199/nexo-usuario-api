package com.nexo.usuarioapi.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    private Instant criadoEm = Instant.now();
}
