package com.nexo.usuarioapi.web;

import com.nexo.usuarioapi.security.UsuarioAutenticado;
import com.nexo.usuarioapi.service.ConfiguracaoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/configuracoes")
public class ConfiguracoesController {

    private final ConfiguracaoService service;

    public ConfiguracoesController(ConfiguracaoService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Map<String, Object>> obter(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.obter(usuario.id());
    }

    @PatchMapping("/{secao}")
    public Map<String, Map<String, Object>> atualizarSecao(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                           @PathVariable String secao,
                                                           @RequestBody Map<String, Object> patch) {
        return service.atualizarSecao(usuario.id(), secao, patch);
    }
}
