package br.com.inkagenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.FichaAnamnese;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.FichaAnamneseRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnamneseServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 14, 0);
    private static final String TOKEN = "token-da-ficha";

    @Mock
    private FichaAnamneseRepository fichas;
    @Mock
    private ConfiguracaoTatuadorRepository configuracoes;

    private AnamneseService service;

    @BeforeEach
    void configurar() {
        Clock relogio = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        service = new AnamneseService(fichas, configuracoes, relogio);
        lenient().when(configuracoes.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(new ConfiguracaoTatuador()));
        lenient().when(fichas.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Sessao sessao(StatusSessao status) {
        Sessao s = new Sessao();
        s.setId(3L);
        s.setStatus(status);
        return s;
    }

    private FichaAnamnese fichaPendente(LocalDateTime expiraEm) {
        FichaAnamnese ficha = new FichaAnamnese();
        ficha.setSessao(sessao(StatusSessao.CONFIRMADA));
        ficha.setToken(TOKEN);
        ficha.setExpiraEm(expiraEm);
        lenient().when(fichas.findByToken(TOKEN)).thenReturn(Optional.of(ficha));
        return ficha;
    }

    private static DadosAnamnese dados(boolean consentimento) {
        return new DadosAnamnese("Latex", "Nenhum", "Queloide", true, false, "Sem observacoes",
                consentimento);
    }

    // ---------- geracao do link (US07) ----------

    @Test
    void deveGerarFichaComTokenEValidadeQuandoSessaoConfirmada() {
        Sessao sessao = sessao(StatusSessao.CONFIRMADA);
        when(fichas.findBySessaoId(3L)).thenReturn(Optional.empty());

        FichaAnamnese ficha = service.gerarFicha(sessao);

        assertThat(ficha.getSessao()).isSameAs(sessao);
        assertThat(ficha.getToken()).isNotBlank();
        assertThat(ficha.getExpiraEm()).isEqualTo(AGORA.plusHours(72));
        assertThat(ficha.isConsentimentoDadosSaude()).isFalse();
        verify(fichas).save(ficha);
    }

    @Test
    void deveRespeitarValidadeConfiguradaQuandoGerarFicha() {
        ConfiguracaoTatuador config = new ConfiguracaoTatuador();
        config.setValidadeAnamneseHoras(24);
        when(configuracoes.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));
        when(fichas.findBySessaoId(3L)).thenReturn(Optional.empty());

        FichaAnamnese ficha = service.gerarFicha(sessao(StatusSessao.CONFIRMADA));

        assertThat(ficha.getExpiraEm()).isEqualTo(AGORA.plusHours(24));
    }

    @Test
    void deveGerarTokensDiferentesQuandoFichasDiferentes() {
        when(fichas.findBySessaoId(3L)).thenReturn(Optional.empty());

        String primeiro = service.gerarFicha(sessao(StatusSessao.CONFIRMADA)).getToken();
        String segundo = service.gerarFicha(sessao(StatusSessao.CONFIRMADA)).getToken();

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    void deveDevolverFichaExistenteQuandoSessaoJaTemFicha() {
        FichaAnamnese existente = new FichaAnamnese();
        when(fichas.findBySessaoId(3L)).thenReturn(Optional.of(existente));

        assertThat(service.gerarFicha(sessao(StatusSessao.CONFIRMADA))).isSameAs(existente);
        verify(fichas, never()).save(any());
    }

    @Test
    void deveRejeitarGeracaoQuandoSessaoNaoConfirmada() {
        assertThatThrownBy(() -> service.gerarFicha(sessao(StatusSessao.PRE_RESERVADA)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("sessão confirmada");
        verify(fichas, never()).save(any());
    }

    // ---------- abertura pelo link (CA3) ----------

    @Test
    void deveAbrirFichaQuandoTokenValido() {
        FichaAnamnese ficha = fichaPendente(AGORA.plusHours(1));

        assertThat(service.abrirPorToken(TOKEN)).isSameAs(ficha);
    }

    @Test
    void deveAbrirFichaQuandoValidadeTerminaExatamenteAgora() {
        FichaAnamnese ficha = fichaPendente(AGORA);

        assertThat(service.abrirPorToken(TOKEN)).isSameAs(ficha);
    }

    @Test
    void deveRejeitarAberturaQuandoTokenNaoExiste() {
        when(fichas.findByToken("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abrirPorToken("inexistente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link da ficha inválido");
    }

    @Test
    void deveRejeitarAberturaQuandoTokenNulo() {
        assertThatThrownBy(() -> service.abrirPorToken(null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link da ficha inválido");
    }

    @Test
    void deveRejeitarAberturaQuandoTokenEmBranco() {
        assertThatThrownBy(() -> service.abrirPorToken(" "))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link da ficha inválido");
    }

    @Test
    void deveRejeitarAberturaQuandoTokenExpirado() {
        fichaPendente(AGORA.minusMinutes(1));

        assertThatThrownBy(() -> service.abrirPorToken(TOKEN))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link da ficha expirado");
    }

    @Test
    void naoDeveExporDadosDeSaudeQuandoLinkDeFichaJaPreenchida() {
        FichaAnamnese ficha = fichaPendente(AGORA.plusHours(1));
        ficha.setPreenchidaEm(AGORA.minusHours(1));

        assertThatThrownBy(() -> service.abrirPorToken(TOKEN))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já foi enviada");
    }

    // ---------- envio e consentimento (US07, LGPD art. 11, I) ----------

    @Test
    void deveBloquearEnvioENaoGravarNadaQuandoSemConsentimento() {
        FichaAnamnese ficha = fichaPendente(AGORA.plusHours(1));

        assertThatThrownBy(() -> service.enviarFicha(TOKEN, dados(false)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("consentimento");
        assertThat(ficha.getAlergias()).isNull();
        assertThat(ficha.getPreenchidaEm()).isNull();
        verify(fichas, never()).save(any());
    }

    @Test
    void deveGravarDadosDeSaudeQuandoComConsentimento() {
        FichaAnamnese ficha = fichaPendente(AGORA.plusHours(1));

        service.enviarFicha(TOKEN, dados(true));

        assertThat(ficha.getAlergias()).isEqualTo("Latex");
        assertThat(ficha.getMedicamentos()).isEqualTo("Nenhum");
        assertThat(ficha.getProblemasCicatrizacao()).isEqualTo("Queloide");
        assertThat(ficha.isDiabetes()).isTrue();
        assertThat(ficha.isGestacao()).isFalse();
        assertThat(ficha.getObservacoes()).isEqualTo("Sem observacoes");
        verify(fichas).save(ficha);
    }

    @Test
    void deveRegistrarMomentoDoConsentimentoQuandoEnviar() {
        FichaAnamnese ficha = fichaPendente(AGORA.plusHours(1));

        service.enviarFicha(TOKEN, dados(true));

        assertThat(ficha.isConsentimentoDadosSaude()).isTrue();
        assertThat(ficha.getConsentimentoEm()).isEqualTo(AGORA);
        assertThat(ficha.getPreenchidaEm()).isEqualTo(AGORA);
    }

    @Test
    void deveRejeitarEnvioQuandoTokenInvalido() {
        when(fichas.findByToken("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enviarFicha("inexistente", dados(true)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link da ficha inválido");
    }

    @Test
    void deveRejeitarEnvioQuandoTokenExpirado() {
        FichaAnamnese ficha = fichaPendente(AGORA.minusSeconds(1));

        assertThatThrownBy(() -> service.enviarFicha(TOKEN, dados(true)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Link da ficha expirado");
        assertThat(ficha.getAlergias()).isNull();
    }

    @Test
    void naoDeveSobrescreverQuandoFichaJaEnviada() {
        FichaAnamnese ficha = fichaPendente(AGORA.plusHours(1));
        ficha.setAlergias("Original");
        ficha.setPreenchidaEm(AGORA.minusHours(1));

        assertThatThrownBy(() -> service.enviarFicha(TOKEN, dados(true)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já foi enviada");
        assertThat(ficha.getAlergias()).isEqualTo("Original");
    }

    // ---------- leitura pelo tatuador (CA3) ----------

    @Test
    void deveDevolverFichaQuandoTatuadorConsultaSessao() {
        FichaAnamnese ficha = new FichaAnamnese();
        when(fichas.findBySessaoId(3L)).thenReturn(Optional.of(ficha));

        assertThat(service.fichaDaSessao(3L)).containsSame(ficha);
    }

    @Test
    void deveDevolverVazioQuandoSessaoNaoTemFicha() {
        when(fichas.findBySessaoId(3L)).thenReturn(Optional.empty());

        assertThat(service.fichaDaSessao(3L)).isEmpty();
    }
}
