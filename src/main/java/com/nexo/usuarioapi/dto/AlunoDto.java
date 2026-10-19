package com.nexo.usuarioapi.dto;

import com.nexo.usuarioapi.domain.Aluno;

/** O aluno em detalhe (GET /api/alunos/{id}). {@code turma} é o nome, se o catálogo respondeu. */
public record AlunoDto(Long id, String nome, String emailInstitucional, Long turmaId, String turma, String foto) {

    public static AlunoDto of(Aluno a, String nomeTurma) {
        return new AlunoDto(a.getId(), a.getNome(), a.getEmailInstitucional(), a.getTurmaId(), nomeTurma, a.getFoto());
    }
}
