package com.nexo.usuarioapi.web;

import com.nexo.usuarioapi.dto.ProfessorCadastroDto;
import com.nexo.usuarioapi.dto.ProfessorCriadoDto;
import com.nexo.usuarioapi.dto.ProfessorDto;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import com.nexo.usuarioapi.service.ProfessorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/professores")
public class ProfessoresController {

    private final ProfessorService professorService;

    public ProfessoresController(ProfessorService professorService) {
        this.professorService = professorService;
    }

    /** Recurso endereçável do docente — destino do Location do cadastro. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DIRETOR')")
    public ProfessorDto detalhar(@PathVariable Long id) {
        return professorService.detalhar(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('DIRETOR')")
    public ResponseEntity<ProfessorCriadoDto> cadastrar(@Valid @RequestBody ProfessorCadastroDto request,
                                                        @AuthenticationPrincipal UsuarioAutenticado operador) {
        ProfessorCriadoDto criado = professorService.cadastrar(request, operador.nome());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri()).body(criado);
    }
}
