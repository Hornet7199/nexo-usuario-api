package com.nexo.usuarioapi.service;

import com.nexo.usuarioapi.domain.FotoPerfil;
import com.nexo.usuarioapi.domain.Usuario;
import com.nexo.usuarioapi.dto.AuthDtos.AtualizarPerfilRequest;
import com.nexo.usuarioapi.dto.AuthDtos.TrocaSenhaRequest;
import com.nexo.usuarioapi.dto.AuthDtos.UsuarioDTO;
import com.nexo.usuarioapi.dto.AuthDtos.ValidacaoSenhaResponse;
import com.nexo.usuarioapi.dto.AuthDtos.ValidarSenhaRequest;
import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.repository.AlunoRepository;
import com.nexo.usuarioapi.repository.FotoPerfilRepository;
import com.nexo.usuarioapi.repository.ProfessorRepository;
import com.nexo.usuarioapi.repository.UsuarioRepository;
import com.nexo.usuarioapi.security.PoliticaSenha;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Perfil do próprio usuário: nome, foto e senha. */
@Service
public class PerfilService {

    /** Limite do servidor; o cliente já reduz a imagem antes de enviar. */
    public static final long TAMANHO_MAXIMO_FOTO = 2 * 1024 * 1024;

    private final UsuarioRepository usuarios;
    private final FotoPerfilRepository fotos;
    private final AlunoRepository alunos;
    private final ProfessorRepository professores;
    private final PasswordEncoder passwordEncoder;
    private final PoliticaSenha politicaSenha;
    private final AuthService authService;
    private final AuditoriaService auditoria;

    public PerfilService(UsuarioRepository usuarios, FotoPerfilRepository fotos, AlunoRepository alunos,
                         ProfessorRepository professores, PasswordEncoder passwordEncoder,
                         PoliticaSenha politicaSenha, AuthService authService, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.fotos = fotos;
        this.alunos = alunos;
        this.professores = professores;
        this.passwordEncoder = passwordEncoder;
        this.politicaSenha = politicaSenha;
        this.authService = authService;
        this.auditoria = auditoria;
    }

    /**
     * Assinatura dos únicos formatos que o navegador exibe direto em &lt;img&gt;, lida dos
     * primeiros bytes do arquivo. Os bytes voltam em /api/fotos/{id} com o tipo declarado
     * aqui, da mesma origem da aplicação — por isso o formato é confirmado no conteúdo, e
     * não aceito do cabeçalho que o cliente enviou.
     */
    static String detectarTipoImagem(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public UsuarioDTO me(Long usuarioId) {
        return UsuarioDTO.of(carregar(usuarioId));
    }

    @Transactional
    public UsuarioDTO atualizarPerfil(Long usuarioId, AtualizarPerfilRequest request) {
        Usuario usuario = carregar(usuarioId);
        if (request.nome() != null && !request.nome().isBlank()) {
            usuario.setNome(request.nome().trim());
        }
        usuarios.save(usuario);
        auditoria.registrar(usuario.getNome(), AuditoriaService.Tipo.ALTERACAO, "Perfil atualizado", null, null);
        return UsuarioDTO.of(usuario);
    }

    @Transactional
    public UsuarioDTO enviarFoto(Long usuarioId, byte[] dados) {
        if (dados.length == 0) {
            throw ApiException.badRequest("Nenhuma imagem enviada.");
        }
        if (dados.length > TAMANHO_MAXIMO_FOTO) {
            throw ApiException.badRequest("A imagem deve ter no máximo 2 MB.");
        }
        String tipo = detectarTipoImagem(dados);
        if (tipo == null) {
            throw ApiException.badRequest("Formato inválido. Envie uma imagem JPEG, PNG ou WebP.");
        }

        Usuario usuario = carregar(usuarioId);
        // Uma foto por usuário: a anterior sai junto com o id antigo, invalidando o cache.
        fotos.deleteByUsuarioId(usuario.getId());
        fotos.flush();

        String id = UUID.randomUUID().toString().replace("-", "");
        fotos.save(new FotoPerfil(id, usuario.getId(), tipo, dados, Instant.now()));

        usuario.setFoto("/api/fotos/" + id);
        usuarios.save(usuario);
        sincronizarFoto(usuario);
        auditoria.registrar(usuario.getNome(), AuditoriaService.Tipo.ALTERACAO, "Foto de perfil atualizada", null, null);
        return UsuarioDTO.of(usuario);
    }

    @Transactional
    public UsuarioDTO removerFoto(Long usuarioId) {
        Usuario usuario = carregar(usuarioId);
        fotos.deleteByUsuarioId(usuario.getId());
        usuario.setFoto(null);
        usuarios.save(usuario);
        sincronizarFoto(usuario);
        auditoria.registrar(usuario.getNome(), AuditoriaService.Tipo.ALTERACAO, "Foto de perfil removida", null, null);
        return UsuarioDTO.of(usuario);
    }

    /**
     * Troca a senha do próprio usuário. Além de gravar o hash: aplica a política, recusa a
     * senha igual à atual e encerra as <b>outras</b> sessões (a que pediu fica de pé pelo
     * cookie que apresentou). Senha nova e sessões derrubadas andam na mesma transação.
     */
    @Transactional
    public void trocarSenha(Long usuarioId, String refreshTokenAtual, TrocaSenhaRequest request) {
        Usuario usuario = carregar(usuarioId);

        // Códigos e campos próprios: a tela distingue "errou a senha atual" de "a nova foi recusada".
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            final String motivo = "A senha atual não confere. Digite a senha com que você entrou no sistema.";
            throw new ApiException(HttpStatus.BAD_REQUEST, "SENHA_ATUAL_INCORRETA", motivo,
                    Map.of("senhaAtual", motivo));
        }
        if (passwordEncoder.matches(request.novaSenha(), usuario.getSenhaHash())) {
            final String motivo = "A nova senha é igual à atual. Escolha uma diferente.";
            throw new ApiException(HttpStatus.BAD_REQUEST, "SENHA_IGUAL_A_ATUAL", motivo,
                    Map.of("novaSenha", motivo));
        }
        politicaSenha.validar(request.novaSenha(), usuario.getLogin(), usuario.getNome());

        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuarios.save(usuario);

        int encerradas = authService.encerrarOutrasSessoes(usuario.getId(), refreshTokenAtual);
        String detalhe = encerradas > 0 ? encerradas + " outra(s) sessão(ões) encerrada(s)" : null;
        auditoria.registrar(usuario.getNome(), AuditoriaService.Tipo.ALTERACAO, "Senha alterada", detalhe, null);
    }

    /** Checagem prévia da nova senha: aplica a mesma política da troca, sem gravar nada. */
    @Transactional(readOnly = true)
    public ValidacaoSenhaResponse validarSenha(Long usuarioId, ValidarSenhaRequest request) {
        Usuario usuario = carregar(usuarioId);
        PoliticaSenha.Recusa recusa = politicaSenha.avaliar(request.novaSenha(), usuario.getLogin(), usuario.getNome());
        return recusa == null
                ? new ValidacaoSenhaResponse(true, null, null)
                : new ValidacaoSenhaResponse(false, recusa.codigo(), recusa.motivo());
    }

    /** Aluno.foto e Professor.foto são cópias denormalizadas — sem sincronizar ficam presas na antiga. */
    private void sincronizarFoto(Usuario usuario) {
        alunos.findByUsuarioId(usuario.getId()).ifPresent(a -> {
            a.setFoto(usuario.getFoto());
            alunos.save(a);
        });
        professores.findByUsuarioId(usuario.getId()).ifPresent(p -> {
            p.setFoto(usuario.getFoto());
            professores.save(p);
        });
    }

    private Usuario carregar(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
    }
}
