package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import com.nexo.usuarioapi.repository.AlunoRepository;
import com.nexo.usuarioapi.repository.ProfessorRepository;
import com.nexo.usuarioapi.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.text.Normalizer;

/**
 * Geração do acesso ao sistema (e-mail institucional + senha provisória),
 * compartilhada pelos cadastros de aluno e de professor. A regra é a mesma que
 * vivia no AlunoService: o client nunca decide credenciais.
 */
@Service
public class CredenciaisService {

    private static final String DOMINIO = "nexo.escola.com";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final AlunoRepository alunos;
    private final ProfessorRepository professores;
    private final PasswordEncoder passwordEncoder;

    public CredenciaisService(UsuarioRepository usuarios, AlunoRepository alunos,
                              ProfessorRepository professores, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.alunos = alunos;
        this.professores = professores;
        this.passwordEncoder = passwordEncoder;
    }

    /** Credenciais recém-geradas; a senha só existe em claro aqui e na resposta do cadastro. */
    public record Acesso(String login, String senhaProvisoria) {}

    public Acesso gerar(String nomeCompleto) {
        return new Acesso(gerarEmailInstitucional(nomeCompleto), gerarSenhaProvisoria());
    }

    /** Cria e persiste o Usuario de login com as credenciais geradas. */
    public Usuario criarUsuario(String nome, String cargo, Role role, Acesso acesso) {
        Usuario usuario = new Usuario();
        usuario.setLogin(acesso.login());
        usuario.setSenhaHash(passwordEncoder.encode(acesso.senhaProvisoria()));
        usuario.setPrecisaTrocarSenha(true);
        usuario.setNome(nome);
        usuario.setCargo(cargo);
        usuario.setRole(role);
        return usuarios.save(usuario);
    }

    /**
     * Sorteia uma senha provisória nova para uma conta que já existe e devolve o
     * valor em claro, que só existe aqui e na resposta ao administrador. Quem chama
     * é quem grava o usuário — e é quem precisa derrubar as sessões abertas.
     */
    public String redefinirSenhaProvisoria(Usuario usuario) {
        String senha = gerarSenhaProvisoria();
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setPrecisaTrocarSenha(true);
        return senha;
    }

    /** primeiro-nome.sobrenome@dominio, com sufixo numérico em caso de colisão. */
    private String gerarEmailInstitucional(String nomeCompleto) {
        String[] partes = normalizar(nomeCompleto).split("\\s+");
        String base = partes.length > 1 ? partes[0] + "." + partes[partes.length - 1] : partes[0];
        String email = base + "@" + DOMINIO;
        int sufixo = 1;
        while (emailEmUso(email)) {
            email = base + (++sufixo) + "@" + DOMINIO;
        }
        return email;
    }

    private boolean emailEmUso(String email) {
        return usuarios.existsByLoginIgnoreCase(email)
                || alunos.existsByEmailInstitucional(email)
                || professores.existsByEmail(email);
    }

    private String gerarSenhaProvisoria() {
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder("Nexo@");
        for (int i = 0; i < 6; i++) {
            sb.append(alfabeto.charAt(RANDOM.nextInt(alfabeto.length())));
        }
        return sb.toString();
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z\\s]", "");
    }
}
