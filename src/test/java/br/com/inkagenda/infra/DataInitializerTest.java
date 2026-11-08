package br.com.inkagenda.infra;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.FichaAnamneseRepository;
import br.com.inkagenda.domain.repository.ItemPortfolioRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import br.com.inkagenda.domain.repository.UsuarioAdminRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:seed-test;DB_CLOSE_DELAY=-1")
class DataInitializerTest {

    private static final ApplicationArguments SEM_ARGUMENTOS = new DefaultApplicationArguments();

    @Autowired
    private DataInitializer seed;
    @Autowired
    private Clock relogio;
    @Autowired
    private ConfiguracaoTatuadorRepository configuracoes;
    @Autowired
    private ItemPortfolioRepository portfolio;
    @Autowired
    private SolicitacaoRepository solicitacoes;
    @Autowired
    private SessaoRepository sessoes;
    @Autowired
    private FichaAnamneseRepository fichas;
    @Autowired
    private UsuarioAdminRepository admins;
    @Autowired
    private PasswordEncoder codificador;

    @Test
    void deveCriarPerfilComExpedienteIntervaloESinalQuandoBaseVazia() {
        ConfiguracaoTatuador config = configuracoes.findFirstByOrderByIdAsc().orElseThrow();

        assertThat(config.getAberturaExpediente()).isEqualTo(LocalTime.of(10, 0));
        assertThat(config.getFimExpediente()).isEqualTo(LocalTime.of(18, 0));
        assertThat(config.getIntervaloLimpezaMinutos()).isEqualTo(30);
        assertThat(config.getPercentualSinal()).isEqualTo(30);
    }

    @Test
    void deveCriarSeisItensDePortfolioQuandoBaseVazia() {
        assertThat(portfolio.findAll()).hasSize(6);
    }

    @Test
    void deveCriarSolicitacoesEmCadaEstadoEsperadoQuandoBaseVazia() {
        assertThat(solicitacoes.findByStatusOrderByCriadaEmDesc(StatusSolicitacao.NOVA)).hasSize(1);
        assertThat(solicitacoes.findByStatusOrderByCriadaEmDesc(StatusSolicitacao.AGUARDANDO_SINAL))
                .hasSize(1);
        assertThat(solicitacoes.findByStatusOrderByCriadaEmDesc(StatusSolicitacao.CONFIRMADA))
                .hasSize(1);

        List<Solicitacao> orcadas = solicitacoes
                .findByStatusOrderByCriadaEmDesc(StatusSolicitacao.ORCADA);
        assertThat(orcadas).hasSize(2);
        assertThat(orcadas).anyMatch(s -> s.getOrcadaEm().isBefore(LocalDateTime.now(relogio).minusDays(4)));
        assertThat(orcadas).anyMatch(s -> s.getOrcadaEm().isAfter(LocalDateTime.now(relogio).minusDays(1)));
    }

    @Test
    void deveCriarSessaoConfirmadaParaAmanhaComFichaQuandoBaseVazia() {
        LocalDate amanha = LocalDate.now(relogio).plusDays(1);

        List<Sessao> sessoesDeAmanha = sessoes.findByStatusAndInicioBetweenOrderByInicio(
                StatusSessao.CONFIRMADA, amanha.atStartOfDay(), amanha.atTime(LocalTime.MAX));

        assertThat(sessoesDeAmanha).hasSize(1);
        assertThat(fichas.findBySessaoId(sessoesDeAmanha.get(0).getId())).isPresent();
    }

    @Test
    void naoDeveDuplicarDadosQuandoExecutarDeNovo() throws Exception {
        long portfolioAntes = portfolio.count();
        long solicitacoesAntes = solicitacoes.count();

        seed.run(SEM_ARGUMENTOS);

        assertThat(portfolio.count()).isEqualTo(portfolioAntes);
        assertThat(solicitacoes.count()).isEqualTo(solicitacoesAntes);
    }

    @Test
    void deveGuardarSenhaDoAdminComBcryptQuandoConfigurada() {
        String hash = admins.findByEmail("admin@teste.local").orElseThrow().getSenhaHash();

        assertThat(hash).startsWith("$2").doesNotContain("valor-proprio-de-teste");
        assertThat(codificador.matches("valor-proprio-de-teste", hash)).isTrue();
    }
}
