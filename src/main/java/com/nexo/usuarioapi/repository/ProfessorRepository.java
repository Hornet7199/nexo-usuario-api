package com.nexo.usuarioapi.repository;

import com.nexo.usuarioapi.domain.Professor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {

    Optional<Professor> findByUsuarioId(Long usuarioId);

    boolean existsByEmail(String email);
}
