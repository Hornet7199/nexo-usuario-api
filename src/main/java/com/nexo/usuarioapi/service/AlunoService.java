package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.domain.Aluno;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.dto.AlunoCadastroDto;
import com.nexo.usuarioapi.dto.AlunoCriadoDto;
import com.nexo.usuarioapi.dto.AlunoDto;
import com.nexo.usuarioapi.dto.AlunoResumoDto;
import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.repository.AlunoRepository;
import com.nexo.usuarioapi.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Cadastro e consulta de aluno: nome, turma e um acesso. O acesso é gerado pelo
 * {@link CredenciaisService} — o client não decide credencial.
 */
@Service
public class AlunoService {

    private final AlunoRepository alunos;
    private final CredenciaisService credenciais;
    private final AuditoriaService auditoria;
    private final PedagogicoClient pedagogico;

    public AlunoService(AlunoRepository alunos, CredenciaisService credenciais,
                        AuditoriaService auditoria, PedagogicoClient pedagogico) {
        this.alunos = alunos;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
        this.pedagogico = pedagogico;
    }

    @Transactional
    public AlunoCriadoDto cadastrar(AlunoCadastroDto dados, String operador) {
        String nomeTurma = pedagogico.turmas().get(dados.turmaId());
        if (pedagogico.habilitado() && nomeTurma == null) {
            throw ApiException.badRequest("Turma inexistente.");
        }

        Aluno aluno = new Aluno();
        aluno.setNome(dados.nome().trim());
        aluno.setTurmaId(dados.turmaId());

        var acesso = credenciais.gerar(aluno.getNome());
        aluno.setEmailInstitucional(acesso.login());
        aluno.setUsuario(credenciais.criarUsuario(aluno.getNome(),
                nomeTurma != null ? nomeTurma : "Aluno", Role.ALUNO, acesso));
        alunos.save(aluno);

        auditoria.registrar(operador, AuditoriaService.Tipo.ALTERACAO,
                "Aluno cadastrado", "Aluno: " + aluno.getNome(), null);

        return new AlunoCriadoDto(aluno.getId(), aluno.getNome(), acesso.login(), acesso.senhaProvisoria());
    }

    /**
     * Os alunos que o operador alcança: o professor, os das turmas que leciona; diretor e
     * administrador, a escola inteira. O recorte é feito aqui — filtro de tela é
     * conveniência, não regra de acesso.
     */
    @Transactional(readOnly = true)
    public List<AlunoResumoDto> listar(UsuarioAutenticado operador) {
        if (!ehProfessor(operador)) {
            Map<Long, String> nomes = nomesDeTurmasOuVazio();
            return alunos.findAllByOrderByNome().stream()
                    .map(a -> AlunoResumoDto.of(a, nomes.get(a.getTurmaId())))
                    .toList();
        }

        // Docente sem turma atribuída enxerga lista vazia — não a escola inteira.
        Map<Long, String> minhasTurmas = pedagogico.turmas();
        if (minhasTurmas.isEmpty()) return List.of();
        return alunos.findByTurmaIdInOrderByNome(minhasTurmas.keySet()).stream()
                .map(a -> AlunoResumoDto.of(a, minhasTurmas.get(a.getTurmaId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlunoDto detalhar(Long id, UsuarioAutenticado operador) {
        Aluno aluno = alunos.findById(id)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));

        Map<Long, String> turmas;
        if (ehProfessor(operador)) {
            turmas = pedagogico.turmas();
            if (!turmas.containsKey(aluno.getTurmaId())) {
                throw ApiException.forbidden("Você não leciona a turma deste aluno.");
            }
        } else {
            turmas = nomesDeTurmasOuVazio();
        }
        return AlunoDto.of(aluno, turmas.get(aluno.getTurmaId()));
    }

    private static boolean ehProfessor(UsuarioAutenticado operador) {
        return Role.PROFESSOR.name().equals(operador.role());
    }

    /** Nome da turma é enfeite na listagem: se o catálogo cair, a lista continua. */
    private Map<Long, String> nomesDeTurmasOuVazio() {
        try {
            return pedagogico.turmas();
        } catch (ApiException e) {
            return Map.of();
        }
    }
}
