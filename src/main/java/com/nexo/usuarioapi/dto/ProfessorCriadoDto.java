package com.nexo.usuarioapi.dto;

/** A senha provisória em claro existe só nesta resposta. */
public record ProfessorCriadoDto(Long id, String nome, String disciplinas, String emailInstitucional,
                                 String senhaProvisoria) {}
