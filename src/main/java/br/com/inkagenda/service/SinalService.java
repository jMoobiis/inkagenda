package br.com.inkagenda.service;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.FichaAnamnese;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SinalService {

    private static final String TOKEN_INVALIDO = "Link de agendamento inválido";
    private static final DateTimeFormatter DATA =DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String CUIDADOS = "Cuidados antes da sessão: durma bem, alimente-se, "
            + "hidrate a pele e evite bebida alcoólica nas 24h anteriores.";

    private final SessaoRepository sessoes;
    private final SolicitacaoRepository solicitacoes;
    private final ConfiguracaoTatuadorRepository configuracoes;
    private final AgendaService agenda;
    private final AnamneseService anamnese;
    private final Clock relogio;

    public SinalService(SessaoRepository sessoes, SolicitacaoRepository solicitacoes,
            ConfiguracaoTatuadorRepository configuracoes, AgendaService agenda,
            AnamneseService anamnese, Clock relogio) {
        this.sessoes = sessoes;
        this.solicitacoes = solicitacoes;
        this.configuracoes = configuracoes;
        this.agenda = agenda;
        this.anamnese = anamnese;
        this.relogio = relogio;
    }

    /** Solicitacao dona do link de agendamento. */
    @Transactional(readOnly = true)
    public Solicitacao solicitacaoPorToken(String token) {
        return solicitacoes.findByTokenAgendamento(
                TokenAcesso.exigirInformado(token, TOKEN_INVALIDO))
                .orElseThrow(() -> new TokenInvalidoException(TOKEN_INVALIDO));
    }

    /** Pre-reserva aguardando sinal da solicitacao, para a tela de Pix. */
    @Transactional(readOnly = true)
    public Optional<Sessao> preReservaDe(Solicitacao solicitacao) {
        return sessoes.findFirstBySolicitacaoIdAndStatusInOrderByIdDesc(solicitacao.getId(),
                List.of(StatusSessao.PRE_RESERVADA));
    }

    /** Dados do tatuador usados nas telas de pagamento. */
    @Transactional(readOnly = true)
    public ConfiguracaoTatuador configuracao() {
        return configuracoes.findFirstByOrderByIdAsc().orElseGet(ConfiguracaoTatuador::new);
    }

    /** O cliente escolhe o horario pelo link e a solicitacao passa a aguardar o sinal (US05). */
    @Transactional
    public Sessao escolherHorario(String token, LocalDateTime inicio) {
        Solicitacao solicitacao = solicitacaoPorToken(token);
        if (solicitacao.getStatus() != StatusSolicitacao.ORCADA) {
            throw new RegraNegocioException("Este orçamento não está disponível para agendamento");
        }
        Sessao sessao = agenda.criarPreReserva(solicitacao, inicio);
        solicitacao.setStatus(StatusSolicitacao.AGUARDANDO_SINAL);
        solicitacoes.save(solicitacao);
        return sessao;
    }

    /** Registra o Pix conferido; so entao a sessao vira confirmada (RN04). */
    @Transactional
    public ResultadoSinal registrarSinal(Long sessaoId, RegistroSinal registro) {
        Sessao sessao = buscarSessao(sessaoId);
        if (sessao.getStatus() != StatusSessao.PRE_RESERVADA) {
            throw new RegraNegocioException("Esta sessão não aguarda sinal");
        }
        if (sessao.getExpiraEm() != null && sessao.getExpiraEm().isBefore(agora())) {
            throw new RegraNegocioException("O prazo da pré-reserva terminou");
        }
        Solicitacao solicitacao = sessao.getSolicitacao();
        validarRegistro(registro, solicitacao);
        boolean divergente = registro.valorRecebido().compareTo(solicitacao.getValorSinal()) != 0;
        if (divergente && !registro.confirmacaoDivergencia()) {
            throw new RegraNegocioException(
                    "O valor recebido diverge do sinal combinado. Confirme explicitamente para seguir");
        }

        BigDecimal valorAAbater = solicitacao.getValorTotal().subtract(registro.valorRecebido());
        sessao.setDataPagamento(registro.dataPagamento());
        sessao.setValorRecebido(registro.valorRecebido());
        sessao.setComprovante(registro.comprovante());
        sessao.setValorAAbater(valorAAbater);
        sessao.setStatus(StatusSessao.CONFIRMADA);
        sessao.setExpiraEm(null);
        sessoes.save(sessao);
        solicitacao.setStatus(StatusSolicitacao.CONFIRMADA);
        solicitacoes.save(solicitacao);
        FichaAnamnese ficha = anamnese.gerarFicha(sessao);
        return new ResultadoSinal(sessao, valorAAbater, divergente, ficha);
    }

    /** Libera os horarios com prazo vencido e devolve as solicitacoes para orcada (RN06). */
    @Transactional
    public int expirarPreReservasVencidas() {
        List<Sessao> vencidas = sessoes.findByStatusAndExpiraEmBefore(StatusSessao.PRE_RESERVADA,
                agora());
        for (Sessao sessao : vencidas) {
            sessao.setStatus(StatusSessao.EXPIRADA);
            sessoes.save(sessao);
            Solicitacao solicitacao = sessao.getSolicitacao();
            solicitacao.setStatus(StatusSolicitacao.ORCADA);
            solicitacoes.save(solicitacao);
        }
        return vencidas.size();
    }

    /** Sessoes confirmadas de amanha, para a tela de vespera (US06). */
    @Transactional(readOnly = true)
    public List<Sessao> sessoesDeAmanha() {
        LocalDate amanha = agora().toLocalDate().plusDays(1);
        return sessoes.findByStatusAndInicioBetweenOrderByInicio(StatusSessao.CONFIRMADA,
                amanha.atStartOfDay(), amanha.atTime(LocalTime.MAX));
    }

    /** Monta o lembrete; sem telefone valido, devolve o aviso em vez do link (US06, CA3). */
    @Transactional(readOnly = true)
    public LembreteVespera lembreteVespera(Sessao sessao) {
        exigirConfirmada(sessao);
        NumeroWhatsapp numero;
        try {
            numero = NumeroWhatsapp.de(sessao.getSolicitacao().getCliente().getWhatsapp());
        } catch (RegraNegocioException e) {
            return new LembreteVespera(sessao, null,
                    "Telefone inválido. Corrija o cadastro do cliente para enviar o lembrete");
        }
        return new LembreteVespera(sessao,
                GeradorLinkWhatsApp.link(numero, mensagemVespera(sessao)), null);
    }

    /** Registra a presenca confirmada pelo cliente (US06). */
    @Transactional
    public void confirmarPresenca(Long sessaoId) {
        Sessao sessao = buscarSessao(sessaoId);
        exigirConfirmada(sessao);
        sessao.setPresencaConfirmada(true);
        sessoes.save(sessao);
    }

    private Sessao buscarSessao(Long sessaoId) {
        return sessoes.findById(sessaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão não encontrada"));
    }

    private void exigirConfirmada(Sessao sessao) {
        if (sessao.getStatus() != StatusSessao.CONFIRMADA) {
            throw new RegraNegocioException("Esta sessão não está confirmada");
        }
    }

    private void validarRegistro(RegistroSinal registro, Solicitacao solicitacao) {
        if (registro.dataPagamento() == null
                || registro.dataPagamento().isAfter(agora().toLocalDate())) {
            throw new RegraNegocioException("Informe uma data do pagamento válida");
        }
        BigDecimal valor = registro.valorRecebido();
        if (valor == null || valor.signum() <= 0) {
            throw new RegraNegocioException("O valor recebido deve ser maior que zero");
        }
        if (valor.compareTo(solicitacao.getValorTotal()) > 0) {
            throw new RegraNegocioException("O valor recebido não pode ser maior que o valor total");
        }
    }

    private String mensagemVespera(Sessao sessao) {
        ConfiguracaoTatuador config = configuracao();
        String primeiroNome = sessao.getSolicitacao().getCliente().getNome().split(" ")[0];
        return "Olá, " + primeiroNome + "! Aqui é o Gusta. Lembrete da sua sessão:\n"
                + "Data: " + sessao.getInicio().format(DATA) + "\n"
                + "Horário: " + sessao.getInicio().format(HORA) + "\n"
                + "Endereço: " + config.getEnderecoEstudio() + "\n"
                + CUIDADOS;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio);
    }
}
