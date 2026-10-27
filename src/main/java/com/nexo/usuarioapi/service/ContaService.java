package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import com.nexo.usuarioapi.dto.ContaDtos.ContaDto;
import com.nexo.usuarioapi.dto.ContaDtos.ResumoContasDto;
import com.nexo.usuarioapi.dto.ContaDtos.SenhaRedefinidaDto;
import com.nexo.usuarioapi.dto.PageEnvelope;
import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.repository.RefreshTokenRepository;
import com.nexo.usuarioapi.repository.UsuarioRepository;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de contas pelo administrador: listar, ativar/desativar e redefinir a senha. */
@Service
public class ContaService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final CredenciaisService credenciais;
    private final AuditoriaService auditoria;

    public ContaService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens,
                        CredenciaisService credenciais, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public PageEnvelope<ContaDto> listar(Role papel, String busca, int page, int size) {
        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim().toLowerCase() + "%";
        var pagina = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), TAMANHO_MAXIMO_PAGINA));
        return PageEnvelope.of(usuarios.buscar(papel, filtro, pagina), ContaDto::of);
    }

    @Transactional(readOnly = true)
    public ResumoContasDto resumo() {
        var porPapel = new java.util.EnumMap<Role, Long>(Role.class);
        long contas = 0;
        long inativas = 0;
        for (Object[] linha : usuarios.contarPorPapelEAtivo()) {
            long total = ((Number) linha[2]).longValue();
            contas += total;
            if (!Boolean.TRUE.equals(linha[1])) inativas += total;
            porPapel.merge((Role) linha[0], total, Long::sum);
        }
        return new ResumoContasDto(contas, inativas,
                porPapel.getOrDefault(Role.ALUNO, 0L), porPapel.getOrDefault(Role.PROFESSOR, 0L),
                porPapel.getOrDefault(Role.DIRETOR, 0L), porPapel.getOrDefault(Role.ADMIN, 0L));
    }

    /**
     * Liga ou desliga o acesso. Desativar derruba também as sessões abertas: sem isso o
     * refresh token na mão de quem foi desligado renovaria o acesso por dias. O access
     * token já emitido vale até expirar (15 minutos).
     */
    @Transactional
    public ContaDto atualizarAtivo(Long id, boolean ativo, UsuarioAutenticado operador) {
        // Desativar a própria conta tranca quem administra do lado de fora.
        if (id.equals(operador.id()) && !ativo) {
            throw ApiException.badRequest("Você não pode desativar a própria conta.");
        }
        Usuario usuario = exigirConta(id);
        if (usuario.isAtivo() == ativo) {
            return ContaDto.of(usuario); // idempotente
        }
        usuario.setAtivo(ativo);
        usuarios.save(usuario);
        if (!ativo) {
            refreshTokens.revogarTodosDoUsuario(usuario.getId());
        }
        auditoria.registrar(operador.nome(), AuditoriaService.Tipo.ALTERACAO,
                ativo ? "Conta reativada" : "Conta desativada", "Conta: " + usuario.getLogin(), null);
        return ContaDto.of(usuario);
    }

    /**
     * Sorteia uma senha provisória para quem perdeu a dela. As sessões abertas caem junto:
     * uma redefinição costuma vir de suspeita de acesso indevido.
     */
    @Transactional
    public SenhaRedefinidaDto redefinirSenha(Long id, UsuarioAutenticado operador) {
        Usuario usuario = exigirConta(id);
        String senha = credenciais.redefinirSenhaProvisoria(usuario);
        usuarios.save(usuario);
        refreshTokens.revogarTodosDoUsuario(usuario.getId());
        auditoria.registrar(operador.nome(), AuditoriaService.Tipo.ALTERACAO,
                "Senha provisória redefinida", "Conta: " + usuario.getLogin(), null);
        return new SenhaRedefinidaDto(usuario.getLogin(), senha);
    }

    private Usuario exigirConta(Long id) {
        return usuarios.findById(id).orElseThrow(() -> ApiException.notFound("Conta não encontrada."));
    }
}
