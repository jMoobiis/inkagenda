package br.com.inkagenda.infra;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.enums.TipoImagem;
import br.com.inkagenda.domain.model.Cliente;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.ImagemReferencia;
import br.com.inkagenda.domain.model.ItemPortfolio;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.ClienteRepository;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.ItemPortfolioRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import br.com.inkagenda.service.AnamneseService;
import br.com.inkagenda.service.ArmazenamentoArquivos;
import br.com.inkagenda.service.ArquivoEnviado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seed com dados 100% ficticios; nao roda no profile postgres e so preenche base vazia. */
@Component
@Profile("!postgres")
@Order(2)
public class DataInitializer implements ApplicationRunner {

    private final ConfiguracaoTatuadorRepository configuracoes;
    private final ItemPortfolioRepository portfolio;
    private final ClienteRepository clientes;
    private final SolicitacaoRepository solicitacoes;
    private final SessaoRepository sessoes;
    private final AnamneseService anamnese;
    private final ArmazenamentoArquivos armazenamento;
    private final Clock relogio;

    public DataInitializer(ConfiguracaoTatuadorRepository configuracoes,
            ItemPortfolioRepository portfolio, ClienteRepository clientes,
            SolicitacaoRepository solicitacoes, SessaoRepository sessoes,
            AnamneseService anamnese, ArmazenamentoArquivos armazenamento, Clock relogio) {
        this.configuracoes = configuracoes;
        this.portfolio = portfolio;
        this.clientes = clientes;
        this.solicitacoes = solicitacoes;
        this.sessoes = sessoes;
        this.anamnese = anamnese;
        this.armazenamento = armazenamento;
        this.relogio = relogio;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (configuracoes.count() > 0) {
            return;
        }
        semearPerfil();
        semearPortfolio();
        semearSolicitacoes();
    }

    private void semearPerfil() {
        ConfiguracaoTatuador config = new ConfiguracaoTatuador();
        config.setNome("Gusta");
        config.setEnderecoEstudio("Rua Fictícia das Agulhas, 100 - Centro");
        config.setChavePix("pix-ficticio@inkagenda.local");
        config.setIntervaloLimpezaMinutos(30);
        config.setPercentualSinal(30);
        configuracoes.save(config);
    }

    private void semearPortfolio() {
        Object[][] fotos = {
            {"Linhas Finas", EstiloTatuagem.FINELINE, "Mais pedida", true},
            {"Sombra Sólida", EstiloTatuagem.BLACKWORK, null, true},
            {"Rosa Clássica", EstiloTatuagem.OLD_SCHOOL, "Clássico", true},
            {"Nova Pele", EstiloTatuagem.COBERTURA, "Antes e depois", false},
            {"Retrato em Luz", EstiloTatuagem.REALISMO, null, false},
            {"Onda Oriental", EstiloTatuagem.NEO_ORIENTAL, "Novo", false},
        };
        for (int i = 0; i < fotos.length; i++) {
            ItemPortfolio item = new ItemPortfolio();
            item.setTitulo((String) fotos[i][0]);
            item.setEstilo((EstiloTatuagem) fotos[i][1]);
            item.setTagDestaque((String) fotos[i][2]);
            item.setFixadoHero((Boolean) fotos[i][3]);
            item.setDisplayOrder(i);
            item.setCaminhoImagem(imagemAbstrata(i));
            portfolio.save(item);
        }
    }

    private void semearSolicitacoes() {
        LocalDateTime agora = LocalDateTime.now(relogio);
        solicitacao("Cliente Fictício Um", "16900000001", StatusSolicitacao.NOVA, agora.minusHours(3),
                null);
        solicitacao("Cliente Fictício Dois", "16900000002", StatusSolicitacao.ORCADA,
                agora.minusDays(1), agora.minusHours(20));
        solicitacao("Cliente Fictício Três", "16900000003", StatusSolicitacao.ORCADA,
                agora.minusDays(6), agora.minusDays(5));

        Solicitacao aguardando = solicitacao("Cliente Fictício Quatro", "16900000004",
                StatusSolicitacao.AGUARDANDO_SINAL, agora.minusDays(2), agora.minusDays(1));
        Sessao preReserva = sessao(aguardando, proximoDiaUtil(agora.toLocalDate().plusDays(3))
                .atTime(14, 0), StatusSessao.PRE_RESERVADA);
        preReserva.setExpiraEm(agora.plusHours(24));
        sessoes.save(preReserva);

        Solicitacao confirmada = solicitacao("Cliente Fictício Cinco", "16900000005",
                StatusSolicitacao.CONFIRMADA, agora.minusDays(4), agora.minusDays(3));
        Sessao amanha = sessao(confirmada, agora.toLocalDate().plusDays(1).atTime(10, 0),
                StatusSessao.CONFIRMADA);
        amanha.setDataPagamento(agora.toLocalDate().minusDays(1));
        amanha.setValorRecebido(confirmada.getValorSinal());
        amanha.setValorAAbater(confirmada.getValorTotal().subtract(confirmada.getValorSinal()));
        anamnese.gerarFicha(sessoes.save(amanha));
    }

    private Solicitacao solicitacao(String nome, String whatsapp, StatusSolicitacao status,
            LocalDateTime criadaEm, LocalDateTime orcadaEm) {
        Solicitacao s = new Solicitacao();
        s.setCliente(clientes.save(new Cliente(nome, whatsapp, criadaEm)));
        s.setTamanhoCm(12);
        s.setRegiaoCorpo("Antebraço");
        s.setEstilo(EstiloTatuagem.BLACKWORK);
        s.setDescricao("Pedido fictício para demonstração do sistema.");
        s.setPreferenciaDias("Terça ou quinta");
        s.setAceitePrivacidade(true);
        s.setStatus(status);
        s.setCriadaEm(criadaEm);
        s.getImagens().add(new ImagemReferencia(s, imagemAbstrata(whatsapp.hashCode()),
                TipoImagem.REFERENCIA));
        if (orcadaEm != null) {
            s.setValorTotal(new BigDecimal("800.00"));
            s.setValorSinal(new BigDecimal("240.00"));
            s.setDuracaoEstimadaHoras(3);
            s.setOrcadaEm(orcadaEm);
            s.setTokenAgendamento(UUID.randomUUID().toString().replace("-", ""));
        }
        return solicitacoes.save(s);
    }

    private static Sessao sessao(Solicitacao solicitacao, LocalDateTime inicio,
            StatusSessao status) {
        Sessao sessao = new Sessao();
        sessao.setSolicitacao(solicitacao);
        sessao.setInicio(inicio);
        sessao.setFim(inicio.plusHours(solicitacao.getDuracaoEstimadaHoras()));
        sessao.setStatus(status);
        return sessao;
    }

    private static LocalDate proximoDiaUtil(LocalDate dia) {
        return dia.getDayOfWeek() == DayOfWeek.SUNDAY ? dia.plusDays(1) : dia;
    }

    private String imagemAbstrata(long semente) {
        return armazenamento.salvar(new ArquivoEnviado("seed.png",
                GeradorImagemAbstrata.png(semente)));
    }
}
