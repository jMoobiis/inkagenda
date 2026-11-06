package br.com.inkagenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.domain.enums.ModoLayout;
import br.com.inkagenda.domain.model.ConfiguracaoVitrine;
import br.com.inkagenda.domain.model.ItemPortfolio;
import br.com.inkagenda.domain.repository.ConfiguracaoVitrineRepository;
import br.com.inkagenda.domain.repository.ItemPortfolioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VitrineServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};

    @Mock
    private ItemPortfolioRepository itens;
    @Mock
    private ConfiguracaoVitrineRepository configuracoes;
    @Mock
    private ArmazenamentoArquivos armazenamento;
    @InjectMocks
    private VitrineService service;

    @BeforeEach
    void configurar() {
        lenient().when(itens.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(configuracoes.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(armazenamento.salvar(any())).thenReturn("gerado.png");
    }

    private static NovaFoto novaFoto(String titulo, EstiloTatuagem estilo, ArquivoEnviado arquivo) {
        return new NovaFoto(titulo, estilo, "Destaque", 4, arquivo);
    }

    private static ArquivoEnviado png() {
        return new ArquivoEnviado("foto.png", PNG);
    }

    private ItemPortfolio item(Long id, boolean ativo, boolean hero, int ordem) {
        ItemPortfolio i = new ItemPortfolio();
        i.setId(id);
        i.setTitulo("Foto " + id);
        i.setEstilo(EstiloTatuagem.BLACKWORK);
        i.setCaminhoImagem("foto" + id + ".png");
        i.setAtivo(ativo);
        i.setFixadoHero(hero);
        i.setDisplayOrder(ordem);
        lenient().when(itens.findById(id)).thenReturn(Optional.of(i));
        return i;
    }

    private void duasFotos() {
        List<ItemPortfolio> fotos = List.of(item(1L, true, false, 0), item(2L, true, false, 1));
        when(itens.findAllByOrderByDisplayOrderAsc()).thenReturn(fotos);
    }

    // ---------- upload ----------

    @Test
    void deveSalvarFotoAtivaComDadosQuandoUploadValido() {
        ItemPortfolio item = service.adicionarFoto(novaFoto("Dragao", EstiloTatuagem.NEO_ORIENTAL,
                png()));

        assertThat(item.getTitulo()).isEqualTo("Dragao");
        assertThat(item.getEstilo()).isEqualTo(EstiloTatuagem.NEO_ORIENTAL);
        assertThat(item.getTagDestaque()).isEqualTo("Destaque");
        assertThat(item.getDisplayOrder()).isEqualTo(4);
        assertThat(item.getCaminhoImagem()).isEqualTo("gerado.png");
        assertThat(item.isAtivo()).isTrue();
        assertThat(item.isFixadoHero()).isFalse();
    }

    @Test
    void deveColocarNoFimDaOrdemQuandoUploadSemDisplayOrder() {
        List<ItemPortfolio> existentes = List.of(item(1L, true, false, 0), item(2L, true, false, 8));
        when(itens.findAllByOrderByDisplayOrderAsc()).thenReturn(existentes);

        ItemPortfolio item = service.adicionarFoto(new NovaFoto("Nova", EstiloTatuagem.FINELINE,
                null, null, png()));

        assertThat(item.getDisplayOrder()).isEqualTo(9);
    }

    @Test
    void deveRejeitarUploadQuandoSemTitulo() {
        assertThatThrownBy(() -> service.adicionarFoto(novaFoto(" ", EstiloTatuagem.FINELINE, png())))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("título");
        verify(armazenamento, never()).salvar(any());
    }

    @Test
    void deveRejeitarUploadQuandoSemEstilo() {
        assertThatThrownBy(() -> service.adicionarFoto(novaFoto("Foto", null, png())))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("estilo");
    }

    @Test
    void deveRejeitarUploadQuandoSemArquivo() {
        assertThatThrownBy(() -> service.adicionarFoto(novaFoto("Foto", EstiloTatuagem.FINELINE,
                null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("imagem");
    }

    @Test
    void deveRejeitarUploadQuandoFormatoInvalido() {
        ArquivoEnviado gif = new ArquivoEnviado("foto.gif", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.adicionarFoto(novaFoto("Foto", EstiloTatuagem.FINELINE, gif)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("JPG ou PNG");
        verify(armazenamento, never()).salvar(any());
    }

    @Test
    void deveRejeitarUploadQuandoArquivoMaiorQueLimite() {
        byte[] grande = new byte[(int) ValidadorArquivoReferencia.TAMANHO_MAXIMO_BYTES + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        assertThatThrownBy(() -> service.adicionarFoto(novaFoto("Foto", EstiloTatuagem.FINELINE,
                new ArquivoEnviado("foto.png", grande))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("5MB");
    }

    // ---------- modos de layout ----------

    @Test
    void deveUsarSteezySplitQuandoNaoHaConfiguracao() {
        when(configuracoes.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThat(service.modoAtual()).isEqualTo(ModoLayout.STEEZY_SPLIT);
    }

    @Test
    void deveAlterarModoQuandoJaHaConfiguracao() {
        ConfiguracaoVitrine config = new ConfiguracaoVitrine();
        when(configuracoes.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        service.alterarModo(ModoLayout.MASONRY_FLOW);

        assertThat(config.getModoLayout()).isEqualTo(ModoLayout.MASONRY_FLOW);
        verify(configuracoes).save(config);
    }

    @Test
    void deveCriarConfiguracaoQuandoAlterarModoPelaPrimeiraVez() {
        when(configuracoes.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        ConfiguracaoVitrine salva = service.alterarModo(ModoLayout.CAROUSEL_SHOWCASE);

        assertThat(salva.getModoLayout()).isEqualTo(ModoLayout.CAROUSEL_SHOWCASE);
    }

    @Test
    void deveRejeitarModoQuandoNulo() {
        assertThatThrownBy(() -> service.alterarModo(null))
                .isInstanceOf(RegraNegocioException.class);
    }

    // ---------- listagem e filtro ----------

    @Test
    void deveListarSomenteAtivasEmOrdemQuandoVitrinePublica() {
        ItemPortfolio ativa = item(1L, true, false, 0);
        when(itens.findByAtivoTrueOrderByDisplayOrderAsc()).thenReturn(List.of(ativa));

        assertThat(service.fotosAtivas()).containsExactly(ativa);
    }

    @Test
    void deveFiltrarPorEstiloQuandoEstiloInformado() {
        ItemPortfolio blackwork = item(1L, true, false, 0);
        ItemPortfolio fineline = item(2L, true, false, 1);
        fineline.setEstilo(EstiloTatuagem.FINELINE);
        when(itens.findByAtivoTrueOrderByDisplayOrderAsc()).thenReturn(List.of(blackwork, fineline));

        assertThat(service.fotosAtivas(EstiloTatuagem.FINELINE)).containsExactly(fineline);
    }

    @Test
    void deveListarTodasQuandoEstiloNulo() {
        ItemPortfolio a = item(1L, true, false, 0);
        ItemPortfolio b = item(2L, true, false, 1);
        when(itens.findByAtivoTrueOrderByDisplayOrderAsc()).thenReturn(List.of(a, b));

        assertThat(service.fotosAtivas(null)).containsExactly(a, b);
    }

    @Test
    void deveListarEstilosSemRepetirQuandoMontarFiltro() {
        ItemPortfolio a = item(1L, true, false, 0);
        ItemPortfolio b = item(2L, true, false, 1);
        ItemPortfolio c = item(3L, true, false, 2);
        c.setEstilo(EstiloTatuagem.REALISMO);
        when(itens.findByAtivoTrueOrderByDisplayOrderAsc()).thenReturn(List.of(a, b, c));

        assertThat(service.estilosDisponiveis())
                .containsExactly(EstiloTatuagem.BLACKWORK, EstiloTatuagem.REALISMO);
    }

    // ---------- ativar, ocultar e Hero ----------

    @Test
    void deveOcultarFotoQuandoDesativada() {
        ItemPortfolio foto = item(1L, true, false, 0);

        service.definirAtiva(1L, false);

        assertThat(foto.isAtivo()).isFalse();
        verify(itens).save(foto);
    }

    @Test
    void deveRetirarDoHeroQuandoFotoOcultada() {
        ItemPortfolio foto = item(1L, true, true, 0);

        service.definirAtiva(1L, false);

        assertThat(foto.isFixadoHero()).isFalse();
    }

    @Test
    void deveReativarFotoQuandoAtivada() {
        ItemPortfolio foto = item(1L, false, false, 0);

        service.definirAtiva(1L, true);

        assertThat(foto.isAtivo()).isTrue();
    }

    @Test
    void deveRejeitarAlteracaoQuandoFotoNaoExiste() {
        when(itens.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definirAtiva(99L, true))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Foto não encontrada");
    }

    @Test
    void deveFixarNoHeroQuandoFotoAtiva() {
        ItemPortfolio foto = item(1L, true, false, 0);

        service.definirHero(1L, true);

        assertThat(foto.isFixadoHero()).isTrue();
        verify(itens).save(foto);
    }

    @Test
    void deveDesafixarDoHeroQuandoSolicitado() {
        ItemPortfolio foto = item(1L, true, true, 0);

        service.definirHero(1L, false);

        assertThat(foto.isFixadoHero()).isFalse();
    }

    @Test
    void deveRejeitarFixarNoHeroQuandoFotoOculta() {
        ItemPortfolio foto = item(1L, false, false, 0);

        assertThatThrownBy(() -> service.definirHero(1L, true))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("oculta");
        assertThat(foto.isFixadoHero()).isFalse();
    }

    @Test
    void deveMostrarSomenteFixadasNoHeroQuandoHaFixadas() {
        ItemPortfolio solta = item(1L, true, false, 0);
        ItemPortfolio fixada = item(2L, true, true, 1);
        when(itens.findByAtivoTrueOrderByDisplayOrderAsc()).thenReturn(List.of(solta, fixada));

        assertThat(service.fotosHero()).containsExactly(fixada);
    }

    @Test
    void deveUsarPrimeirasAtivasNoHeroQuandoNenhumaFixada() {
        List<ItemPortfolio> ativas = List.of(item(1L, true, false, 0), item(2L, true, false, 1),
                item(3L, true, false, 2), item(4L, true, false, 3));
        when(itens.findByAtivoTrueOrderByDisplayOrderAsc()).thenReturn(ativas);

        assertThat(service.fotosHero()).containsExactlyElementsOf(ativas.subList(0, 3));
    }

    @Test
    void deveLimitarHeroATresQuandoHaMaisFixadas() {
        List<ItemPortfolio> fixadas = List.of(item(1L, true, true, 0), item(2L, true, true, 1),
                item(3L, true, true, 2), item(4L, true, true, 3));
        when(itens.findByAtivoTrueOrderByDisplayOrderAsc()).thenReturn(fixadas);

        assertThat(service.fotosHero()).hasSize(3);
    }

    // ---------- ordenacao ----------

    @Test
    void deveAplicarNovaOrdemQuandoReordenar() {
        ItemPortfolio a = item(1L, true, false, 0);
        ItemPortfolio b = item(2L, true, false, 1);
        ItemPortfolio c = item(3L, false, false, 2);
        when(itens.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(a, b, c));

        service.reordenar(List.of(3L, 1L, 2L));

        assertThat(c.getDisplayOrder()).isZero();
        assertThat(a.getDisplayOrder()).isEqualTo(1);
        assertThat(b.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void deveRejeitarOrdemQuandoFaltaFoto() {
        duasFotos();

        assertThatThrownBy(() -> service.reordenar(List.of(1L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("todas as fotos");
    }

    @Test
    void deveRejeitarOrdemQuandoIdDesconhecido() {
        duasFotos();

        assertThatThrownBy(() -> service.reordenar(List.of(1L, 99L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("todas as fotos");
    }

    @Test
    void deveRejeitarOrdemQuandoIdRepetido() {
        duasFotos();

        assertThatThrownBy(() -> service.reordenar(List.of(1L, 1L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("todas as fotos");
    }

    // ---------- entrega das imagens ----------

    @Test
    void deveEntregarCaminhoQuandoFotoPublicaAtiva() {
        item(1L, true, false, 0);

        assertThat(service.caminhoFotoPublica(1L)).isEqualTo("foto1.png");
    }

    @Test
    void naoDeveEntregarQuandoFotoOculta() {
        item(1L, false, false, 0);

        assertThatThrownBy(() -> service.caminhoFotoPublica(1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
