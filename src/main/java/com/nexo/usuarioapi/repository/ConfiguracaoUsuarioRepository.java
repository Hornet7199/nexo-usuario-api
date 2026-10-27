package com.nexo.usuarioapi.repository;

import com.nexo.usuarioapi.domain.ConfiguracaoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracaoUsuarioRepository extends JpaRepository<ConfiguracaoUsuario, Long> {
    Optional<ConfiguracaoUsuario> findByUsuarioId(Long usuarioId);
}
