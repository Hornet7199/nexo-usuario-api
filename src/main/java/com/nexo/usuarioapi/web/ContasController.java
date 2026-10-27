package com.nexo.usuarioapi.web;

import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.dto.ContaDtos.AtualizarAtivoRequest;
import com.nexo.usuarioapi.dto.ContaDtos.ContaDto;
import com.nexo.usuarioapi.dto.ContaDtos.ResumoContasDto;
import com.nexo.usuarioapi.dto.ContaDtos.SenhaRedefinidaDto;
import com.nexo.usuarioapi.dto.PageEnvelope;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import com.nexo.usuarioapi.service.ContaService;
import com.nexo.usuarioapi.service.SincronizacaoContatos;
import com.nexo.usuarioapi.service.SincronizacaoJogadores;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Painel de contas do administrador (mesmo caminho que o front já usa: /api/admin/contas). */
@RestController
@RequestMapping("/api/admin/contas")
@PreAuthorize("hasAnyRole('ADMIN','DIRETOR')")
public class ContasController {

    private final ContaService contaService;
    private final SincronizacaoContatos sincronizacaoContatos;
    private final SincronizacaoJogadores sincronizacaoJogadores;

    public ContasController(ContaService contaService, SincronizacaoContatos sincronizacaoContatos,
                            SincronizacaoJogadores sincronizacaoJogadores) {
        this.contaService = contaService;
        this.sincronizacaoContatos = sincronizacaoContatos;
        this.sincronizacaoJogadores = sincronizacaoJogadores;
    }

    @GetMapping
    public PageEnvelope<ContaDto> listar(@RequestParam(required = false) Role papel,
                                         @RequestParam(required = false) String busca,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return contaService.listar(papel, busca, page, size);
    }

    /** Números de contas do painel admin; os de catálogo vêm do pedagogico-api. */
    @GetMapping("/resumo")
    public ResumoContasDto resumo() {
        return contaService.resumo();
    }

    /**
     * Reenvia todas as contas ao serviço de comunicação. Para quando uma notificação se perdeu
     * (o chat estava fora do ar num cadastro) ou para popular um chat novo.
     */
    @PostMapping("/sincronizar-contatos")
    public java.util.Map<String, Integer> sincronizarContatos() {
        return java.util.Map.of("contatos", sincronizacaoContatos.publicarTodos());
    }

    /** Idem para a gamificação: reenvia todos os alunos (nome, foto e privacidade) ao ranking. */
    @PostMapping("/sincronizar-jogadores")
    public java.util.Map<String, Integer> sincronizarJogadores() {
        return java.util.Map.of("jogadores", sincronizacaoJogadores.publicarTodos());
    }

    @PatchMapping("/{id}/ativo")
    public ContaDto atualizarAtivo(@PathVariable Long id, @Valid @RequestBody AtualizarAtivoRequest request,
                                   @AuthenticationPrincipal UsuarioAutenticado operador) {
        return contaService.atualizarAtivo(id, request.ativo(), operador);
    }

    @PostMapping("/{id}/senha-provisoria")
    public SenhaRedefinidaDto redefinirSenha(@PathVariable Long id,
                                             @AuthenticationPrincipal UsuarioAutenticado operador) {
        return contaService.redefinirSenha(id, operador);
    }
}
