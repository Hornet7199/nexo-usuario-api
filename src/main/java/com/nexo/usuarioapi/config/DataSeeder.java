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

    // Ids das matérias no catálogo do pedagogico-api (ordem de inserção do CatalogoMaterias).
    private static final long MATERIA_BIOLOGIA = 2L;
    private static final long MATERIA_FISICA = 5L;
    private static final long MATERIA_HISTORIA = 7L;
    private static final long MATERIA_MATEMATICA = 9L;
    private static final long MATERIA_QUIMICA = 11L;

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

        // A ordem importa: os ids saem iguais aos do seed do pedagogico-api. Contas: diretor=1,
        // professor=2, aluno=3, admin=4. Professores 1 a 5 e alunos 1 a 12 também seguem a mesma
        // ordem de lá — é por esses ids que o pedagógico liga notas e frequência a cada pessoa.
        // Mexer na ordem ou na lista aqui sem mexer no DataSeeder do pedagogico-api desalinha tudo.
        criarUsuario("diretor", hash, "Diretor Silva", "Administração Escolar", Role.DIRETOR);

        Usuario professorUsr = criarUsuario("professor", hash, "Prof. Roberto Alves", "Professor de História",
                Role.PROFESSOR);
        Professor roberto = new Professor();
        roberto.setUsuario(professorUsr);
        roberto.setNome(professorUsr.getNome());
        roberto.setSexo("M");
        roberto.setDataNascimento(LocalDate.of(1979, 4, 12));
        roberto.setEmail("roberto.alves@nexo.escola.com");
        roberto.setMateriaIds(Set.of(MATERIA_HISTORIA));
        roberto.setDisciplinas("História");
        professores.save(roberto);

        // Demais docentes do corpo (sem conta de acesso), ids 2 a 5
        docente("Profa. Carla Nunes", "F", LocalDate.of(1985, 9, 3), "carla.nunes", "Matemática", MATERIA_MATEMATICA);
        docente("Profa. Juliana Costa Santos", "F", LocalDate.of(1983, 1, 27), "juliana.santos", "Biologia",
                MATERIA_BIOLOGIA);
        docente("Profa. Mariana Oliveira", "F", LocalDate.of(1990, 6, 15), "mariana.oliveira", "Biologia, Química",
                MATERIA_BIOLOGIA, MATERIA_QUIMICA);
        docente("Prof. Carlos Eduardo Silva", "M", LocalDate.of(1976, 11, 2), "carlos.silva", "Física, Matemática",
                MATERIA_FISICA, MATERIA_MATEMATICA);

        Usuario alunoUsr = criarUsuario("aluno", hash, "Gabriel Mendes", "2º Ano - Ensino Médio", Role.ALUNO);
        Aluno gabriel = new Aluno();
        gabriel.setUsuario(alunoUsr);
        gabriel.setNome(alunoUsr.getNome());
        gabriel.setEmailInstitucional("gabriel.mendes@nexo.escola.com");
        gabriel.setTurmaId(4L);
        alunos.save(gabriel);

        // Demais alunos (sem conta de acesso), ids 2 a 12
        String[][] outros = {
                {"Ana Beatriz Souza", "1"}, {"Carlos Eduardo Lima", "1"}, {"Mariana Ferreira", "1"},
                {"João Pedro Santos", "2"}, {"Larissa Oliveira", "2"}, {"Rafael Costa", "2"},
                {"Beatriz Almeida", "3"}, {"Lucas Martins", "3"}, {"Julia Rodrigues", "3"},
                {"Pedro Henrique Silva", "4"}, {"Camila Barbosa", "4"},
        };
        for (String[] d : outros) {
            Aluno a = new Aluno();
            a.setNome(d[0]);
            a.setTurmaId(Long.parseLong(d[1]));
            String primeiro = d[0].split(" ")[0].toLowerCase();
            String ultimo = d[0].substring(d[0].lastIndexOf(' ') + 1).toLowerCase();
            a.setEmailInstitucional(primeiro + "." + ultimo + "@nexo.escola.com");
            alunos.save(a);
        }

        criarUsuario("admin", hash, "Helena Ramos", "Administração do sistema", Role.ADMIN);

        log.warn("Seed de desenvolvimento aplicado: contas diretor/admin/professor/aluno com senha padrão, "
                + "mais 5 professores e 12 alunos alinhados ao seed do pedagogico-api.");
    }

    private void docente(String nome, String sexo, LocalDate nascimento, String emailLocal, String disciplinas,
                         Long... materiaIds) {
        Professor p = new Professor();
        p.setNome(nome);
        p.setSexo(sexo);
        p.setDataNascimento(nascimento);
        p.setEmail(emailLocal + "@nexo.escola.com");
        p.setMateriaIds(new java.util.LinkedHashSet<>(java.util.List.of(materiaIds)));
        p.setDisciplinas(disciplinas);
        professores.save(p);
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
