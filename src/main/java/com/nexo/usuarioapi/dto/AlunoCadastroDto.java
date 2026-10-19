package com.nexo.usuarioapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro de aluno: só nome e turma. O acesso (login e senha) é gerado no servidor. */
public record AlunoCadastroDto(
        @NotBlank(message = "Informe o nome completo.")
        @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres.")
        String nome,

        @NotNull(message = "Selecione a turma.")
        Long turmaId
) {}
