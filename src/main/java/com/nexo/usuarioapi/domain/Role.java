package com.nexo.usuarioapi.domain;

/**
 * Papéis do sistema, os mesmos do monólito: o JWT emitido aqui precisa carregar um
 * {@code role} que as demais APIs e o front Angular reconheçam.
 */
public enum Role {
    ALUNO, PROFESSOR, DIRETOR, ADMIN
}
