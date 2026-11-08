package br.com.inkagenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PainelServiceTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 16, 10, 0);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};

    @Mock
    private SolicitacaoRepository solicitacoes;
    @Mock
    private SessaoRepository sessoes;
    @Mock
    private ImagemReferenciaRepository imagens;
    @Mock
    private BloqueioAgendaRepository bloqueios;
    @Mock
    private OrcamentoService orcamento;
    @Mock
    private AgendaService agenda;
    @Mock
    private ArmazenamentoArquivos armazenamento;
    @InjectMocks
    private PainelService service;

    @BeforeEach
    void configurar() {
        lenient().when(solicitacoes.findByStatusOrderByCriadaEmDesc(any())).thenReturn(List.of());
    }

    private static Sessao sessaoComSolicitacao(StatusSolicitacao status) {
        Solicitacao solicitacao = new Solicitacao();
        solicitacao.setStatus(status);
        Sessao sessao = new Sessao();
        sessao.setSolicitacao(solicitacao);
        sessao.setStatus(StatusSessao.CANCELADA);
        sessao.setInicio(INICIO);
        return sessao;
    }

    @Test
    void deveMarcarSemRetornoQuandoOrcamentoEstaParado() {
        Solicitacao parada = new Solicitacao();
        when(solicitacoes.findByStatusOrderByCriadaEmDesc(StatusSolicitacao.ORCADA))
                .thenReturn(List.of(parada));
        when(orcamento.estaSemRetorno(parada)).thenReturn(true);

        List<ItemPainel> itens = service.painel().get(StatusSolicitacao.ORCADA);

        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).semRetorno()).isTrue();
    }

    @Test
    void deveTrazerTodosOsStatusQuandoNaoHaSolicitacoes() {
        assertThat(service.painel()).containsOnlyKeys(StatusSolicitacao.values());
    }

    @Test
    void deveRejeitarQuandoSolicitacaoNaoExiste() {
        when(solicitacoes.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.solicitacao(9L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveRejeitarQuandoImagemNaoPertenceASolicitacao() {
        when(imagens.findByIdAndSolicitacaoId(5L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.caminhoImagem(9L, 5L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveRejeitarQuandoSessaoNaoTemComprovante() {
        when(sessoes.findById(1L)).thenReturn(Optional.of(new Sessao()));

        assertThatThrownBy(() -> service.caminhoComprovante(1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveSalvarComprovanteQuandoArquivoValido() {
        ArquivoEnviado arquivo = new ArquivoEnviado("pix.png", PNG);
        when(armazenamento.salvar(arquivo)).thenReturn("gerado.png");

        assertThat(service.salvarComprovante(arquivo)).isEqualTo("gerado.png");
    }

    @Test
    void deveRejeitarComprovanteQuandoFormatoInvalido() {
        ArquivoEnviado arquivo = new ArquivoEnviado("pix.exe", new byte[] {1, 2});

        assertThatThrownBy(() -> service.salvarComprovante(arquivo))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("JPG ou PNG");
        verify(armazenamento, never()).salvar(any());
    }

    @Test
    void deveOrdenarAgendaDoDiaQuandoHaSessoesEBloqueios() {
        Sessao tarde = sessaoComSolicitacao(StatusSolicitacao.CONFIRMADA);
        tarde.setInicio(INICIO.plusHours(4));
        Sessao manha = sessaoComSolicitacao(StatusSolicitacao.CONFIRMADA);
        when(sessoes.findByStatusInAndInicioBeforeAndFimAfter(any(), any(), any()))
                .thenReturn(List.of(tarde, manha));
        when(bloqueios.findByInicioBeforeAndFimAfter(any(), any())).thenReturn(List.of());

        AgendaDia agendaDia = service.agendaDoDia(LocalDate.of(2026, 11, 16));

        assertThat(agendaDia.sessoes()).containsExactly(manha, tarde);
    }

    @Test
    void deveCriarBloqueioQuandoIntervaloValido() {
        when(bloqueios.save(any())).thenAnswer(i -> i.getArgument(0));

        BloqueioAgenda bloqueio = service.criarBloqueio(INICIO, INICIO.plusHours(2), "Pessoal");

        assertThat(bloqueio.getDescricao()).isEqualTo("Pessoal");
    }

    @Test
    void deveRejeitarBloqueioQuandoFimNaoEDepoisDoInicio() {
        assertThatThrownBy(() -> service.criarBloqueio(INICIO, INICIO, "Pessoal"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("depois do início");
        verify(bloqueios, never()).save(any());
    }

    @Test
    void deveRemoverBloqueioQuandoExiste() {
        BloqueioAgenda bloqueio = new BloqueioAgenda(INICIO, INICIO.plusHours(1), "Pessoal");
        when(bloqueios.findById(3L)).thenReturn(Optional.of(bloqueio));

        service.removerBloqueio(3L);

        verify(bloqueios).delete(bloqueio);
    }

    @Test
    void deveRejeitarRemocaoQuandoBloqueioNaoExiste() {
        when(bloqueios.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removerBloqueio(3L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDevolverParaOrcadaQuandoCancelaMantendoSinal() {
        Sessao sessao = sessaoComSolicitacao(StatusSolicitacao.CONFIRMADA);
        when(sessoes.findById(1L)).thenReturn(Optional.of(sessao));
        when(agenda.cancelar(sessao, false)).thenReturn(new ResultadoAlteracao(sessao, false));

        service.cancelarSessao(1L, false);

        assertThat(sessao.getSolicitacao().getStatus()).isEqualTo(StatusSolicitacao.ORCADA);
        verify(solicitacoes).save(sessao.getSolicitacao());
    }

    @Test
    void deveMarcarComoPerdidaQuandoCancelaPerdendoSinal() {
        Sessao sessao = sessaoComSolicitacao(StatusSolicitacao.CONFIRMADA);
        when(sessoes.findById(1L)).thenReturn(Optional.of(sessao));
        when(agenda.cancelar(sessao, true)).thenReturn(new ResultadoAlteracao(sessao, true));

        service.cancelarSessao(1L, true);

        assertThat(sessao.getSolicitacao().getStatus()).isEqualTo(StatusSolicitacao.PERDIDA);
    }

    @Test
    void naoDeveAlterarSolicitacaoQuandoAgendaRecusaCancelamento() {
        Sessao sessao = sessaoComSolicitacao(StatusSolicitacao.CONFIRMADA);
        when(sessoes.findById(1L)).thenReturn(Optional.of(sessao));
        when(agenda.cancelar(sessao, false)).thenThrow(new RegraNegocioException("Sem antecedencia"));

        assertThatThrownBy(() -> service.cancelarSessao(1L, false))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(sessao.getSolicitacao().getStatus()).isEqualTo(StatusSolicitacao.CONFIRMADA);
        verify(solicitacoes, never()).save(any());
    }
}
