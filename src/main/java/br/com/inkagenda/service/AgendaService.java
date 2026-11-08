package br.com.inkagenda.service;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.model.BloqueioAgenda;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.IntervaloHorario;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.BloqueioAgendaRepository;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgendaService {

    private static final int PASSO_MINUTOS = 30;

    private final SessaoRepository sessoes;
    private final BloqueioAgendaRepository bloqueios;
    private final ConfiguracaoTatuadorRepository configuracoes;
    private final Clock relogio;

    public AgendaService(SessaoRepository sessoes, BloqueioAgendaRepository bloqueios,
            ConfiguracaoTatuadorRepository configuracoes, Clock relogio) {
        this.sessoes = sessoes;
        this.bloqueios = bloqueios;
        this.configuracoes = configuracoes;
        this.relogio = relogio;
    }

    /** Horarios de inicio livres no dia para uma sessao de {@code duracaoHoras} (US04). */
    @Transactional(readOnly = true)
    public List<LocalDateTime> horariosDisponiveis(LocalDate dia, int duracaoHoras) {
        if (duracaoHoras <= 0) {
            throw new RegraNegocioException("A duração da sessão deve ser maior que zero");
        }
        ConfiguracaoTatuador config = configuracao();
        List<LocalDateTime> livres = new ArrayList<>();
        if (!config.getDiasAtendimento().contains(dia.getDayOfWeek())) {
            return livres;
        }
        Duration duracao = Duration.ofHours(duracaoHoras);
        LocalDateTime fimExpediente = dia.atTime(config.getFimExpediente());
        for (LocalDateTime inicio = dia.atTime(config.getAberturaExpediente());
                !inicio.plus(duracao).isAfter(fimExpediente);
                inicio = inicio.plusMinutes(PASSO_MINUTOS)) {
            if (inicio.isAfter(agora())
                    && semConflito(inicio, inicio.plus(duracao), config, null)) {
                livres.add(inicio);
            }
        }
        return livres;
    }

    /** Cria a pre-reserva de uma solicitacao no horario escolhido (US04, RN07, RN09). */
    @Transactional
    public Sessao criarPreReserva(Solicitacao solicitacao, LocalDateTime inicio) {
        Integer horas = solicitacao.getDuracaoEstimadaHoras();
        if (horas == null || horas <= 0) {
            throw new RegraNegocioException("A solicitação não tem duração estimada");
        }
        ConfiguracaoTatuador config = configuracao();
        LocalDateTime fim = inicio.plusHours(horas);
        validarHorario(inicio, fim, config, null);

        Sessao sessao = new Sessao();
        sessao.setSolicitacao(solicitacao);
        sessao.setInicio(inicio);
        sessao.setFim(fim);
        sessao.setStatus(StatusSessao.PRE_RESERVADA);
        sessao.setExpiraEm(agora().plusHours(config.getPrazoPreReservaHoras()));
        return sessoes.save(sessao);
    }

    /** Remarca a sessao mantendo a duracao (US08, RN08). */
    @Transactional
    public ResultadoAlteracao remarcar(Sessao sessao, LocalDateTime novoInicio,
            boolean confirmacaoExplicitaPerdaSinal) {
        exigirSessaoAtiva(sessao);
        ConfiguracaoTatuador config = configuracao();
        boolean perdeuSinal = exigirConfirmacaoSeForaDaAntecedencia(sessao, config,
                confirmacaoExplicitaPerdaSinal);
        LocalDateTime novoFim = novoInicio.plus(Duration.between(sessao.getInicio(),
                sessao.getFim()));
        validarHorario(novoInicio, novoFim, config, sessao);

        sessao.setInicio(novoInicio);
        sessao.setFim(novoFim);
        sessoes.save(sessao);
        return new ResultadoAlteracao(sessao, perdeuSinal);
    }

    /** Cancela a sessao, liberando o horario (US08, RN08). */
    @Transactional
    public ResultadoAlteracao cancelar(Sessao sessao, boolean confirmacaoExplicitaPerdaSinal) {
        exigirSessaoAtiva(sessao);
        boolean perdeuSinal = exigirConfirmacaoSeForaDaAntecedencia(sessao, configuracao(),
                confirmacaoExplicitaPerdaSinal);
        sessao.setStatus(StatusSessao.CANCELADA);
        sessoes.save(sessao);
        return new ResultadoAlteracao(sessao, perdeuSinal);
    }

    private void exigirSessaoAtiva(Sessao sessao) {
        if (!sessao.getStatus().ocupaAgenda()) {
            throw new RegraNegocioException("A sessão não está ativa na agenda");
        }
    }

    private boolean exigirConfirmacaoSeForaDaAntecedencia(Sessao sessao,
            ConfiguracaoTatuador config, boolean confirmacao) {
        boolean semAntecedencia = sessao.getInicio()
                .isBefore(agora().plusHours(config.getAntecedenciaMinimaHoras()));
        if (semAntecedencia && !confirmacao) {
            throw new RegraNegocioException("Faltam menos de " + config.getAntecedenciaMinimaHoras()
                    + "h para a sessão: o sinal será perdido. Confirme explicitamente para seguir");
        }
        return semAntecedencia;
    }

    private void validarHorario(LocalDateTime inicio, LocalDateTime fim,
            ConfiguracaoTatuador config, Sessao ignorada) {
        if (!config.getDiasAtendimento().contains(inicio.getDayOfWeek())) {
            throw new RegraNegocioException("Não há atendimento neste dia da semana");
        }
        LocalDate dia = inicio.toLocalDate();
        IntervaloHorario expediente = new IntervaloHorario(dia.atTime(config.getAberturaExpediente()),
                dia.atTime(config.getFimExpediente()));
        if (!new IntervaloHorario(inicio, fim).cabeEm(expediente)) {
            throw new RegraNegocioException("A sessão precisa caber dentro do expediente");
        }
        if (!inicio.isAfter(agora())) {
            throw new RegraNegocioException("O horário escolhido já passou");
        }
        if (!semConflito(inicio, fim, config, ignorada)) {
            throw new RegraNegocioException("Horário indisponível: conflita com outra sessão ou bloqueio");
        }
    }

    private boolean semConflito(LocalDateTime inicio, LocalDateTime fim,
            ConfiguracaoTatuador config, Sessao ignorada) {
        LocalDate dia = inicio.toLocalDate();
        LocalDateTime inicioDia = dia.atStartOfDay();
        LocalDateTime fimDia = dia.plusDays(1).atStartOfDay();
        Duration limpeza = Duration.ofMinutes(config.getIntervaloLimpezaMinutos());
        IntervaloHorario pretendido = new IntervaloHorario(inicio, fim);

        for (Sessao existente : sessoes.findByStatusInAndInicioBeforeAndFimAfter(
                EnumSet.of(StatusSessao.PRE_RESERVADA, StatusSessao.CONFIRMADA), fimDia,
                inicioDia)) {
            if (ignorada != null && ignorada.getId() != null
                    && ignorada.getId().equals(existente.getId())) {
                continue;
            }
            IntervaloHorario ocupado = new IntervaloHorario(existente.getInicio(),
                    existente.getFim()).comFolgaApos(limpeza);
            if (pretendido.comFolgaApos(limpeza).sobrepoe(ocupado)) {
                return false;
            }
        }
        for (BloqueioAgenda bloqueio : bloqueios.findByInicioBeforeAndFimAfter(fimDia,
                inicioDia)) {
            if (pretendido.sobrepoe(new IntervaloHorario(bloqueio.getInicio(),
                    bloqueio.getFim()))) {
                return false;
            }
        }
        return true;
    }

    private ConfiguracaoTatuador configuracao() {
        return configuracoes.findFirstByOrderByIdAsc().orElseGet(ConfiguracaoTatuador::new);
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio);
    }
}
