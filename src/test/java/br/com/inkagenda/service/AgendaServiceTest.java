package br.com.inkagenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.model.BloqueioAgenda;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.BloqueioAgendaRepository;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Terca-feira, 14h. */
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 14, 0);
    /** Segunda-feira, dia de atendimento. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 16);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 11, 15);

    @Mock
    private SessaoRepository sessoes;
    @Mock
    private BloqueioAgendaRepository bloqueios;
    @Mock
    private ConfiguracaoTatuadorRepository configuracoes;

    private AgendaService service;

    @BeforeEach
    void configurar() {
        Clock relogio = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        service = new AgendaService(sessoes, bloqueios, configuracoes, relogio);
        lenient().when(configuracoes.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(new ConfiguracaoTatuador()));
        lenient().when(sessoes.findByStatusInAndInicioBeforeAndFimAfter(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(bloqueios.findByInicioBeforeAndFimAfter(any(), any())).thenReturn(List.of());
        lenient().when(sessoes.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static LocalDateTime em(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto);
    }

    private static Sessao sessao(Long id, LocalDateTime inicio, LocalDateTime fim) {
        Sessao s = new Sessao();
        s.setId(id);
        s.setInicio(inicio);
        s.setFim(fim);
        s.setStatus(StatusSessao.CONFIRMADA);
        return s;
    }

    private static Solicitacao solicitacaoComDuracao(Integer horas) {
        Solicitacao s = new Solicitacao();
        s.setDuracaoEstimadaHoras(horas);
        return s;
    }

    private void existente(Sessao... sessoesExistentes) {
        lenient().when(sessoes.findByStatusInAndInicioBeforeAndFimAfter(any(), any(), any()))
                .thenReturn(List.of(sessoesExistentes));
    }

    // ---------- disponibilidade (US04, RN07, RN09) ----------

    @Test
    void deveOferecerTodosOsHorariosCabiveisQuandoDiaEstaLivre() {
        List<LocalDateTime> slots = service.horariosDisponiveis(SEGUNDA, 2);

        assertThat(slots).first().isEqualTo(em(SEGUNDA, 10, 0));
        assertThat(slots).last().isEqualTo(em(SEGUNDA, 16, 0));
        assertThat(slots).hasSize(13);
    }

    @Test
    void deveBloquearHorariosPorDuracaoMaisIntervaloQuandoHaSessaoNoDia() {
        existente(sessao(1L, em(SEGUNDA, 12, 0), em(SEGUNDA, 14, 0)));

        List<LocalDateTime> slots = service.horariosDisponiveis(SEGUNDA, 2);

        assertThat(slots).containsExactly(em(SEGUNDA, 14, 30), em(SEGUNDA, 15, 0),
                em(SEGUNDA, 15, 30), em(SEGUNDA, 16, 0));
    }

    @Test
    void deveRespeitarIntervaloDeLimpezaConfiguradoQuandoEleForMaior() {
        ConfiguracaoTatuador config = new ConfiguracaoTatuador();
        config.setIntervaloLimpezaMinutos(60);
        lenient().when(configuracoes.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        existente(sessao(1L, em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0)));

        List<LocalDateTime> slots = service.horariosDisponiveis(SEGUNDA, 2);

        assertThat(slots).first().isEqualTo(em(SEGUNDA, 13, 0));
    }

    @Test
    void deveRetornarVazioQuandoDiaNaoTemAtendimento() {
        assertThat(service.horariosDisponiveis(DOMINGO, 2)).isEmpty();
    }

    @Test
    void deveRetornarVazioQuandoDuracaoExcedeOExpediente() {
        assertThat(service.horariosDisponiveis(SEGUNDA, 9)).isEmpty();
    }

    @Test
    void deveOferecerUnicoHorarioQuandoDuracaoOcupaTodoOExpediente() {
        assertThat(service.horariosDisponiveis(SEGUNDA, 8)).containsExactly(em(SEGUNDA, 10, 0));
    }

    @Test
    void deveBloquearHorariosQuandoHaBloqueioPessoal() {
        lenient().when(bloqueios.findByInicioBeforeAndFimAfter(any(), any()))
                .thenReturn(List.of(new BloqueioAgenda(em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0),
                        "Consulta")));

        List<LocalDateTime> slots = service.horariosDisponiveis(SEGUNDA, 2);

        assertThat(slots).first().isEqualTo(em(SEGUNDA, 12, 0));
        assertThat(slots).last().isEqualTo(em(SEGUNDA, 16, 0));
    }

    @Test
    void deveOferecerSomenteHorariosFuturosQuandoDiaEHoje() {
        LocalDate hoje = AGORA.toLocalDate();

        List<LocalDateTime> slots = service.horariosDisponiveis(hoje, 2);

        assertThat(slots).containsExactly(em(hoje, 14, 30), em(hoje, 15, 0), em(hoje, 15, 30),
                em(hoje, 16, 0));
    }

    @Test
    void deveRejeitarDuracaoNaoPositivaNaDisponibilidade() {
        assertThatThrownBy(() -> service.horariosDisponiveis(SEGUNDA, 0))
                .isInstanceOf(RegraNegocioException.class);
    }

    // ---------- criacao da pre-reserva (US04, RN07, RN09) ----------

    @Test
    void devePreReservarSessaoComFimEPrazoQuandoHorarioEstaLivre() {
        Sessao criada = service.criarPreReserva(solicitacaoComDuracao(3), em(SEGUNDA, 11, 0));

        assertThat(criada.getInicio()).isEqualTo(em(SEGUNDA, 11, 0));
        assertThat(criada.getFim()).isEqualTo(em(SEGUNDA, 14, 0));
        assertThat(criada.getStatus()).isEqualTo(StatusSessao.PRE_RESERVADA);
        assertThat(criada.getExpiraEm()).isEqualTo(AGORA.plusHours(24));
        verify(sessoes).save(criada);
    }

    @Test
    void deveAceitarSessaoQueTerminaExatamenteNoFimDoExpediente() {
        Sessao criada = service.criarPreReserva(solicitacaoComDuracao(2), em(SEGUNDA, 16, 0));

        assertThat(criada.getFim()).isEqualTo(em(SEGUNDA, 18, 0));
    }

    @Test
    void deveRecusarSessaoQueSobrepoeOutraConfirmada() {
        existente(sessao(1L, em(SEGUNDA, 12, 0), em(SEGUNDA, 14, 0)));

        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(2),
                em(SEGUNDA, 13, 0))).isInstanceOf(RegraNegocioException.class);
        verify(sessoes, never()).save(any());
    }

    @Test
    void deveRecusarSessaoQueNaoRespeitaIntervaloDeLimpezaDepoisDeOutra() {
        existente(sessao(1L, em(SEGUNDA, 12, 0), em(SEGUNDA, 14, 0)));

        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(2),
                em(SEGUNDA, 14, 0))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void deveRecusarSessaoQueNaoRespeitaIntervaloDeLimpezaAntesDeOutra() {
        existente(sessao(1L, em(SEGUNDA, 14, 0), em(SEGUNDA, 16, 0)));

        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(3),
                em(SEGUNDA, 11, 0))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void deveAceitarSessaoQueComecaExatamenteAposOIntervaloDeLimpeza() {
        existente(sessao(1L, em(SEGUNDA, 12, 0), em(SEGUNDA, 14, 0)));

        Sessao criada = service.criarPreReserva(solicitacaoComDuracao(2), em(SEGUNDA, 14, 30));

        assertThat(criada.getInicio()).isEqualTo(em(SEGUNDA, 14, 30));
    }

    @Test
    void deveRecusarSessaoQueUltrapassaOFimDoExpediente() {
        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(2),
                em(SEGUNDA, 16, 30))).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("expediente");
        verify(sessoes, never()).save(any());
    }

    @Test
    void deveRecusarSessaoQueComecaAntesDaAberturaDoExpediente() {
        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(2),
                em(SEGUNDA, 9, 0))).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("expediente");
    }

    @Test
    void deveRecusarSessaoEmDiaSemAtendimento() {
        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(2),
                em(DOMINGO, 11, 0))).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("atendimento");
    }

    @Test
    void deveRecusarSessaoQueColideComBloqueioPessoal() {
        lenient().when(bloqueios.findByInicioBeforeAndFimAfter(any(), any()))
                .thenReturn(List.of(new BloqueioAgenda(em(SEGUNDA, 12, 0), em(SEGUNDA, 13, 0),
                        "Medico")));

        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(2),
                em(SEGUNDA, 11, 0))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void deveRecusarSessaoNoPassado() {
        LocalDate hoje = AGORA.toLocalDate();

        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(1),
                em(hoje, 11, 0))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void deveRecusarPreReservaQuandoSolicitacaoNaoTemDuracaoEstimada() {
        assertThatThrownBy(() -> service.criarPreReserva(solicitacaoComDuracao(null),
                em(SEGUNDA, 11, 0))).isInstanceOf(RegraNegocioException.class);
    }

    // ---------- remarcacao e cancelamento (US08, RN08) ----------

    @Test
    void deveRemarcarMantendoSinalQuandoHaAntecedenciaSuficiente() {
        Sessao atual = sessao(1L, em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0));

        ResultadoAlteracao resultado = service.remarcar(atual, em(SEGUNDA.plusDays(1), 14, 0),
                false);

        assertThat(resultado.perdeuSinal()).isFalse();
        assertThat(atual.getInicio()).isEqualTo(em(SEGUNDA.plusDays(1), 14, 0));
        assertThat(atual.getFim()).isEqualTo(em(SEGUNDA.plusDays(1), 16, 0));
        assertThat(atual.getStatus()).isEqualTo(StatusSessao.CONFIRMADA);
        verify(sessoes).save(atual);
    }

    @Test
    void deveExigirConfirmacaoExplicitaQuandoRemarcacaoTemMenosDe48Horas() {
        LocalDateTime inicio = AGORA.plusHours(20);
        Sessao atual = sessao(1L, inicio, inicio.plusHours(2));

        assertThatThrownBy(() -> service.remarcar(atual, em(SEGUNDA, 10, 0), false))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("sinal");
        assertThat(atual.getInicio()).isEqualTo(inicio);
        verify(sessoes, never()).save(any());
    }

    @Test
    void deveRemarcarComPerdaDeSinalQuandoMenosDe48HorasECOnfirmacaoExplicita() {
        LocalDateTime inicio = AGORA.plusHours(20);
        Sessao atual = sessao(1L, inicio, inicio.plusHours(2));

        ResultadoAlteracao resultado = service.remarcar(atual, em(SEGUNDA, 10, 0), true);

        assertThat(resultado.perdeuSinal()).isTrue();
        assertThat(atual.getInicio()).isEqualTo(em(SEGUNDA, 10, 0));
    }

    @Test
    void deveManterSinalQuandoFaltamExatamente48Horas() {
        LocalDateTime inicio = AGORA.plusHours(48);
        Sessao atual = sessao(1L, inicio, inicio.plusHours(2));

        ResultadoAlteracao resultado = service.remarcar(atual, em(SEGUNDA, 10, 0), false);

        assertThat(resultado.perdeuSinal()).isFalse();
    }

    @Test
    void deveExigirConfirmacaoQuandoFaltamUmMinutoParaCompletar48Horas() {
        LocalDateTime inicio = AGORA.plusHours(48).minusMinutes(1);
        Sessao atual = sessao(1L, inicio, inicio.plusHours(2));

        assertThatThrownBy(() -> service.remarcar(atual, em(SEGUNDA, 10, 0), false))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void deveRecusarRemarcacaoParaHorarioOcupado() {
        Sessao atual = sessao(1L, em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0));
        Sessao outra = sessao(2L, em(SEGUNDA.plusDays(1), 14, 0), em(SEGUNDA.plusDays(1), 16, 0));
        existente(outra);

        assertThatThrownBy(() -> service.remarcar(atual, em(SEGUNDA.plusDays(1), 15, 0), false))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(atual.getInicio()).isEqualTo(em(SEGUNDA, 10, 0));
        verify(sessoes, never()).save(any());
    }

    @Test
    void devePermitirRemarcarParaHorarioQueSobrepoeASessaoOriginal() {
        Sessao atual = sessao(1L, em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0));
        existente(atual);

        service.remarcar(atual, em(SEGUNDA, 11, 0), false);

        assertThat(atual.getInicio()).isEqualTo(em(SEGUNDA, 11, 0));
    }

    @Test
    void deveRecusarRemarcacaoQuandoNovoHorarioUltrapassaExpediente() {
        Sessao atual = sessao(1L, em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0));

        assertThatThrownBy(() -> service.remarcar(atual, em(SEGUNDA.plusDays(1), 17, 0), false))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void deveRecusarRemarcacaoDeSessaoCancelada() {
        Sessao atual = sessao(1L, em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0));
        atual.setStatus(StatusSessao.CANCELADA);

        assertThatThrownBy(() -> service.remarcar(atual, em(SEGUNDA.plusDays(1), 10, 0), false))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void deveCancelarLiberandoHorarioQuandoHaAntecedenciaSuficiente() {
        Sessao atual = sessao(1L, em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0));

        ResultadoAlteracao resultado = service.cancelar(atual, false);

        assertThat(resultado.perdeuSinal()).isFalse();
        assertThat(atual.getStatus()).isEqualTo(StatusSessao.CANCELADA);
        verify(sessoes).save(atual);
    }

    @Test
    void deveExigirConfirmacaoExplicitaQuandoCancelamentoTemMenosDe48Horas() {
        LocalDateTime inicio = AGORA.plusHours(10);
        Sessao atual = sessao(1L, inicio, inicio.plusHours(2));

        assertThatThrownBy(() -> service.cancelar(atual, false))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(atual.getStatus()).isEqualTo(StatusSessao.CONFIRMADA);
        verify(sessoes, never()).save(any());
    }

    @Test
    void deveCancelarComPerdaDeSinalQuandoMenosDe48HorasEConfirmacaoExplicita() {
        LocalDateTime inicio = AGORA.plusHours(10);
        Sessao atual = sessao(1L, inicio, inicio.plusHours(2));

        ResultadoAlteracao resultado = service.cancelar(atual, true);

        assertThat(resultado.perdeuSinal()).isTrue();
        assertThat(atual.getStatus()).isEqualTo(StatusSessao.CANCELADA);
    }

    @Test
    void deveRecusarCancelamentoDeSessaoJaCancelada() {
        Sessao atual = sessao(1L, em(SEGUNDA, 10, 0), em(SEGUNDA, 12, 0));
        atual.setStatus(StatusSessao.CANCELADA);

        assertThatThrownBy(() -> service.cancelar(atual, true))
                .isInstanceOf(RegraNegocioException.class);
    }
}
