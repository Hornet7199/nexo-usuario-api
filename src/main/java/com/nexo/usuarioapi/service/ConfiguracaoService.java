package com.nexo.usuarioapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.usuarioapi.exception.ApiException;
import com.nexo.usuarioapi.domain.ConfiguracaoUsuario;
import com.nexo.usuarioapi.domain.Role;
import com.nexo.usuarioapi.domain.Usuario;
import com.nexo.usuarioapi.repository.ConfiguracaoUsuarioRepository;
import com.nexo.usuarioapi.repository.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configurações por usuário, particionadas por seção (espelha o ConfiguracoesService do Angular).
 * O documento completo é a fonte de verdade do servidor; o PATCH faz merge raso por seção.
 */
@Service
public class ConfiguracaoService {

    private final ConfiguracaoUsuarioRepository configuracoes;
    private final UsuarioRepository usuarios;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher eventos;

    public ConfiguracaoService(ConfiguracaoUsuarioRepository configuracoes,
                               UsuarioRepository usuarios,
                               ObjectMapper mapper,
                               ApplicationEventPublisher eventos) {
        this.configuracoes = configuracoes;
        this.usuarios = usuarios;
        this.mapper = mapper;
        this.eventos = eventos;
    }

    @Transactional
    public Map<String, Map<String, Object>> obter(Long usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));

        ConfiguracaoUsuario config = configuracoes.findByUsuarioId(usuarioId)
                .orElseGet(() -> criarPadrao(usuario));

        // Merge com defaults para garantir seções/campos novos em versões futuras
        Map<String, Map<String, Object>> defaults = defaultsPara(usuario.getRole());
        Map<String, Map<String, Object>> salvas = ler(config.getJson());
        salvas.forEach((secao, campos) -> defaults.merge(secao, campos, (d, s) -> {
            d.putAll(s);
            return d;
        }));
        return defaults;
    }

    @Transactional
    public Map<String, Map<String, Object>> atualizarSecao(Long usuarioId, String secao, Map<String, Object> patch) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));

        Map<String, Map<String, Object>> defaults = defaultsPara(usuario.getRole());
        if (!defaults.containsKey(secao)) {
            throw ApiException.badRequest("Seção de configuração desconhecida para o perfil: " + secao);
        }

        ConfiguracaoUsuario config = configuracoes.findByUsuarioId(usuarioId)
                .orElseGet(() -> criarPadrao(usuario));

        Map<String, Map<String, Object>> atual = ler(config.getJson());
        Map<String, Object> campos = atual.computeIfAbsent(secao, s -> new LinkedHashMap<>(defaults.get(secao)));
        campos.putAll(patch);

        validar(usuario.getRole(), secao, campos);

        config.setJson(escrever(atual));
        config.setAtualizadaEm(Instant.now());
        configuracoes.save(config);
        // O chat mostra ou esconde a foto do professor conforme esta escolha.
        if (usuario.getRole() == Role.PROFESSOR && SECAO_PRIVACIDADE.equals(secao)) {
            eventos.publishEvent(new ContaAlterada(usuarioId));
        }
        // O ranking mostra ou esconde o aluno conforme estas escolhas.
        if (usuario.getRole() == Role.ALUNO && SECAO_PRIVACIDADE.equals(secao)) {
            eventos.publishEvent(new PrivacidadeDoAlunoAlterada(usuarioId));
        }
        return obter(usuarioId);
    }

    static final String SECAO_PRIVACIDADE = "privacidade";

    /** Chaves de privacidade que o servidor faz valer, por perfil. */
    private static final Map<Role, List<String>> PRIVACIDADE_APLICADA = Map.of(
            Role.ALUNO, List.of("perfilPublico", "exibirNoRanking"),
            Role.PROFESSOR, List.of("perfilVisivelAlunos"));

    /**
     * "Perfil visível para alunos" do professor. Sem configuração gravada vale o padrão
     * (visível). Só lê: não cria a linha de configuração como o {@link #obter} faz.
     */
    @Transactional(readOnly = true)
    public boolean perfilVisivelParaAlunos(Long usuarioId) {
        return configuracoes.findByUsuarioId(usuarioId)
                .map(c -> ler(c.getJson()).get(SECAO_PRIVACIDADE))
                .map(priv -> !Boolean.FALSE.equals(priv.get("perfilVisivelAlunos")))
                .orElse(true);
    }

    /** Escolhas de privacidade do aluno. Sem configuração gravada valem os padrões (tudo visível). */
    public record PrivacidadeDoAluno(boolean exibirNoRanking, boolean perfilPublico) {}

    /** Só lê: não cria a linha de configuração como o {@link #obter} faz. */
    @Transactional(readOnly = true)
    public PrivacidadeDoAluno privacidadeDoAluno(Long usuarioId) {
        Map<String, Object> priv = configuracoes.findByUsuarioId(usuarioId)
                .map(c -> ler(c.getJson()).get(SECAO_PRIVACIDADE))
                .orElse(Map.of());
        return new PrivacidadeDoAluno(
                !Boolean.FALSE.equals(priv.get("exibirNoRanking")),
                !Boolean.FALSE.equals(priv.get("perfilPublico")));
    }

    /** Validações de negócio reforçadas no servidor (ex.: início < fim na Disponibilidade). */
    private void validar(Role role, String secao, Map<String, Object> campos) {
        // Chave de privacidade que o servidor faz valer só aceita booleano de verdade:
        // "false" (texto) ou null seriam lidos como "ligado" e expõem o usuário sem aviso.
        if (SECAO_PRIVACIDADE.equals(secao)) {
            for (String chave : PRIVACIDADE_APLICADA.getOrDefault(role, List.of())) {
                if (campos.containsKey(chave) && !(campos.get(chave) instanceof Boolean)) {
                    throw ApiException.validation("Valor inválido.",
                            Map.of(chave, "Use verdadeiro ou falso."));
                }
            }
        }
        if ("disponibilidade".equals(secao)) {
            String inicio = String.valueOf(campos.getOrDefault("horarioInicio", "08:00"));
            String fim = String.valueOf(campos.getOrDefault("horarioFim", "18:00"));
            if (inicio.compareTo(fim) >= 0) {
                throw ApiException.validation("Horário inválido.",
                        Map.of("horarioInicio", "O horário de início deve ser anterior ao de fim."));
            }
        }
    }

    private ConfiguracaoUsuario criarPadrao(Usuario usuario) {
        ConfiguracaoUsuario config = new ConfiguracaoUsuario();
        config.setUsuario(usuario);
        config.setJson(escrever(defaultsPara(usuario.getRole())));
        return configuracoes.save(config);
    }

    private Map<String, Map<String, Object>> ler(String json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String escrever(Map<String, Map<String, Object>> dados) {
        try {
            return mapper.writeValueAsString(dados);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Defaults por perfil, conforme configuracao.md (Task 3). */
    static Map<String, Map<String, Object>> defaultsPara(Role role) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        switch (role) {
            case ALUNO -> {
                m.put("notificacoes", secao("avisoTarefasNovas", true, "lembretesPrazos", true,
                        "mensagensProfessores", false, "notificacoesEmail", true));
                m.put("gamificacao", secao("exibirXpTempoReal", true, "animacoesConquista", true,
                        "rankingTurma", true));
                m.put("estudos", secao("modoFoco", false, "autoAvancarTarefas", true,
                        "lembreteEstudoDiario", true));
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
                m.put("acessibilidade", secao("fonteAmpliada", false, "altoContraste", false,
                        "leituraVozAlta", false));
                // visivelResponsaveis saiu: o sistema não tem conta nem dado de responsável,
                // então a chave não tinha o que controlar.
                m.put("privacidade", secao("perfilPublico", true, "exibirNoRanking", true));
            }
            case PROFESSOR -> {
                m.put("notificacoes", secao("novasEntregasAlunos", true, "mensagensAlunosResponsaveis", true,
                        "lembreteCorrecaoPendente", true, "notificacoesEmail", true));
                m.put("avaliacao", secao("sugestaoCorrecaoAutomatica", false, "exibirNotasImediatamente", true,
                        "permitirReenvioAtividade", false));
                Map<String, Object> disponibilidade = new LinkedHashMap<>();
                disponibilidade.put("aceitarContatoForaHorario", false);
                disponibilidade.put("horarioInicio", "08:00");
                disponibilidade.put("horarioFim", "18:00");
                m.put("disponibilidade", disponibilidade);
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
                // exibirContatoResponsaveis saiu pelo mesmo motivo do visivelResponsaveis do
                // aluno: não existe conta de responsável para ver o contato.
                m.put("privacidade", secao("perfilVisivelAlunos", true));
            }
            case DIRETOR -> {
                m.put("notificacoes", secao("novosCadastrosPendentes", true, "relatoriosPendentes", true,
                        "alertasSistema", true, "notificacoesEmail", true));
                Map<String, Object> institucional = new LinkedHashMap<>();
                institucional.put("nomeInstituicao", "Colégio Nexo");
                institucional.put("anoLetivoAtivo", 2026);
                m.put("institucional", institucional);
                m.put("gestao", secao("aprovarProfessoresAutomaticamente", false,
                        "exigirAprovacaoManualAluno", true));
                m.put("integracoes", secao("exportarRelatoriosAutomaticamente", false,
                        "sincronizarCalendarioInstitucional", true));
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
                // Sem seção de privacidade: a única chave (exibirDadosInstituicaoPublicamente)
                // não tinha o que controlar — não há página pública da instituição.
            }
            // Seções do dia a dia de quem administra o sistema — não as de gestão do
            // diretor. O trabalho aqui é conta e catálogo; a seção "documentos" da
            // antiga secretaria saiu junto com o checklist de documentos do aluno.
            case ADMIN -> {
                m.put("notificacoes", secao("novasContasCriadas", true, "contasInativas", true,
                        "conteudoDespublicado", true, "notificacoesEmail", true));
                Map<String, Object> catalogo = new LinkedHashMap<>();
                catalogo.put("publicarConteudoNovoAutomaticamente", true);
                catalogo.put("avisarMateriaComMenosDeNConteudos", 3);
                m.put("catalogo", catalogo);
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
            }
        }
        return m;
    }

    private static Map<String, Object> secao(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
