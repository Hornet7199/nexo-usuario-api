package com.nexo.usuarioapi.dto;

/** A senha provisória em claro existe só nesta resposta. */
public record AlunoCriadoDto(Long id, String nome, String emailInstitucional, String senhaProvisoria) {}
