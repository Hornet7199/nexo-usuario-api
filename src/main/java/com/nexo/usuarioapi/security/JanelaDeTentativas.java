package com.nexo.usuarioapi.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de falhas por chave dentro de uma janela de tempo — a peça reaproveitada
 * pelos vários limites de tentativa da autenticação (por login, por origem, por
 * renovação de sessão), que antes existiam só para o login e em código embutido no
 * {@code AuthService}.
 *
 * <p>Uma chave entra em bloqueio quando acumula {@code maximo} falhas dentro da janela,
 * e sai dele quando a janela da <b>primeira</b> falha vence — janela fixa, não
 * deslizante. É mais simples de explicar ao usuário ("tente de novo em X minutos") e o
 * atacante não ganha nada com isso: continuar errando não estende o bloqueio, mas
 * também não o encurta.
 *
 * <p><b>Limite conhecido</b>: o estado é um mapa em memória. Ele se perde no reinício e
 * cada instância tem o seu, então com N réplicas o teto efetivo é N × {@code maximo}.
 * Para uma instância só — o caso aqui — vale o número configurado. Distribuir isso
 * exigiria um contador compartilhado (Redis), com uma ida à rede em cada tentativa.
 */
public final class JanelaDeTentativas {

    /** A partir daqui uma nova falha varre as janelas já vencidas. */
    private static final int LIMITE_PODA = 1_000;

    private final int maximo;
    private final Duration janela;

    /**
     * A chave vem de fora (login digitado, endereço de origem), então o mapa cresce com
     * o que o atacante mandar — daí a poda em {@link #registrarFalha}.
     */
    private final Map<String, Registro> porChave = new ConcurrentHashMap<>();

    private record Registro(int quantidade, Instant primeiraFalha) {}

    public JanelaDeTentativas(int maximo, Duration janela) {
        this.maximo = maximo;
        this.janela = janela;
    }

    /** {@code maximo <= 0} desliga o limite — é como a configuração o desativa. */
    public boolean desligada() {
        return maximo <= 0;
    }

    /**
     * Segundos que faltam para a chave voltar a ser aceita, ou {@code 0} se ela não está
     * bloqueada. O valor vira o cabeçalho {@code Retry-After} da resposta 429.
     */
    public long segundosDeBloqueio(String chave) {
        if (desligada() || chave == null) return 0;

        Registro registro = porChave.get(chave);
        if (registro == null) return 0;

        Instant fimDaJanela = registro.primeiraFalha().plus(janela);
        if (!fimDaJanela.isAfter(Instant.now())) {
            // Janela vencida: a contagem recomeça do zero na próxima falha.
            porChave.remove(chave);
            return 0;
        }
        if (registro.quantidade() < maximo) return 0;

        // Nunca devolve 0 aqui: 0 significaria "liberado" para quem chama.
        return Math.max(1, Duration.between(Instant.now(), fimDaJanela).toSeconds());
    }

    public void registrarFalha(String chave) {
        if (desligada() || chave == null) return;

        if (porChave.size() >= LIMITE_PODA) {
            podarVencidas();
        }
        porChave.merge(chave,
                new Registro(1, Instant.now()),
                (antiga, ignorada) -> new Registro(antiga.quantidade() + 1, antiga.primeiraFalha()));
    }

    /** Zera a contagem da chave — usado quando a tentativa finalmente dá certo. */
    public void limpar(String chave) {
        if (chave != null) porChave.remove(chave);
    }

    /** Janelas encerradas não bloqueiam mais ninguém: só ocupam heap. */
    private void podarVencidas() {
        Instant limite = Instant.now().minus(janela);
        porChave.values().removeIf(registro -> registro.primeiraFalha().isBefore(limite));
    }
}
