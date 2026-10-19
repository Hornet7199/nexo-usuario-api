package com.nexo.usuarioapi.web;

import com.nexo.usuarioapi.dto.AuthDtos.AtualizarPerfilRequest;
import com.nexo.usuarioapi.dto.AuthDtos.TrocaSenhaRequest;
import com.nexo.usuarioapi.dto.AuthDtos.UsuarioDTO;
import com.nexo.usuarioapi.dto.AuthDtos.ValidacaoSenhaResponse;
import com.nexo.usuarioapi.dto.AuthDtos.ValidarSenhaRequest;
import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.security.RefreshTokenCookie;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import com.nexo.usuarioapi.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Perfil do usuário logado: dados, foto e senha. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuariosController {

    private final PerfilService perfilService;

    public UsuariosController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping("/me")
    public UsuarioDTO me(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return perfilService.me(principal.id());
    }

    @PatchMapping("/me")
    public UsuarioDTO atualizarPerfil(@AuthenticationPrincipal UsuarioAutenticado principal,
                                      @Valid @RequestBody AtualizarPerfilRequest request) {
        return perfilService.atualizarPerfil(principal.id(), request);
    }

    /** A foto tirada na hora ou escolhida da galeria; o tipo e o tamanho são checados no serviço. */
    @PostMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UsuarioDTO enviarFoto(@AuthenticationPrincipal UsuarioAutenticado principal,
                                 @RequestPart("arquivo") MultipartFile arquivo) {
        try {
            return perfilService.enviarFoto(principal.id(), arquivo.getBytes());
        } catch (IOException e) {
            throw ApiException.badRequest("Não foi possível ler a imagem enviada.");
        }
    }

    /** Volta ao avatar padrão. */
    @DeleteMapping("/me/foto")
    public UsuarioDTO removerFoto(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return perfilService.removerFoto(principal.id());
    }

    /** Mutação sem corpo de resposta: 204. As outras sessões do usuário são encerradas. */
    @PostMapping("/me/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@AuthenticationPrincipal UsuarioAutenticado principal,
                            @CookieValue(name = RefreshTokenCookie.NOME, required = false) String refreshToken,
                            @Valid @RequestBody TrocaSenhaRequest request) {
        perfilService.trocarSenha(principal.id(), refreshToken, request);
    }

    /** Responde 200 com o veredito: senha recusada aqui é resposta, não erro. */
    @PostMapping("/me/senha/validar")
    public ValidacaoSenhaResponse validarSenha(@AuthenticationPrincipal UsuarioAutenticado principal,
                                               @Valid @RequestBody ValidarSenhaRequest request) {
        return perfilService.validarSenha(principal.id(), request);
    }
}
