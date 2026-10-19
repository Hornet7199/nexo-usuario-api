package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.dto.AuthDtos.SessaoEmitida;
import com.nexo.usuarioapi.dto.AuthDtos.TokenResponse;
import com.nexo.usuarioapi.dto.AuthDtos.UsuarioDTO;
import com.nexo.usuarioapi.domain.RefreshToken;
import com.nexo.usuarioapi.domain.Usuario;
import com.nexo.usuarioapi.repository.RefreshTokenRepository;
import com.nexo.usuarioapi.repository.UsuarioRepository;
import com.nexo.usuarioapi.security.AccessTokensRevogados;
import com.nexo.usuarioapi.security.JanelaDeTentativas;
import com.nexo.usuarioapi.security.JwtService;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_TENTATIVAS_POR_LOGIN = 5;
    private static final Duration JANELA_BLOQUEIO = Duration.ofMinutes(15);

    /** Janela mais curta para a renovação: ela é automática, não depende de digitação. */
    private static final Duration JANELA_REFRESH = Duration.ofMinutes(5);

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final AccessTokensRevogados accessTokensRevogados;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final Duration refreshTtl;

    /**
     * Hash descartável comparado quando o login não existe, para que a verificação de
     * senha custe o mesmo tempo nos dois casos (ver {@link #login}). É gerado de uma
     * senha aleatória no arranque: nenhuma senha real precisa casar com ele.
     */
    private final String hashFicticio;

    /** Protege uma conta específica de força bruta: 5 erros na mesma conta e ela trava. */
    private final JanelaDeTentativas porLogin;

    /**
     * Protege o conjunto das contas. O limite por login não enxerga <i>password
     * spraying</i> — uma senha provável testada contra centenas de logins diferentes —,
     * porque nesse ataque cada conta erra uma vez só e nenhuma chega perto do teto. Aqui
     * a contagem é por origem, que é o que esse ataque tem em comum.
     *
     * <p>O teto é bem mais alto que o do login por causa do NAT: um laboratório inteiro
     * sai pelo mesmo endereço público, e ali dezenas de erros honestos numa manhã são
     * normais. Ajuste por {@code nexo.auth.max-tentativas-por-ip} (0 desliga) se a escola
     * tiver muitas máquinas atrás de um IP só.
     */
    private final JanelaDeTentativas porOrigem;

    /** Renovações inválidas por origem — ver {@link #refresh(String, String)}. */
    private final JanelaDeTentativas refreshPorOrigem;

    public AuthService(UsuarioRepository usuarios,
                       RefreshTokenRepository refreshTokens,
                       JwtService jwtService,
                       AccessTokensRevogados accessTokensRevogados,
                       PasswordEncoder passwordEncoder,
                       AuditoriaService auditoria,
                       @Value("${nexo.jwt.refresh-token-days}") long refreshTokenDays,
                       @Value("${nexo.auth.max-tentativas-por-ip:100}") int maxTentativasPorIp,
                       @Value("${nexo.auth.max-refresh-invalidos-por-ip:30}") int maxRefreshInvalidosPorIp) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.accessTokensRevogados = accessTokensRevogados;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.refreshTtl = Duration.ofDays(refreshTokenDays);
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
        this.porLogin = new JanelaDeTentativas(MAX_TENTATIVAS_POR_LOGIN, JANELA_BLOQUEIO);
        this.porOrigem = new JanelaDeTentativas(maxTentativasPorIp, JANELA_BLOQUEIO);
        this.refreshPorOrigem = new JanelaDeTentativas(maxRefreshInvalidosPorIp, JANELA_REFRESH);
    }

    /**
     * {@code noRollbackFor}: o registro de auditoria da tentativa inválida é escrito e
     * logo em seguida a exceção sobe. Sem isso o Spring desfazia a transação junto com
     * a exceção e <b>nenhum login malsucedido chegava à trilha de auditoria</b> — a tela
     * do diretor mostrava só os acessos que deram certo.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public SessaoEmitida login(String login, String senha, String ip) {
        String chave = login.trim().toLowerCase();
        verificarBloqueio(porLogin, chave, "LOGIN_BLOQUEADO",
                "Muitas tentativas de login nesta conta. Tente novamente em alguns minutos.");
        verificarBloqueio(porOrigem, ip, "ORIGEM_BLOQUEADA",
                "Muitas tentativas malsucedidas vindas desta rede. Tente novamente em alguns minutos.");

        Usuario usuario = usuarios.findByLoginIgnoreCase(chave).orElse(null);

        // A verificação da senha roda sempre, inclusive para login inexistente ou inativo.
        // Antes o bcrypt era pulado nesses casos e a resposta voltava numa fração do tempo,
        // o que permitia descobrir quais logins existem só cronometrando as respostas.
        boolean senhaConfere = passwordEncoder.matches(
                senha, usuario != null ? usuario.getSenhaHash() : hashFicticio);

        if (usuario == null || !usuario.isAtivo() || !senhaConfere) {
            porLogin.registrarFalha(chave);
            porOrigem.registrarFalha(ip);
            auditoria.registrar(chave, AuditoriaService.Tipo.ERRO, "Tentativa de login inválida", null, ip);
            throw ApiException.unauthorized("Credenciais inválidas.");
        }

        // Só a contagem da conta é zerada. A da origem sobrevive de propósito: num
        // spraying o atacante costuma ter alguma credencial válida, e zerar tudo a cada
        // acerto daria a ele um jeito barato de reiniciar o contador quando quisesse.
        porLogin.limpar(chave);
        auditoria.registrar(usuario.getNome(), AuditoriaService.Tipo.LOGIN, "Login realizado", null, ip);
        return gerarTokens(usuario);
    }

    /** Renovação sem origem conhecida (usada em teste); o caminho HTTP usa a sobrecarga. */
    @Transactional(noRollbackFor = ApiException.class)
    public SessaoEmitida refresh(String refreshToken) {
        return refresh(refreshToken, null);
    }

    /**
     * Ver {@link #login} — a revogação em cascata abaixo também precisa sobreviver ao throw.
     *
     * <p>{@code /api/auth/refresh} é público por necessidade: quem chega nele ainda não
     * tem access token para provar quem é. Sem limite, era o único endpoint da API em que
     * dava para bater à vontade — cada tentativa custa uma consulta ao banco. Só a falha
     * conta: o cliente legítimo renova de tempos em tempos e sempre com um token válido.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public SessaoEmitida refresh(String refreshToken, String ip) {
        verificarBloqueio(refreshPorOrigem, ip, "REFRESH_BLOQUEADO",
                "Muitas renovações inválidas vindas desta rede. Entre novamente.");
        try {
            return rotacionar(refreshToken);
        } catch (ApiException erro) {
            refreshPorOrigem.registrarFalha(ip);
            throw erro;
        }
    }

    private SessaoEmitida rotacionar(String refreshToken) {
        RefreshToken atual = refreshTokens.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token inválido ou expirado."));

        // Reapresentação de um token já rotacionado: o legítimo dono já o trocou, então
        // quem está usando esta cópia a obteve de outro jeito. Como não dá para saber
        // qual das duas partes é a legítima, derruba a sessão inteira do usuário e força
        // um login novo. Antes o replay só levava 401 e o token roubado paralelo seguia
        // valendo pelos 7 dias restantes.
        if (atual.isRevogado()) {
            refreshTokens.revogarTodosDoUsuario(atual.getUsuario().getId());
            auditoria.registrar(atual.getUsuario().getNome(), AuditoriaService.Tipo.ERRO,
                    "Refresh token reutilizado — sessões revogadas", null, null);
            throw ApiException.unauthorized("Sessão encerrada por segurança. Entre novamente.");
        }
        if (!atual.getExpiraEm().isAfter(Instant.now())) {
            throw ApiException.unauthorized("Refresh token inválido ou expirado.");
        }
        // Um usuário desativado mantinha acesso renovando o token por até 7 dias:
        // o vínculo só era checado no login.
        if (!atual.getUsuario().isAtivo()) {
            throw ApiException.unauthorized("Usuário inativo.");
        }

        // Rotação: o token usado é invalidado e um novo é emitido
        atual.setRevogado(true);
        refreshTokens.save(atual);
        return gerarTokens(atual.getUsuario());
    }

    /**
     * Encerra <b>apenas</b> a sessão que apresentou o refresh token — as outras continuam
     * de pé. Antes o logout chamava {@code deleteByUsuario} e derrubava todos os
     * dispositivos do usuário de uma vez: sair no celular deslogava o desktop no meio do
     * trabalho.
     *
     * <p>A linha é revogada, não apagada, para preservar o rastro de reuso: se a cópia
     * vazada desta sessão for reapresentada depois do logout, {@link #refresh} a reconhece
     * como token já revogado e dispara a cascata, em vez de tratá-la como desconhecida.
     *
     * <p>Sem cookie (sessão já encerrada, ou cliente que o perdeu) o logout ainda responde
     * normalmente e registra a auditoria — é idempotente de propósito.
     */
    @Transactional
    public void logout(UsuarioAutenticado usuario, String refreshToken, String ip) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.findByTokenHash(hash(refreshToken))
                    // Um cookie de outro usuário não encerra a sessão alheia.
                    .filter(t -> t.getUsuario().getId().equals(usuario.id()))
                    .ifPresent(t -> {
                        t.setRevogado(true);
                        refreshTokens.save(t);
                    });
        }

        // O refresh token morre acima, mas o access token na mão do cliente continuaria
        // aceito até expirar. Aqui ele para de valer no mesmo instante.
        accessTokensRevogados.revogar(usuario.jti());

        auditoria.registrar(usuario.nome(), AuditoriaService.Tipo.LOGOUT, "Logout realizado", null, ip);
    }

    /**
     * Revoga todas as sessões do usuário menos a que fez o pedido. Chamado na troca de
     * senha: sem isso, trocar a senha não expulsava ninguém — quem tivesse copiado o
     * refresh token continuava renovando por até 7 dias, justamente no cenário em que a
     * troca acontece porque a senha vazou.
     *
     * <p>A sessão atual é poupada pelo hash do cookie que ela apresentou, para que quem
     * trocou a senha não seja jogado de volta na tela de login.
     *
     * <p>O access token das outras sessões não é alcançável aqui — {@link
     * AccessTokensRevogados} indexa por {@code jti}, e o {@code jti} das outras emissões
     * não fica guardado em lugar nenhum. Na prática elas seguem lendo por no máximo o TTL
     * do access token (15 min) e então param, porque a renovação já não passa.
     *
     * @return quantas sessões foram encerradas
     */
    @Transactional
    public int encerrarOutrasSessoes(Long usuarioId, String refreshTokenAtual) {
        // Sentinela em vez de null: comparar com null em JPQL exigiria um segundo ramo na
        // consulta, e nenhum hash SHA-256 é a string vazia.
        String hashPreservado = (refreshTokenAtual == null || refreshTokenAtual.isBlank())
                ? ""
                : hash(refreshTokenAtual);
        return refreshTokens.revogarOutrasSessoes(usuarioId, hashPreservado);
    }

    public UsuarioDTO me(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .map(UsuarioDTO::of)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
    }

    private SessaoEmitida gerarTokens(Usuario usuario) {
        String access = jwtService.gerarAccessToken(usuario);

        // O valor em claro só existe aqui e no cookie enviado ao cliente — o banco
        // guarda o hash, e o corpo da resposta não carrega o refresh token.
        String refreshTokenPlano = UUID.randomUUID().toString();

        RefreshToken novo = new RefreshToken();
        novo.setTokenHash(hash(refreshTokenPlano));
        novo.setUsuario(usuario);
        novo.setExpiraEm(Instant.now().plus(refreshTtl));
        refreshTokens.save(novo);

        return new SessaoEmitida(new TokenResponse(access, UsuarioDTO.of(usuario)), refreshTokenPlano);
    }

    private static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    /**
     * Barra a chave se ela estourou o limite da janela. O {@code Retry-After} vem junto
     * para o cliente saber quando voltar, em vez de repetir em laço.
     */
    private static void verificarBloqueio(JanelaDeTentativas janela, String chave,
                                          String codigo, String mensagem) {
        long faltam = janela.segundosDeBloqueio(chave);
        if (faltam > 0) {
            throw ApiException.tooManyRequests(codigo, mensagem, faltam);
        }
    }
}
