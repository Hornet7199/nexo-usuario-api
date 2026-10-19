package com.nexo.usuarioapi.dto;

import com.nexo.usuarioapi.domain.Aluno;

/**
 * Aluno na listagem: o mínimo para escolhê-lo. Sem o e-mail institucional, que é o login
 * — uma coleção o entregaria da turma inteira de uma vez.
 */
public record AlunoResumoDto(Long id, String nome, Long turmaId, String turma, String foto) {

    public static AlunoResumoDto of(Aluno a, String nomeTurma) {
        return new AlunoResumoDto(a.getId(), a.getNome(), a.getTurmaId(), nomeTurma, a.getFoto());
    }
}
