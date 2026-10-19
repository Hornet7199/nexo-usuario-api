package com.nexo.usuarioapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Trilha de auditoria como log estruturado (logger {@code auditoria}), sem tabela
 * própria. Quem precisar de trilha centralizada coleta esse logger; o contrato
 * ({@link #registrar}) é o mesmo que o monólito usava.
 */
@Service
public class AuditoriaService {

    public enum Tipo { LOGIN, LOGOUT, ALTERACAO, ERRO }

    private static final Logger TRILHA = LoggerFactory.getLogger("auditoria");

    public void registrar(String ator, Tipo tipo, String acao, String detalhe, String ip) {
        TRILHA.info("tipo={} ator=\"{}\" acao=\"{}\" detalhe=\"{}\" ip={}",
                tipo, limpar(ator), limpar(acao), limpar(detalhe), limpar(ip));
    }

    /**
     * O ator de um login inválido é o texto que o cliente digitou: sem tirar quebras de
     * linha e aspas, ele forjaria linhas inteiras na trilha.
     */
    private static String limpar(String valor) {
        if (valor == null) return "-";
        return valor.replaceAll("[\\p{Cntrl}\"]", " ").trim();
    }
}
