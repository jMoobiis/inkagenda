package br.com.inkagenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.enums.TipoImagem;
import br.com.inkagenda.domain.model.Cliente;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.ClienteRepository;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrcamentoServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 14, 0);
    private static final int CINCO_MB = 5 * 1024 * 1024;
    private static final String BASE_URL = "https://inkagenda.test";

    @Mock
    private SolicitacaoRepository solicitacoes;
    @Mock
    private ClienteRepository clientes;
    @Mock
    private ConfiguracaoTatuadorRepository configuracoes;
    @Mock
    private ArmazenamentoArquivos armazenamento;

    private OrcamentoService service;

    @BeforeEach
    void configurar() {
        Clock relogio = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        service = new OrcamentoService(solicitacoes, clientes, configuracoes, armazenamento,
                relogio, BASE_URL);
        lenient().when(configuracoes.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(new ConfiguracaoTatuador()));
        lenient().when(clientes.findByWhatsapp(any())).thenReturn(Optional.empty());
        lenient().when(clientes.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(solicitacoes.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(armazenamento.salvar(any())).thenReturn("uploads/arquivo.png");
    }

    // ---------- US01: solicitacao de orcamento ----------

    @Test
    void deveBloquearEnvioDeOrcamentoQuandoNenhumaImagemForAnexada() {
        NovaSolicitacao dados = solicitacao("16991234567", false, true, List.of(), null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Anexe ao menos uma imagem de referência");
        verify(solicitacoes, never()).save(any());
    }

    @Test
    void deveBloquearEnvioDeOrcamentoQuandoListaDeImagensForNula() {
        NovaSolicitacao dados = solicitacao("16991234567", false, true, null, null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Anexe ao menos uma imagem de referência");
    }

    @Test
    void deveBloquearEnvioQuandoHouverMaisDeCincoImagens() {
        List<ArquivoEnviado> seis = Collections.nCopies(6, png("ref.png", 100));
        NovaSolicitacao dados = solicitacao("16991234567", false, true, seis, null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("5 imagens");
        verify(armazenamento, never()).salvar(any());
    }

    @Test
    void deveAceitarEnvioQuandoHouverExatamenteCincoImagens() {
        List<ArquivoEnviado> cinco = Collections.nCopies(5, png("ref.png", 100));

        Solicitacao criada = service.criarSolicitacao(
                solicitacao("16991234567", false, true, cinco, null));

        assertThat(criada.getImagens()).hasSize(5);
        verify(armazenamento, times(5)).salvar(any());
    }

    @Test
    void deveBloquearEnvioQuandoImagemUltrapassarCincoMB() {
        NovaSolicitacao dados = solicitacao("16991234567", false, true,
                List.of(png("grande.png", CINCO_MB + 1)), null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void deveAceitarEnvioQuandoImagemTiverExatamenteCincoMB() {
        Solicitacao criada = service.criarSolicitacao(solicitacao("16991234567", false, true,
                List.of(png("limite.png", CINCO_MB)), null));

        assertThat(criada.getImagens()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"animacao.gif", "documento.pdf", "semextensao", "virus.png.exe"})
    void deveBloquearEnvioQuandoExtensaoNaoForJpgOuPng(String nome) {
        NovaSolicitacao dados = solicitacao("16991234567", false, true,
                List.of(png(nome, 100)), null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("JPG ou PNG");
    }

    @Test
    void deveBloquearEnvioQuandoConteudoNaoCorresponderAoFormatoDeclarado() {
        ArquivoEnviado falso = new ArquivoEnviado("foto.png",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        NovaSolicitacao dados = solicitacao("16991234567", false, true, List.of(falso), null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("JPG ou PNG");
        verify(armazenamento, never()).salvar(any());
    }

    @Test
    void deveAceitarEnvioQuandoArquivoForJpegValidoComExtensaoMaiuscula() {
        Solicitacao criada = service.criarSolicitacao(solicitacao("16991234567", false, true,
                List.of(jpg("FOTO.JPEG", 100)), null));

        assertThat(criada.getImagens()).hasSize(1);
    }

    @Test
    void deveCriarSolicitacaoComStatusNovaEDataHoraQuandoDadosForemValidos() {
        Solicitacao criada = service.criarSolicitacao(solicitacao("(16) 99123-4567", false, true,
                List.of(png("ref.png", 100)), null));

        assertThat(criada.getStatus()).isEqualTo(StatusSolicitacao.NOVA);
        assertThat(criada.getCriadaEm()).isEqualTo(AGORA);
        assertThat(criada.getCliente().getWhatsapp()).isEqualTo("16991234567");
        assertThat(criada.getEstilo()).isEqualTo(EstiloTatuagem.BLACKWORK);
        assertThat(criada.isAceitePrivacidade()).isTrue();
        verify(solicitacoes).save(criada);
    }

    @Test
    void deveBloquearEnvioQuandoAvisoDePrivacidadeNaoForAceito() {
        NovaSolicitacao dados = solicitacao("16991234567", false, false,
                List.of(png("ref.png", 100)), null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Aviso de Privacidade");
        verify(solicitacoes, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "abc", "169912345678901"})
    void deveBloquearEnvioQuandoWhatsappForInvalido(String whatsapp) {
        NovaSolicitacao dados = solicitacao(whatsapp, false, true,
                List.of(png("ref.png", 100)), null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("WhatsApp");
    }

    @Test
    void deveRemoverCodigoDoPaisQuandoWhatsappVierComPrefixoCinquentaECinco() {
        Solicitacao criada = service.criarSolicitacao(solicitacao("+55 16 99123-4567", false, true,
                List.of(png("ref.png", 100)), null));

        assertThat(criada.getCliente().getWhatsapp()).isEqualTo("16991234567");
    }

    @Test
    void deveExigirFotoDaTatuagemAtualQuandoForCobertura() {
        NovaSolicitacao dados = solicitacao("16991234567", true, true,
                List.of(png("ref.png", 100)), null);

        assertThatThrownBy(() -> service.criarSolicitacao(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("tatuagem atual");
    }

    @Test
    void deveGuardarFotoDaTatuagemAtualQuandoForCobertura() {
        Solicitacao criada = service.criarSolicitacao(solicitacao("16991234567", true, true,
                List.of(png("ref.png", 100)), png("atual.png", 100)));

        assertThat(criada.getImagens()).hasSize(2);
        assertThat(criada.getImagens()).filteredOn(i -> i.getTipo() == TipoImagem.TATUAGEM_ATUAL)
                .hasSize(1);
    }

    @Test
    void deveVincularClienteExistenteSemDuplicarQuandoWhatsappJaEstiverCadastrado() {
        Cliente existente = new Cliente("Cliente Antigo", "16991234567", AGORA.minusDays(30));
        when(clientes.findByWhatsapp("16991234567")).thenReturn(Optional.of(existente));

        Solicitacao criada = service.criarSolicitacao(solicitacao("16 99123-4567", false, true,
                List.of(png("ref.png", 100)), null));

        assertThat(criada.getCliente()).isSameAs(existente);
        verify(clientes, never()).save(any());
    }

    @Test
    void deveCadastrarNovoClienteQuandoWhatsappNaoExistir() {
        service.criarSolicitacao(solicitacao("16991234567", false, true,
                List.of(png("ref.png", 100)), null));

        verify(clientes).save(any(Cliente.class));
    }

    // ---------- US03: resposta de orcamento ----------

    @Test
    void deveSugerirSinalDeTrintaPorCentoQuandoConfiguracaoForPadrao() {
        assertThat(service.sugerirSinal(new BigDecimal("500.00")))
                .isEqualByComparingTo("150.00");
    }

    @Test
    void deveRejeitarRespostaQuandoSinalForMaiorQueValorTotal() {
        Solicitacao nova = solicitacaoComStatus(StatusSolicitacao.NOVA);
        when(solicitacoes.findById(1L)).thenReturn(Optional.of(nova));

        assertThatThrownBy(() -> service.responderOrcamento(1L, resposta("500", 3, "500.01")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("sinal");
        assertThat(nova.getStatus()).isEqualTo(StatusSolicitacao.NOVA);
    }

    @ParameterizedTest
    @CsvSource({"0,3,100", "-1,3,100", "500,3,0", "500,3,-10", "500,0,100", "500,-2,100"})
    void deveRejeitarRespostaQuandoAlgumValorForMenorOuIgualAZero(String total, int horas,
            String sinal) {
        Solicitacao nova = solicitacaoComStatus(StatusSolicitacao.NOVA);
        when(solicitacoes.findById(1L)).thenReturn(Optional.of(nova));

        assertThatThrownBy(() -> service.responderOrcamento(1L, resposta(total, horas, sinal)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    void deveAceitarRespostaQuandoSinalForIgualAoValorTotal() {
        when(solicitacoes.findById(1L))
                .thenReturn(Optional.of(solicitacaoComStatus(StatusSolicitacao.NOVA)));

        Solicitacao orcada = service.responderOrcamento(1L, resposta("500", 3, "500"));

        assertThat(orcada.getStatus()).isEqualTo(StatusSolicitacao.ORCADA);
    }

    @Test
    void deveMudarParaOrcadaEGerarTokenQuandoRespostaForValida() {
        when(solicitacoes.findById(1L))
                .thenReturn(Optional.of(solicitacaoComStatus(StatusSolicitacao.NOVA)));

        Solicitacao orcada = service.responderOrcamento(1L, resposta("500", 3, "150"));

        assertThat(orcada.getStatus()).isEqualTo(StatusSolicitacao.ORCADA);
        assertThat(orcada.getTokenAgendamento()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(orcada.getValorTotal()).isEqualByComparingTo("500");
        assertThat(orcada.getDuracaoEstimadaHoras()).isEqualTo(3);
        assertThat(orcada.getValorSinal()).isEqualByComparingTo("150");
        assertThat(orcada.getOrcadaEm()).isEqualTo(AGORA);
        verify(solicitacoes).save(orcada);
    }

    @Test
    void deveGerarTokensDiferentesQuandoOrcamentosForemDistintos() {
        when(solicitacoes.findById(1L))
                .thenReturn(Optional.of(solicitacaoComStatus(StatusSolicitacao.NOVA)));
        when(solicitacoes.findById(2L))
                .thenReturn(Optional.of(solicitacaoComStatus(StatusSolicitacao.NOVA)));

        String primeiro = service.responderOrcamento(1L, resposta("500", 3, "150"))
                .getTokenAgendamento();
        String segundo = service.responderOrcamento(2L, resposta("500", 3, "150"))
                .getTokenAgendamento();

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    void deveManterTokenQuandoOrcamentoJaEnviadoForAjustado() {
        Solicitacao orcada = solicitacaoComStatus(StatusSolicitacao.ORCADA);
        orcada.setTokenAgendamento("token-ja-enviado-ao-cliente");
        when(solicitacoes.findById(1L)).thenReturn(Optional.of(orcada));

        Solicitacao ajustada = service.responderOrcamento(1L, resposta("600", 4, "180"));

        assertThat(ajustada.getTokenAgendamento()).isEqualTo("token-ja-enviado-ao-cliente");
        assertThat(ajustada.getValorTotal()).isEqualByComparingTo("600");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = StatusSolicitacao.class,
            names = {"AGUARDANDO_SINAL", "CONFIRMADA", "PERDIDA"})
    void deveRejeitarRespostaQuandoStatusNaoPermitirOrcamento(StatusSolicitacao status) {
        when(solicitacoes.findById(1L)).thenReturn(Optional.of(solicitacaoComStatus(status)));

        assertThatThrownBy(() -> service.responderOrcamento(1L, resposta("500", 3, "150")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("status");
    }

    @Test
    void deveRejeitarRespostaQuandoSolicitacaoNaoExistir() {
        when(solicitacoes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.responderOrcamento(99L, resposta("500", 3, "150")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não encontrada");
    }

    @Test
    void deveGerarLinkWhatsAppComResumoValoresELinkDeAgendamentoQuandoOrcada() {
        Solicitacao orcada = solicitacaoComStatus(StatusSolicitacao.ORCADA);
        orcada.setValorTotal(new BigDecimal("500.00"));
        orcada.setValorSinal(new BigDecimal("150.00"));
        orcada.setDuracaoEstimadaHoras(3);
        orcada.setTokenAgendamento("abc123");

        String link = service.gerarLinkWhatsApp(orcada);

        assertThat(link).startsWith("https://wa.me/5516991234567?text=");
        String texto = URLDecoder.decode(link.substring(link.indexOf("text=") + 5),
                StandardCharsets.UTF_8);
        assertThat(texto)
                .contains("Maria")
                .contains("500,00")
                .contains("150,00")
                .contains("3h")
                .contains(BASE_URL + "/agendar/abc123");
    }

    // ---------- US02: destaque de inatividade ----------

    @Test
    void deveMarcarSemRetornoQuandoOrcadaHaMaisDeTresDias() {
        Solicitacao orcada = solicitacaoComStatus(StatusSolicitacao.ORCADA);
        orcada.setOrcadaEm(AGORA.minusDays(3).minusMinutes(1));

        assertThat(service.estaSemRetorno(orcada)).isTrue();
    }

    @Test
    void naoDeveMarcarSemRetornoQuandoOrcadaHaExatamenteTresDias() {
        Solicitacao orcada = solicitacaoComStatus(StatusSolicitacao.ORCADA);
        orcada.setOrcadaEm(AGORA.minusDays(3));

        assertThat(service.estaSemRetorno(orcada)).isFalse();
    }

    @Test
    void deveRespeitarPrazoConfiguradoQuandoDiasSemRetornoForAlterado() {
        ConfiguracaoTatuador configuracao = new ConfiguracaoTatuador();
        configuracao.setDiasSemRetorno(1);
        when(configuracoes.findFirstByOrderByIdAsc()).thenReturn(Optional.of(configuracao));
        Solicitacao orcada = solicitacaoComStatus(StatusSolicitacao.ORCADA);
        orcada.setOrcadaEm(AGORA.minusDays(2));

        assertThat(service.estaSemRetorno(orcada)).isTrue();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = StatusSolicitacao.class,
            names = "ORCADA", mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
    void naoDeveMarcarSemRetornoQuandoStatusNaoForOrcada(StatusSolicitacao status) {
        Solicitacao solicitacao = solicitacaoComStatus(status);
        solicitacao.setOrcadaEm(AGORA.minusDays(30));

        assertThat(service.estaSemRetorno(solicitacao)).isFalse();
    }

    // ---------- apoio ----------

    private static NovaSolicitacao solicitacao(String whatsapp, boolean cobertura,
            boolean aceitePrivacidade, List<ArquivoEnviado> imagens, ArquivoEnviado fotoAtual) {
        return new NovaSolicitacao("Maria Teste", whatsapp, 10, "Antebraço",
                EstiloTatuagem.BLACKWORK, cobertura, "Uma ideia", "Sábados", aceitePrivacidade,
                imagens, fotoAtual);
    }

    private static Solicitacao solicitacaoComStatus(StatusSolicitacao status) {
        Solicitacao solicitacao = new Solicitacao();
        solicitacao.setCliente(new Cliente("Maria Teste", "16991234567", AGORA.minusDays(10)));
        solicitacao.setStatus(status);
        solicitacao.setCriadaEm(AGORA.minusDays(10));
        return solicitacao;
    }

    private static RespostaOrcamento resposta(String total, int horas, String sinal) {
        return new RespostaOrcamento(new BigDecimal(total), horas, new BigDecimal(sinal));
    }

    private static ArquivoEnviado png(String nome, int tamanho) {
        return arquivo(nome, tamanho, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    }

    private static ArquivoEnviado jpg(String nome, int tamanho) {
        return arquivo(nome, tamanho, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private static ArquivoEnviado arquivo(String nome, int tamanho, byte[] assinatura) {
        byte[] conteudo = Arrays.copyOf(assinatura, Math.max(tamanho, assinatura.length));
        return new ArquivoEnviado(nome, conteudo);
    }
}
