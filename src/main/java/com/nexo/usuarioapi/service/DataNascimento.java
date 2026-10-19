package com.nexo.usuarioapi.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Validação de data de nascimento nos cadastros.
 *
 * <p>Data futura já era barrada, mas só isso deixava passar coisa igualmente
 * impossível: aluno nascido ontem, professor de 9 anos, gente com 300 anos. Uma
 * faixa de idade plausível por papel resolve os três — e a mensagem diz o motivo,
 * porque "data inválida" não ajuda quem digitou 2205 em vez de 2005.
 *
 * <p>Os limites são deliberadamente largos: cabe repetência, EJA e professor perto
 * da aposentadoria. O que eles barram é o absurdo, não o incomum.
 */
final class DataNascimento {

    private DataNascimento() {}

    /**
     * Analisa e valida, registrando o problema em {@code erros} sob a chave
     * {@code dataNascimento}. Devolve null quando não dá para usar a data.
     */
    static LocalDate validar(String iso, int idadeMinima, int idadeMaxima, Map<String, String> erros) {
        if (iso == null || iso.isBlank()) {
            erros.put("dataNascimento", "Informe a data de nascimento.");
            return null;
        }

        LocalDate nascimento;
        try {
            nascimento = LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            erros.put("dataNascimento", "Data de nascimento inválida.");
            return null;
        }

        LocalDate hoje = LocalDate.now();
        if (nascimento.isAfter(hoje)) {
            erros.put("dataNascimento", "A data de nascimento não pode ser futura.");
            return null;
        }

        int idade = Period.between(nascimento, hoje).getYears();
        if (idade < idadeMinima) {
            erros.put("dataNascimento",
                    "Idade fora do esperado: o cadastro exige ao menos " + idadeMinima + " anos.");
            return null;
        }
        if (idade > idadeMaxima) {
            erros.put("dataNascimento",
                    "Idade fora do esperado: confira o ano digitado (" + idade + " anos).");
            return null;
        }
        return nascimento;
    }
}
