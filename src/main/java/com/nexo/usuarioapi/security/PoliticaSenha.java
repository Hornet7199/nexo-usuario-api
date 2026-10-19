package com.nexo.usuarioapi.security;

import com.nexo.usuarioapi.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;
import java.util.Set;

/**
 * Regras de qualidade da senha, aplicadas <b>no servidor</b>.
 *
 * <p>O formulário do Angular já exige 8 caracteres, mas validação de cliente é conforto
 * de quem digita, não controle de acesso: um POST direto na API ignora a tela inteira.
 * O {@code @Size(min = 8)} do DTO cobre o comprimento; o que falta — e é o que esta
 * classe faz — é recusar as senhas que <i>têm</i> 8 caracteres e ainda assim são as
 * primeiras que qualquer ataque de dicionário tenta.
 *
 * <p>Cada recusa sai com <b>código próprio</b> no envelope de erro, e não apenas com uma
 * mensagem: é o que permite à tela apontar o campo certo e reagir a um caso específico
 * sem comparar texto. Todas apontam o campo {@code novaSenha} — nenhuma regra daqui fala
 * da senha atual, e confundir as duas coisas era o que fazia a tela acusar o campo errado.
 */
@Component
public class PoliticaSenha {

    public static final int MINIMO = 8;

    /**
     * Teto de bytes (UTF-8) da senha nova. O BCrypt trunca a entrada em 72 bytes e ignora
     * o resto em silêncio, então o teto <i>técnico</i> é 72; o de política é 50 porque
     * ninguém digita 72 bytes de verdade, e um teto menor deixa folga para o BCrypt e corta
     * o custo de hashear entrada gigante. O login continua aceitando até 72 para não
     * trancar quem definiu uma senha maior quando o limite era esse.
     */
    public static final int MAXIMO_BYTES = 50;

    /**
     * Lista curta de propósito. Não substitui uma base de senhas vazadas — cobre o que
     * de fato aparece num sistema escolar quando alguém precisa "só trocar rápido".
     */
    private static final Set<String> COMUNS = Set.of(
            "12345678", "123456789", "1234567890", "87654321",
            "senha123", "senha1234", "minhasenha", "password", "password1", "passw0rd",
            "qwerty123", "asdf1234", "abcd1234", "abc12345",
            "escola123", "aluno123", "aluno1234", "professor", "professor1", "professor123",
            "diretor123", "nexo1234", "nexo12345", "mudar123", "trocar123", "primeiroacesso");

    /** Por que uma senha foi recusada: o código que a tela usa e o texto para o usuário. */
    public record Recusa(String codigo, String motivo) {}

    /** Lança {@link ApiException} 400 se a senha for recusada; não faz nada se for válida. */
    public void validar(String senha, String login, String nome) {
        Recusa recusa = avaliar(senha, login, nome);
        if (recusa != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, recusa.codigo(), recusa.motivo(),
                    Map.of("novaSenha", recusa.motivo()));
        }
    }

    /**
     * Mesmas regras de {@link #validar}, mas devolvendo a recusa em vez de lançá-la
     * ({@code null} = senha válida). Serve à checagem prévia da tela, que precisa do
     * veredito sem gravar nada.
     */
    public Recusa avaliar(String senha, String login, String nome) {
        if (senha == null || senha.isBlank()) {
            return recusa("SENHA_AUSENTE", "Informe a nova senha.");
        }
        if (senha.length() < MINIMO) {
            return recusa("SENHA_CURTA", "A nova senha precisa ter pelo menos " + MINIMO + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            return recusa("SENHA_LONGA", "A nova senha é longa demais: o limite é " + MAXIMO_BYTES + " bytes. "
                    + "Acentos e emojis contam como mais de um caractere.");
        }

        String simplificada = simplificar(senha);

        // As regras de forma vêm antes da lista de dicionário de propósito: "12345678"
        // cai nas duas, e dizer que é uma sequência já diz o que fazer a respeito, o que
        // "está na lista das mais tentadas" não diz.
        if (umCaractereSo(senha)) {
            return recusa("SENHA_REPETITIVA", "A nova senha repete o mesmo caractere do começo ao fim. "
                    + "Misture letras, números e símbolos.");
        }
        if (sequenciaDeDigitos(senha)) {
            return recusa("SENHA_SEQUENCIAL", "A nova senha é uma sequência numérica direta, como 12345678. "
                    + "Escolha algo sem ordem previsível.");
        }
        if (COMUNS.contains(simplificada)) {
            return recusa("SENHA_COMUM", "Essa senha está entre as primeiras que um ataque automatizado tenta. "
                    + "Escolha uma que não seja palavra de dicionário.");
        }
        if (pareceComDadoDoUsuario(simplificada, login)) {
            return recusa("SENHA_COM_LOGIN", "A nova senha não pode conter o seu login. Ele aparece na lista de "
                    + "turma e no e-mail institucional, então quem o conhece já teria "
                    + "metade da senha.");
        }
        if (pareceComDadoDoUsuario(simplificada, primeiroNome(nome))) {
            return recusa("SENHA_COM_NOME", "A nova senha não pode conter o seu nome — é o primeiro palpite de "
                    + "quem tenta adivinhar.");
        }
        return null;
    }

    private static Recusa recusa(String codigo, String motivo) {
        return new Recusa(codigo, motivo);
    }

    /** Minúsculas e sem acento: "Ana" e "ána" não devem escapar da comparação. */
    private static String simplificar(String valor) {
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase();
    }

    private static boolean umCaractereSo(String senha) {
        return senha.chars().distinct().count() == 1;
    }

    /** Pega 12345678 e 87654321, mas não 1a2b3c4d — que já tem variedade suficiente. */
    private static boolean sequenciaDeDigitos(String senha) {
        if (!senha.chars().allMatch(Character::isDigit)) return false;
        boolean crescente = true;
        boolean decrescente = true;
        for (int i = 1; i < senha.length(); i++) {
            int passo = senha.charAt(i) - senha.charAt(i - 1);
            if (passo != 1) crescente = false;
            if (passo != -1) decrescente = false;
        }
        return crescente || decrescente;
    }

    /**
     * O login institucional é público (aparece em lista de turma, e-mail, chamada), então
     * senha derivada dele não protege nada. Trechos com menos de 4 caracteres são
     * ignorados para não recusar senha boa por causa de um "ana" que calhou no meio.
     */
    private static boolean pareceComDadoDoUsuario(String senhaSimplificada, String dado) {
        if (dado == null) return false;
        String alvo = simplificar(dado);
        // Do login institucional interessa a parte antes do @ — o domínio é igual para todos.
        int arroba = alvo.indexOf('@');
        if (arroba > 0) alvo = alvo.substring(0, arroba);
        if (alvo.length() < 4) return false;
        return senhaSimplificada.contains(alvo);
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return null;
        return nome.trim().split("\\s+")[0];
    }
}
