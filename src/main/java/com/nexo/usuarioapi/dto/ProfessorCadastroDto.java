package com.nexo.usuarioapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cadastro de professor. A data chega como texto ISO porque a faixa de idade aceita e as
 * mensagens ficam em {@code DataNascimento}, a mesma validação do monólito.
 */
public record ProfessorCadastroDto(
        @NotBlank(message = "Informe o nome completo.")
        @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres.")
        String nome,

        @NotBlank(message = "Informe a data de nascimento.")
        String dataNascimento,

        @NotBlank(message = "Selecione o sexo.")
        @Size(max = 20, message = "Sexo inválido.")
        String sexo,

        @NotEmpty(message = "Selecione ao menos uma matéria.")
        List<@NotNull(message = "Matéria inválida.") Long> materiaIds
) {}
