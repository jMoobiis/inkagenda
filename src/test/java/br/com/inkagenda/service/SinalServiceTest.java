package br.com.inkagenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.model.Cliente;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.FichaAnamnese;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SinalServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Terca-feira, 14h. */
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 14, 0);
    private static final LocalDate HOJE = AGORA.toLocalDate();
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 16, 10, 0);
    private static final String TOKEN = "token-de-agendamento";
    private static final BigDecimal TOTAL = new BigDecimal("1000.00");
    private static final BigDecimal SINAL = new BigDecimal("300.00");

    @Mock
    private SessaoRepository sessoes;
    @Mock
    private SolicitacaoRepository solicitacoes;
    @Mock
    private ConfiguracaoTatuadorRepository configuracoes;
    @Mock
    private AgendaService agenda;
    @Mock
    private AnamneseService anamnese;

    private SinalService service;

    @BeforeEach
    void configurar() {
        Clock relogio = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        service = new SinalService(sessoes, solicitacoes, configuracoes, agenda, anamnese, relogio);
        ConfiguracaoTatuador config = new ConfiguracaoTatuador();
        config.setEnderecoEstudio("Rua Ficticia, 100");
        lenient().when(configuracoes.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        lenient().when(sessoes.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(solicitacoes.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Solicitacao solicitacao(StatusSolicitacao status, String whatsapp) {
        Solicitacao s = new Solicitacao();
        s.setCliente(new Cliente("Cliente Ficticio", whatsapp, AGORA.minusDays(5)));
        s.setStatus(status);
        s.setValorTotal(TOTAL);
        s.setValorSinal(SINAL);
        s.setDuracaoEstimadaHoras(2);
        s.setTokenAgendamento(TOKEN);
        return s;
    }

    private Sessao preReserva() {
        Sessao s = new Sessao();
        s.setId(7L);
        s.setSolicitacao(solicitacao(StatusSolicitacao.AGUARDANDO_SINAL, "16999990000"));
        s.setInicio(INICIO);
        s.setFim(INICIO.plusHours(2));
        s.setStatus(StatusSessao.PRE_RESERVADA);
        s.setExpiraEm(AGORA.plusHours(10));
        lenient().when(sessoes.findById(7L)).thenReturn(Optional.of(s));
        return s;
    }

    private static RegistroSinal registro(String valor, boolean confirmaDivergencia) {
        return new RegistroSinal(HOJE, new BigDecimal(valor), "comprovante.png",
                confirmaDivergencia);
    }

    private Sessao confirmadaEm(LocalDateTime inicio, String whatsapp) {
        Sessao s = new Sessao();
        s.setId(9L);
        s.setSolicitacao(solicitacao(StatusSolicitacao.CONFIRMADA, whatsapp));
        s.setInicio(inicio);
        s.setFim(inicio.plusHours(2));
        s.setStatus(StatusSessao.CONFIRMADA);
        lenient().when(sessoes.findById(9L)).thenReturn(Optional.of(s));
        return s;
    }

    // ---------- escolha de horario (US05, UC02) ----------

    @Test
    void deveCriarPreReservaEAguardarSinalQuandoTokenValido() {
        Solicitacao orcada = solicitacao(StatusSolicitacao.ORCADA, "16999990000");
        Sessao criada = new Sessao();
        when(solicitacoes.findByTokenAgendamento(TOKEN)).thenReturn(Optional.of(orcada));
        when(agenda.criarPreReserva(orcada, INICIO)).thenReturn(criada);

        Sessao sessao = service.escolherHorario(TOKEN, INICIO);

        assertThat(sessao).isSameAs(criada);
        assertThat(orcada.getStatus()).isEqualTo(StatusSolicitacao.AGUARDANDO_SINAL);
        verify(solicitacoes).save(orcada);
    }

    @Test
    void deveRejeitarEscolhaQuandoTokenNaoExiste() {
        when(solicitacoes.findByTokenAgendamento("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.escolherHorario("inexistente", INICIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link de agendamento inválido");
    }

    @Test
    void deveRejeitarEscolhaQuandoTokenNulo() {
        assertThatThrownBy(() -> service.escolherHorario(null, INICIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link de agendamento inválido");
    }

    @Test
    void deveRejeitarEscolhaQuandoTokenEmBranco() {
        assertThatThrownBy(() -> service.escolherHorario("  ", INICIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link de agendamento inválido");
    }

    @Test
    void deveRejeitarEscolhaQuandoSolicitacaoJaAguardaSinal() {
        Solicitacao aguardando = solicitacao(StatusSolicitacao.AGUARDANDO_SINAL, "16999990000");
        when(solicitacoes.findByTokenAgendamento(TOKEN)).thenReturn(Optional.of(aguardando));

        assertThatThrownBy(() -> service.escolherHorario(TOKEN, INICIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não está disponível para agendamento");
        verify(agenda, never()).criarPreReserva(any(), any());
    }

    @Test
    void deveManterOrcadaQuandoAgendaRecusaHorario() {
        Solicitacao orcada = solicitacao(StatusSolicitacao.ORCADA, "16999990000");
        when(solicitacoes.findByTokenAgendamento(TOKEN)).thenReturn(Optional.of(orcada));
        when(agenda.criarPreReserva(orcada, INICIO))
                .thenThrow(new RegraNegocioException("Horario indisponivel"));

        assertThatThrownBy(() -> service.escolherHorario(TOKEN, INICIO))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(orcada.getStatus()).isEqualTo(StatusSolicitacao.ORCADA);
        verify(solicitacoes, never()).save(any());
    }

    // ---------- registro do sinal (US05, RN04) ----------

    @Test
    void deveConfirmarSessaoESolicitacaoQuandoSinalRegistrado() {
        Sessao sessao = preReserva();

        ResultadoSinal resultado = service.registrarSinal(7L, registro("300.00", false));

        assertThat(resultado.sessao().getStatus()).isEqualTo(StatusSessao.CONFIRMADA);
        assertThat(sessao.getSolicitacao().getStatus()).isEqualTo(StatusSolicitacao.CONFIRMADA);
        assertThat(sessao.getExpiraEm()).isNull();
        verify(sessoes).save(sessao);
        verify(solicitacoes).save(sessao.getSolicitacao());
    }

    @Test
    void deveGuardarDataValorEComprovanteQuandoSinalRegistrado() {
        Sessao sessao = preReserva();

        service.registrarSinal(7L, registro("300.00", false));

        assertThat(sessao.getDataPagamento()).isEqualTo(HOJE);
        assertThat(sessao.getValorRecebido()).isEqualByComparingTo("300.00");
        assertThat(sessao.getComprovante()).isEqualTo("comprovante.png");
    }

    @Test
    void deveCalcularValorAAbaterQuandoSinalRegistrado() {
        Sessao sessao = preReserva();

        ResultadoSinal resultado = service.registrarSinal(7L, registro("300.00", false));

        assertThat(resultado.valorAAbater()).isEqualByComparingTo("700.00");
        assertThat(sessao.getValorAAbater()).isEqualByComparingTo("700.00");
    }

    @Test
    void deveAceitarSemComprovanteQuandoComprovanteEhOpcional() {
        Sessao sessao = preReserva();

        service.registrarSinal(7L, new RegistroSinal(HOJE, SINAL, null, false));

        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.CONFIRMADA);
        assertThat(sessao.getComprovante()).isNull();
    }

    @Test
    void deveGerarFichaDeAnamneseQuandoSessaoConfirmada() {
        Sessao sessao = preReserva();
        FichaAnamnese ficha = new FichaAnamnese();
        when(anamnese.gerarFicha(sessao)).thenReturn(ficha);

        ResultadoSinal resultado = service.registrarSinal(7L, registro("300.00", false));

        assertThat(resultado.ficha()).isSameAs(ficha);
    }

    @Test
    void naoDeveConsiderarDivergenteQuandoValorIgualEmEscalaDiferente() {
        preReserva();

        ResultadoSinal resultado = service.registrarSinal(7L, registro("300", false));

        assertThat(resultado.divergente()).isFalse();
    }

    @Test
    void deveAlertarENaoConfirmarQuandoValorDivergeSemConfirmacao() {
        Sessao sessao = preReserva();

        assertThatThrownBy(() -> service.registrarSinal(7L, registro("250.00", false)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("diverge");
        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.PRE_RESERVADA);
        assertThat(sessao.getValorRecebido()).isNull();
        verify(anamnese, never()).gerarFicha(any());
    }

    @Test
    void deveConfirmarQuandoValorDivergeComConfirmacaoExplicita() {
        Sessao sessao = preReserva();

        ResultadoSinal resultado = service.registrarSinal(7L, registro("250.00", true));

        assertThat(resultado.divergente()).isTrue();
        assertThat(resultado.valorAAbater()).isEqualByComparingTo("750.00");
        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.CONFIRMADA);
    }

    @Test
    void deveRejeitarSinalQuandoSemDataDePagamento() {
        Sessao sessao = preReserva();

        assertThatThrownBy(() -> service.registrarSinal(7L,
                new RegistroSinal(null, SINAL, null, false)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("data do pagamento");
        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.PRE_RESERVADA);
    }

    @Test
    void deveRejeitarSinalQuandoDataDePagamentoNoFuturo() {
        preReserva();

        assertThatThrownBy(() -> service.registrarSinal(7L,
                new RegistroSinal(HOJE.plusDays(1), SINAL, null, false)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("data do pagamento");
    }

    @Test
    void deveRejeitarSinalQuandoSemValor() {
        preReserva();

        assertThatThrownBy(() -> service.registrarSinal(7L,
                new RegistroSinal(HOJE, null, null, true)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    void deveRejeitarSinalQuandoValorZero() {
        preReserva();

        assertThatThrownBy(() -> service.registrarSinal(7L, registro("0.00", true)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    void deveRejeitarSinalQuandoValorNegativo() {
        preReserva();

        assertThatThrownBy(() -> service.registrarSinal(7L, registro("-1.00", true)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    void deveRejeitarSinalQuandoValorMaiorQueTotal() {
        preReserva();

        assertThatThrownBy(() -> service.registrarSinal(7L, registro("1000.01", true)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("maior que o valor total");
    }

    @Test
    void deveZerarValorAAbaterQuandoValorIgualAoTotal() {
        preReserva();

        ResultadoSinal resultado = service.registrarSinal(7L, registro("1000.00", true));

        assertThat(resultado.valorAAbater()).isEqualByComparingTo("0.00");
    }

    @Test
    void deveRejeitarSinalQuandoSessaoNaoExiste() {
        when(sessoes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarSinal(99L, registro("300.00", false)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Sessão não encontrada");
    }

    @Test
    void deveRejeitarSinalQuandoSessaoJaConfirmada() {
        Sessao sessao = preReserva();
        sessao.setStatus(StatusSessao.CONFIRMADA);

        assertThatThrownBy(() -> service.registrarSinal(7L, registro("300.00", false)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não aguarda sinal");
    }

    @Test
    void deveRejeitarSinalQuandoPrazoDaPreReservaVenceu() {
        Sessao sessao = preReserva();
        sessao.setExpiraEm(AGORA.minusMinutes(1));

        assertThatThrownBy(() -> service.registrarSinal(7L, registro("300.00", false)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("prazo da pré-reserva");
        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.PRE_RESERVADA);
    }

    @Test
    void deveAceitarSinalQuandoPrazoVenceExatamenteAgora() {
        Sessao sessao = preReserva();
        sessao.setExpiraEm(AGORA);

        service.registrarSinal(7L, registro("300.00", false));

        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.CONFIRMADA);
    }

    // ---------- expiracao da pre-reserva (RN06) ----------

    @Test
    void deveExpirarSessaoEDevolverParaOrcadaQuandoPrazoVencido() {
        Sessao sessao = preReserva();
        sessao.setExpiraEm(AGORA.minusHours(1));
        when(sessoes.findByStatusAndExpiraEmBefore(StatusSessao.PRE_RESERVADA, AGORA))
                .thenReturn(List.of(sessao));

        int expiradas = service.expirarPreReservasVencidas();

        assertThat(expiradas).isEqualTo(1);
        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.EXPIRADA);
        assertThat(sessao.getStatus().ocupaAgenda()).isFalse();
        assertThat(sessao.getSolicitacao().getStatus()).isEqualTo(StatusSolicitacao.ORCADA);
        verify(sessoes).save(sessao);
        verify(solicitacoes).save(sessao.getSolicitacao());
    }

    @Test
    void deveExpirarTodasQuandoHaVariasVencidas() {
        Sessao a = preReserva();
        Sessao b = preReserva();
        when(sessoes.findByStatusAndExpiraEmBefore(StatusSessao.PRE_RESERVADA, AGORA))
                .thenReturn(List.of(a, b));

        assertThat(service.expirarPreReservasVencidas()).isEqualTo(2);
    }

    @Test
    void naoDeveAlterarNadaQuandoNaoHaPreReservasVencidas() {
        when(sessoes.findByStatusAndExpiraEmBefore(StatusSessao.PRE_RESERVADA, AGORA))
                .thenReturn(List.of());

        assertThat(service.expirarPreReservasVencidas()).isZero();
        verify(sessoes, never()).save(any());
    }

    // ---------- confirmacao de vespera (US06, UC05) ----------

    @Test
    void deveListarSessoesConfirmadasDeAmanhaQuandoConsultarVespera() {
        Sessao amanha = confirmadaEm(HOJE.plusDays(1).atTime(10, 0), "16999990000");
        when(sessoes.findByStatusAndInicioBetweenOrderByInicio(eq(StatusSessao.CONFIRMADA),
                eq(HOJE.plusDays(1).atStartOfDay()), eq(HOJE.plusDays(1).atTime(LocalTime.MAX))))
                .thenReturn(List.of(amanha));

        assertThat(service.sessoesDeAmanha()).containsExactly(amanha);
    }

    @Test
    void deveMontarLinkComDataHorarioEnderecoECuidadosQuandoTelefoneValido() {
        Sessao sessao = confirmadaEm(LocalDateTime.of(2026, 11, 11, 15, 30), "16999990000");

        LembreteVespera lembrete = service.lembreteVespera(sessao);

        assertThat(lembrete.podeEnviar()).isTrue();
        assertThat(lembrete.aviso()).isNull();
        assertThat(lembrete.linkWhatsApp()).startsWith("https://wa.me/5516999990000?text=");
        String mensagem = URLDecoder.decode(lembrete.linkWhatsApp(), StandardCharsets.UTF_8);
        assertThat(mensagem).contains("11/11/2026", "15:30", "Rua Ficticia, 100", "Cuidados");
    }

    @Test
    void deveDesabilitarLembreteEAvisarQuandoTelefoneInvalido() {
        Sessao sessao = confirmadaEm(HOJE.plusDays(1).atTime(10, 0), "123");

        LembreteVespera lembrete = service.lembreteVespera(sessao);

        assertThat(lembrete.podeEnviar()).isFalse();
        assertThat(lembrete.linkWhatsApp()).isNull();
        assertThat(lembrete.aviso()).contains("Corrija o cadastro");
    }

    @Test
    void deveRejeitarLembreteQuandoSessaoNaoConfirmada() {
        Sessao sessao = preReserva();

        assertThatThrownBy(() -> service.lembreteVespera(sessao))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não está confirmada");
    }

    @Test
    void deveRegistrarPresencaQuandoSessaoConfirmada() {
        Sessao sessao = confirmadaEm(HOJE.plusDays(1).atTime(10, 0), "16999990000");

        service.confirmarPresenca(9L);

        assertThat(sessao.isPresencaConfirmada()).isTrue();
        verify(sessoes).save(sessao);
    }

    @Test
    void deveRejeitarPresencaQuandoSessaoNaoConfirmada() {
        Sessao sessao = preReserva();

        assertThatThrownBy(() -> service.confirmarPresenca(7L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não está confirmada");
        assertThat(sessao.isPresencaConfirmada()).isFalse();
    }

    @Test
    void deveRejeitarPresencaQuandoSessaoNaoExiste() {
        when(sessoes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmarPresenca(99L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Sessão não encontrada");
    }
}
