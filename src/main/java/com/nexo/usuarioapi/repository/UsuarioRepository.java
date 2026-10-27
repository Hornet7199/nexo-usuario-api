package com.nexo.usuarioapi.repository;

import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    /** Contas para a tela de administração: filtro opcional por papel e por nome/login. */
    @Query("""
           select u from Usuario u
           where (:role is null or u.role = :role)
             and (:busca is null or lower(u.nome) like :busca or lower(u.login) like :busca)
           order by u.nome
           """)
    Page<Usuario> buscar(@Param("role") Role role, @Param("busca") String busca, Pageable pageable);

    /**
     * (papel, ativo, total) para a escola inteira numa consulta só: o painel precisa de seis
     * números sobre a tabela de usuários, e um group by responde o que seis count(*) fariam.
     */
    @Query("select u.role, u.ativo, count(u) from Usuario u group by u.role, u.ativo")
    java.util.List<Object[]> contarPorPapelEAtivo();
}
