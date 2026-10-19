package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.domain.Professor;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.dto.ProfessorCadastroDto;
import com.nexo.usuarioapi.dto.ProfessorCriadoDto;
import com.nexo.usuarioapi.dto.ProfessorDto;
import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.repository.ProfessorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cadastro de professor: dados básicos e matérias que leciona. Reutiliza o
 * {@link CredenciaisService} do cadastro de aluno para gerar o acesso.
 */
@Service
public class ProfessorService {

    /** Não se contrata menor de idade para lecionar. */
    private static final int IDADE_MINIMA_PROFESSOR = 18;
    private static final int IDADE_MAXIMA_PROFESSOR = 100;

    /**
     * Teto de matérias por docente: sem ele dava para marcar o catálogo inteiro e criar um
     * professor que leciona tudo, o que desmonta a regra de "só mexe na matéria dele".
     */
    private static final int MAXIMO_MATERIAS = 3;

    private final ProfessorRepository professores;
    private final CredenciaisService credenciais;
    private final AuditoriaService auditoria;
    private final PedagogicoClient pedagogico;

    public ProfessorService(ProfessorRepository professores, CredenciaisService credenciais,
                            AuditoriaService auditoria, PedagogicoClient pedagogico) {
        this.professores = professores;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
        this.pedagogico = pedagogico;
    }

    @Transactional
    public ProfessorCriadoDto cadastrar(ProfessorCadastroDto dados, String operador) {
        Map<String, String> erros = new LinkedHashMap<>();

        LocalDate nascimento = DataNascimento.validar(dados.dataNascimento(),
                IDADE_MINIMA_PROFESSOR, IDADE_MAXIMA_PROFESSOR, erros);

        Set<Long> materiaIds = new LinkedHashSet<>(dados.materiaIds());
        String disciplinas = null;
        if (materiaIds.size() > MAXIMO_MATERIAS) {
            erros.put("materiaIds", "Um professor leciona no máximo " + MAXIMO_MATERIAS + " matérias.");
        } else if (pedagogico.habilitado()) {
            Map<Long, String> catalogo = pedagogico.materias();
            if (!catalogo.keySet().containsAll(materiaIds)) {
                erros.put("materiaIds", "Alguma matéria selecionada não existe.");
            } else {
                disciplinas = materiaIds.stream().map(catalogo::get).sorted().collect(Collectors.joining(", "));
            }
        }
        if (!erros.isEmpty()) {
            throw ApiException.validation("Dados de cadastro inválidos.", erros);
        }

        Professor professor = new Professor();
        professor.setNome(dados.nome().trim());
        professor.setDataNascimento(nascimento);
        professor.setSexo(dados.sexo());
        professor.setMateriaIds(materiaIds);
        professor.setDisciplinas(disciplinas);

        var acesso = credenciais.gerar(professor.getNome());
        professor.setEmail(acesso.login());
        professor.setUsuario(credenciais.criarUsuario(professor.getNome(), cargo(professor), Role.PROFESSOR, acesso));
        professores.save(professor);

        auditoria.registrar(operador, AuditoriaService.Tipo.ALTERACAO,
                "Professor cadastrado", "Professor: " + professor.getNome(), null);

        return new ProfessorCriadoDto(professor.getId(), professor.getNome(), disciplinas,
                acesso.login(), acesso.senhaProvisoria());
    }

    @Transactional(readOnly = true)
    public ProfessorDto detalhar(Long id) {
        return professores.findById(id).map(ProfessorDto::of)
                .orElseThrow(() -> ApiException.notFound("Professor não encontrado."));
    }

    /** Cargo exibido no perfil, no mesmo formato dos docentes já existentes. */
    private static String cargo(Professor professor) {
        String titulo = "F".equalsIgnoreCase(professor.getSexo()) ? "Professora" : "Professor";
        String disciplinas = professor.getDisciplinas();
        return disciplinas == null || disciplinas.isBlank() ? titulo : titulo + " de " + disciplinas;
    }
}
