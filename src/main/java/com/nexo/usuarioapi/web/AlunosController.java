package com.nexo.usuarioapi.web;

import com.nexo.usuarioapi.dto.AlunoCadastroDto;
import com.nexo.usuarioapi.dto.AlunoCriadoDto;
import com.nexo.usuarioapi.dto.AlunoDto;
import com.nexo.usuarioapi.dto.AlunoResumoDto;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import com.nexo.usuarioapi.service.AlunoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/alunos")
public class AlunosController {

    private final AlunoService alunoService;

    public AlunosController(AlunoService alunoService) {
        this.alunoService = alunoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR','ADMIN')")
    public List<AlunoResumoDto> listar(@AuthenticationPrincipal UsuarioAutenticado operador) {
        return alunoService.listar(operador);
    }

    /** O ADMIN entra aqui porque é ele quem cria a conta: sem isso não releria o que gravou. */
    @GetMapping("/{alunoId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR','ADMIN')")
    public AlunoDto detalhar(@PathVariable Long alunoId, @AuthenticationPrincipal UsuarioAutenticado operador) {
        return alunoService.detalhar(alunoId, operador);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DIRETOR','ADMIN')")
    public ResponseEntity<AlunoCriadoDto> cadastrar(@Valid @RequestBody AlunoCadastroDto request,
                                                    @AuthenticationPrincipal UsuarioAutenticado operador) {
        AlunoCriadoDto criado = alunoService.cadastrar(request, operador.nome());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri()).body(criado);
    }
}
