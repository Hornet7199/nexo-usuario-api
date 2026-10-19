package com.nexo.usuarioapi.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;

/**
 * Foto de perfil enviada pelo usuário, guardada no banco.
 *
 * <p>Fica em tabela própria (e não numa coluna de Usuario) por dois motivos: os bytes
 * não entram em toda resposta que devolve o usuário, e o id é um UUID aleatório — a
 * imagem é servida publicamente em /api/fotos/{id}, já que &lt;img src&gt; não envia o
 * header Authorization, e um id aleatório evita varrer as fotos por id sequencial.
 */
@Entity
@Table(name = "fotos_perfil")
@Getter
public class FotoPerfil {

    /** UUID aleatório: também serve de cache-busting, pois muda a cada novo envio. */
    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private Long usuarioId;

    @Column(nullable = false)
    private String tipoConteudo;

    /**
     * Tipo declarado como bytea: sem isso o Hibernate escolhe BLOB pelo tamanho, e o H2
     * em MODE=PostgreSQL (usado em desenvolvimento) recusa esse tipo. bytea é entendido
     * tanto pelo H2 nesse modo quanto pelo Postgres de produção. Evita também o @Lob,
     * que no Postgres viraria large object (oid), bem mais chato de manter.
     */
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] dados;

    @Column(nullable = false)
    private Instant atualizadaEm;

    protected FotoPerfil() {}

    public FotoPerfil(String id, Long usuarioId, String tipoConteudo, byte[] dados, Instant atualizadaEm) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipoConteudo = tipoConteudo;
        this.dados = dados;
        this.atualizadaEm = atualizadaEm;
    }
}
