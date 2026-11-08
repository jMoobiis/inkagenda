package br.com.inkagenda.infra;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.ItemPortfolioRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import br.com.inkagenda.domain.repository.UsuarioAdminRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** O profile postgres fica sem dados de demonstracao, mas mantem o usuario admin. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:postgres-profile-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("postgres")
class SeedForaDoPostgresTest {

    @Autowired
    private ApplicationContext contexto;
    @Autowired
    private ConfiguracaoTatuadorRepository configuracoes;
    @Autowired
    private ItemPortfolioRepository portfolio;
    @Autowired
    private SolicitacaoRepository solicitacoes;
    @Autowired
    private UsuarioAdminRepository admins;

    @Test
    void naoDeveRodarSeedQuandoProfilePostgres() {
        assertThat(contexto.getBeansOfType(DataInitializer.class)).isEmpty();
        assertThat(configuracoes.count()).isZero();
        assertThat(portfolio.count()).isZero();
        assertThat(solicitacoes.count()).isZero();
    }

    @Test
    void deveCriarAdminQuandoProfilePostgres() {
        assertThat(admins.findByEmail("admin@teste.local")).isPresent();
    }
}
