package com.nexo.usuarioapi.repository;

import com.nexo.usuarioapi.domain.FotoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FotoPerfilRepository extends JpaRepository<FotoPerfil, String> {
    Optional<FotoPerfil> findByUsuarioId(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);

    /**
     * Só o necessário para responder a imagem, fora do contexto de persistência.
     * Carregar a entidade fazia o Hibernate guardar um snapshot de dirty-checking com
     * uma <b>cópia</b> do array de bytes — cada download de foto ocupava o dobro do
     * tamanho da imagem no heap até o fim da requisição.
     */
    interface Conteudo {
        String getTipoConteudo();
        byte[] getDados();
    }

    Optional<Conteudo> findConteudoById(String id);
}
