package com.nexo.usuarioapi.config;

import com.nexo.usuarioapi.domain.Aluno;
import com.nexo.usuarioapi.domain.Professor;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import com.nexo.usuarioapi.repository.AlunoRepository;
import com.nexo.usuarioapi.repository.ProfessorRepository;
import com.nexo.usuarioapi.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

/**
 * Contas de exemplo para desenvolvimento, com os mesmos logins do monólito (diretor,
 * professor, aluno, admin) para o front entrar sem ajuste.
 *
 * <p>Só roda com {@code nexo.seed.enabled=true} (padrão: desligado), num banco vazio e
 * nunca com o perfil {@code prod}: as contas nascem com senha conhecida ("123456").
 */
@Component
@ConditionalOnProperty(name = "nexo.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SENHA_PADRAO = "123456";

    private final UsuarioRepository usuarios;
    private final AlunoRepository alunos;
    private final ProfessorRepository professores;
    private final PasswordEncoder encoder;
    private final Environment env;

    public DataSeeder(UsuarioRepository usuarios, AlunoRepository alunos, ProfessorRepository professores,
                      PasswordEncoder encoder, Environment env) {
        this.usuarios = usuarios;
        this.alunos = alunos;
        this.professores = professores;
        this.encoder = encoder;
        this.env = env;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "nexo.seed.enabled=true com o perfil prod: o seed cria contas com senha conhecida.");
        }
        if (usuarios.count() > 0) return;

        String hash = encoder.encode(SENHA_PADRAO);

        // A ordem importa: os ids saem iguais aos do seed do monólito (diretor=1, professor=2,
        // aluno=3, admin=4). O token carrega o id do usuário e o monólito o usa para achar o
        // aluno/professor correspondente enquanto os dois bancos coexistem.
        criarUsuario("diretor", hash, "Diretor Silva", "Administração Escolar", Role.DIRETOR);

        Usuario professorUsr = criarUsuario("professor", hash, "Prof. Roberto Alves", "Professor de História",
                Role.PROFESSOR);
        Professor roberto = new Professor();
        roberto.setUsuario(professorUsr);
        roberto.setNome(professorUsr.getNome());
        roberto.setSexo("M");
        roberto.setDataNascimento(LocalDate.of(1979, 4, 12));
        roberto.setEmail("roberto.alves@nexo.escola.com");
        roberto.setMateriaIds(Set.of(1L));
        roberto.setDisciplinas("História");
        professores.save(roberto);

        Usuario alunoUsr = criarUsuario("aluno", hash, "Gabriel Mendes", "2º Ano - Ensino Médio", Role.ALUNO);
        Aluno gabriel = new Aluno();
        gabriel.setUsuario(alunoUsr);
        gabriel.setNome(alunoUsr.getNome());
        gabriel.setEmailInstitucional("gabriel.mendes@nexo.escola.com");
        gabriel.setTurmaId(4L);
        alunos.save(gabriel);

        criarUsuario("admin", hash, "Helena Ramos", "Administração do sistema", Role.ADMIN);

        log.warn("Seed de desenvolvimento aplicado: contas diretor/admin/professor/aluno com senha padrão.");
    }

    private Usuario criarUsuario(String login, String hash, String nome, String cargo, Role role) {
        Usuario u = new Usuario();
        u.setLogin(login);
        u.setSenhaHash(hash);
        u.setNome(nome);
        u.setCargo(cargo);
        u.setRole(role);
        return usuarios.save(u);
    }
}
