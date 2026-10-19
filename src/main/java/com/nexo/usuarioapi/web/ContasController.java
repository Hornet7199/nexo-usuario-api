package com.nexo.usuarioapi.web;

import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.dto.ContaDtos.AtualizarAtivoRequest;
import com.nexo.usuarioapi.dto.ContaDtos.ContaDto;
import com.nexo.usuarioapi.dto.ContaDtos.SenhaRedefinidaDto;
import com.nexo.usuarioapi.dto.PageEnvelope;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import com.nexo.usuarioapi.service.ContaService;
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

    public ContasController(ContaService contaService) {
        this.contaService = contaService;
    }

    @GetMapping
    public PageEnvelope<ContaDto> listar(@RequestParam(required = false) Role papel,
                                         @RequestParam(required = false) String busca,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return contaService.listar(papel, busca, page, size);
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
