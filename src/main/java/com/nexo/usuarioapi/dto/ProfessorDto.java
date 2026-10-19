package com.nexo.usuarioapi.dto;

import com.nexo.usuarioapi.domain.Professor;

import java.time.LocalDate;

public record ProfessorDto(Long id, String nome, String email, String sexo, LocalDate dataNascimento,
                           String disciplinas, String foto) {

    public static ProfessorDto of(Professor p) {
        return new ProfessorDto(p.getId(), p.getNome(), p.getEmail(), p.getSexo(), p.getDataNascimento(),
                p.getDisciplinas(), p.getFoto());
    }
}
