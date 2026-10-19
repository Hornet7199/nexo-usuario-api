package com.nexo.usuarioapi.repository;

import com.nexo.usuarioapi.domain.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    boolean existsByEmailInstitucional(String email);

    Optional<Aluno> findByUsuarioId(Long usuarioId);

    List<Aluno> findAllByOrderByNome();

    List<Aluno> findByTurmaIdInOrderByNome(Collection<Long> turmaIds);
}
