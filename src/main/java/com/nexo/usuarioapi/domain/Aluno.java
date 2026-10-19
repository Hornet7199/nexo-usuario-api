package com.nexo.usuarioapi.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Aluno como este serviço o conhece: um nome, um acesso e o id da turma.
 *
 * <p>Não há dado pessoal (nascimento, sexo, endereço): a ficha do aluno vive no sistema
 * de aula da escola, e uma base invadida não deve entregar nada além do nome. Também
 * não há gamificação, engajamento nem métricas — isso é domínio pedagógico, que
 * referencia o aluno por {@code id}. A turma é só um id: quem a conhece é o
 * pedagogico-api.
 */
@Entity
@Table(name = "alunos", indexes = @Index(name = "idx_alunos_turma", columnList = "turma_id"))
@Getter
@Setter
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    @Column(nullable = false)
    private String nome;

    @Column(unique = true)
    private String emailInstitucional;

    @Column(name = "turma_id")
    private Long turmaId;

    /** Cópia de {@link Usuario#getFoto()}, mantida em sincronia pelo PerfilService. */
    private String foto;
}
