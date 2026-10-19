package com.nexo.usuarioapi.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Docente: identidade e cadastro. As métricas de produtividade (correções pendentes,
 * avaliação, tarefas) e as turmas atendidas pertencem ao pedagogico-api.
 */
@Entity
@Table(name = "professores")
@Getter
@Setter
public class Professor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    @Column(nullable = false)
    private String nome;

    private LocalDate dataNascimento;

    private String sexo;

    @Column(unique = true)
    private String email;

    private String foto;

    /** Ids das matérias lecionadas — o catálogo vive no pedagogico-api. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "professor_materia_ids", joinColumns = @JoinColumn(name = "professor_id"))
    @Column(name = "materia_id")
    private Set<Long> materiaIds = new LinkedHashSet<>();

    /**
     * Nomes das matérias ("História, Geografia") como estavam no cadastro. Guardado
     * para exibição: sem ele cada leitura de professor dependeria do pedagogico-api.
     */
    private String disciplinas;
}
