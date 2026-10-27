package com.nexo.usuarioapi.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Configurações do usuário armazenadas como documento JSON particionado por seção,
 * espelhando o formato do signal<Settings> do Angular. O PATCH parcial por seção
 * é resolvido no serviço (merge servidor-vence).
 */
@Entity
@Table(name = "configuracoes_usuario")
@Getter
@Setter
public class ConfiguracaoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private Usuario usuario;

    @Lob
    @Column(nullable = false)
    private String json;

    private Instant atualizadaEm = Instant.now();
}
