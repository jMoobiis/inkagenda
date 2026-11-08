package br.com.inkagenda.service;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.model.BloqueioAgenda;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.BloqueioAgendaRepository;
import br.com.inkagenda.domain.repository.ImagemReferenciaRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas e acoes do painel do tatuador, sem detalhes de HTTP. */
@Service
public class PainelService {

    private final SolicitacaoRepository solicitacoes;
    private final SessaoRepository sessoes;
    private final ImagemReferenciaRepository imagens;
    private final BloqueioAgendaRepository bloqueios;
    private final OrcamentoService orcamento;
    private final AgendaService agenda;
    private final ArmazenamentoArquivos armazenamento;

    public PainelService(SolicitacaoRepository solicitacoes, SessaoRepository sessoes,
            ImagemReferenciaRepository imagens, BloqueioAgendaRepository bloqueios,
            OrcamentoService orcamento, AgendaService agenda, ArmazenamentoArquivos armazenamento) {
        this.solicitacoes = solicitacoes;
        this.sessoes = sessoes;
        this.imagens = imagens;
        this.bloqueios = bloqueios;
        this.orcamento = orcamento;
        this.agenda = agenda;
        this.armazenamento = armazenamento;
    }

    /** Solicitacoes por status, com o alerta "Sem retorno" (US02, CA2). */
    @Transactional(readOnly = true)
    public Map<StatusSolicitacao, List<ItemPainel>> painel() {
        Map<StatusSolicitacao, List<ItemPainel>> porStatus = new EnumMap<>(StatusSolicitacao.class);
        for (StatusSolicitacao status : StatusSolicitacao.values()) {
            porStatus.put(status, solicitacoes.findByStatusOrderByCriadaEmDesc(status).stream()
                    .map(s -> new ItemPainel(s, orcamento.estaSemRetorno(s)))
                    .toList());
        }
        return porStatus;
    }

    /** Solicitacao com as imagens ja carregadas, para a tela de detalhe. */
    @Transactional(readOnly = true)
    public Solicitacao solicitacao(Long id) {
        Solicitacao solicitacao = solicitacoes.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada"));
        solicitacao.getImagens().size();
        return solicitacao;
    }

    @Transactional(readOnly = true)
    public String caminhoImagem(Long solicitacaoId, Long imagemId) {
        return imagens.findByIdAndSolicitacaoId(imagemId, solicitacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imagem não encontrada"))
                .getCaminho();
    }

    @Transactional(readOnly = true)
    public Sessao sessao(Long id) {
        return sessoes.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão não encontrada"));
    }

    /** Sessao que ocupa a agenda para a solicitacao, se houver. */
    @Transactional(readOnly = true)
    public Optional<Sessao> sessaoAtiva(Long solicitacaoId) {
        return sessoes.findFirstBySolicitacaoIdAndStatusInOrderByIdDesc(solicitacaoId,
                EnumSet.of(StatusSessao.PRE_RESERVADA, StatusSessao.CONFIRMADA));
    }

    @Transactional(readOnly = true)
    public String caminhoComprovante(Long sessaoId) {
        String comprovante = sessao(sessaoId).getComprovante();
        if (comprovante == null) {
            throw new RecursoNaoEncontradoException("Comprovante não encontrado");
        }
        return comprovante;
    }

    /** Valida e guarda o comprovante; o nome enviado nunca vira caminho. */
    public String salvarComprovante(ArquivoEnviado arquivo) {
        ValidadorArquivoReferencia.validar(arquivo);
        return armazenamento.salvar(arquivo);
    }

    @Transactional(readOnly = true)
    public AgendaDia agendaDoDia(LocalDate dia) {
        LocalDateTime inicio = dia.atStartOfDay();
        LocalDateTime fim = dia.plusDays(1).atStartOfDay();
        List<Sessao> doDia = sessoes.findByStatusInAndInicioBeforeAndFimAfter(
                EnumSet.of(StatusSessao.PRE_RESERVADA, StatusSessao.CONFIRMADA), fim, inicio)
                .stream().sorted(Comparator.comparing(Sessao::getInicio)).toList();
        List<BloqueioAgenda> bloqueiosDoDia = bloqueios.findByInicioBeforeAndFimAfter(fim, inicio)
                .stream().sorted(Comparator.comparing(BloqueioAgenda::getInicio)).toList();
        return new AgendaDia(dia, doDia, bloqueiosDoDia);
    }

    @Transactional
    public BloqueioAgenda criarBloqueio(LocalDateTime inicio, LocalDateTime fim, String descricao) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraNegocioException("O fim do bloqueio deve ser depois do início");
        }
        return bloqueios.save(new BloqueioAgenda(inicio, fim, descricao));
    }

    @Transactional
    public void removerBloqueio(Long id) {
        BloqueioAgenda bloqueio = bloqueios.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio não encontrado"));
        bloqueios.delete(bloqueio);
    }

    @Transactional
    public ResultadoAlteracao remarcarSessao(Long id, LocalDateTime novoInicio,
            boolean confirmacaoPerdaSinal) {
        return agenda.remarcar(sessao(id), novoInicio, confirmacaoPerdaSinal);
    }

    /**
     * Cancela e libera o horario. Com o sinal mantido, a solicitacao volta para orcada e o cliente
     * pode escolher outro horario; com o sinal perdido, ela vira perdida (RN08).
     */
    @Transactional
    public ResultadoAlteracao cancelarSessao(Long id, boolean confirmacaoPerdaSinal) {
        ResultadoAlteracao resultado = agenda.cancelar(sessao(id), confirmacaoPerdaSinal);
        Solicitacao solicitacao = resultado.sessao().getSolicitacao();
        solicitacao.setStatus(resultado.perdeuSinal() ? StatusSolicitacao.PERDIDA
                : StatusSolicitacao.ORCADA);
        solicitacoes.save(solicitacao);
        return resultado;
    }
}
