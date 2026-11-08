package br.com.inkagenda.service;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.domain.enums.ModoLayout;
import br.com.inkagenda.domain.model.ConfiguracaoVitrine;
import br.com.inkagenda.domain.model.ItemPortfolio;
import br.com.inkagenda.domain.repository.ConfiguracaoVitrineRepository;
import br.com.inkagenda.domain.repository.ItemPortfolioRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VitrineService {

    private static final int MAXIMO_HERO = 3;

    private final ItemPortfolioRepository itens;
    private final ConfiguracaoVitrineRepository configuracoes;
    private final ArmazenamentoArquivos armazenamento;

    public VitrineService(ItemPortfolioRepository itens,
            ConfiguracaoVitrineRepository configuracoes, ArmazenamentoArquivos armazenamento) {
        this.itens = itens;
        this.configuracoes = configuracoes;
        this.armazenamento = armazenamento;
    }

    @Transactional
    public ItemPortfolio adicionarFoto(NovaFoto foto) {
        if (foto.titulo() == null || foto.titulo().isBlank()) {
            throw new RegraNegocioException("Informe o título da foto");
        }
        if (foto.estilo() == null) {
            throw new RegraNegocioException("Informe o estilo da foto");
        }
        if (foto.arquivo() == null) {
            throw new RegraNegocioException("Anexe a imagem da foto");
        }
        ValidadorArquivoReferencia.validar(foto.arquivo());

        ItemPortfolio item = new ItemPortfolio();
        item.setTitulo(foto.titulo());
        item.setEstilo(foto.estilo());
        item.setTagDestaque(foto.tagDestaque());
        item.setDisplayOrder(foto.displayOrder() != null ? foto.displayOrder() : proximaOrdem());
        item.setCaminhoImagem(armazenamento.salvar(foto.arquivo()));
        return itens.save(item);
    }

    @Transactional(readOnly = true)
    public ModoLayout modoAtual() {
        return configuracoes.findFirstByOrderByIdAsc().map(ConfiguracaoVitrine::getModoLayout)
                .orElse(ModoLayout.STEEZY_SPLIT);
    }

    @Transactional
    public ConfiguracaoVitrine alterarModo(ModoLayout modo) {
        if (modo == null) {
            throw new RegraNegocioException("Informe o modo de exibição");
        }
        ConfiguracaoVitrine config = configuracoes.findFirstByOrderByIdAsc()
                .orElseGet(ConfiguracaoVitrine::new);
        config.setModoLayout(modo);
        return configuracoes.save(config);
    }

    @Transactional(readOnly = true)
    public List<ItemPortfolio> todasAsFotos() {
        return itens.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<ItemPortfolio> fotosAtivas() {
        return itens.findByAtivoTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<ItemPortfolio> fotosAtivas(EstiloTatuagem estilo) {
        if (estilo == null) {
            return fotosAtivas();
        }
        return fotosAtivas().stream().filter(foto -> foto.getEstilo() == estilo).toList();
    }

    @Transactional(readOnly = true)
    public List<EstiloTatuagem> estilosDisponiveis() {
        return fotosAtivas().stream().map(ItemPortfolio::getEstilo).distinct().toList();
    }

    /** Fotos fixadas no Hero; sem nenhuma fixada, as primeiras ativas. */
    @Transactional(readOnly = true)
    public List<ItemPortfolio> fotosHero() {
        List<ItemPortfolio> ativas = fotosAtivas();
        List<ItemPortfolio> fixadas = ativas.stream().filter(ItemPortfolio::isFixadoHero).toList();
        return (fixadas.isEmpty() ? ativas : fixadas).stream().limit(MAXIMO_HERO).toList();
    }

    /** Ocultar tira a foto do Hero, para que reativar nao a fixe de novo sem pedido. */
    @Transactional
    public ItemPortfolio definirAtiva(Long id, boolean ativa) {
        ItemPortfolio item = buscar(id);
        item.setAtivo(ativa);
        if (!ativa) {
            item.setFixadoHero(false);
        }
        return itens.save(item);
    }

    @Transactional
    public ItemPortfolio definirHero(Long id, boolean fixar) {
        ItemPortfolio item = buscar(id);
        if (fixar && !item.isAtivo()) {
            throw new RegraNegocioException("Uma foto oculta não pode ser fixada no Hero");
        }
        item.setFixadoHero(fixar);
        return itens.save(item);
    }

    /** Aplica a ordem informada, que precisa listar cada foto uma unica vez. */
    @Transactional
    public void reordenar(List<Long> idsNaOrdem) {
        Map<Long, ItemPortfolio> porId = itens.findAllByOrderByDisplayOrderAsc().stream()
                .collect(Collectors.toMap(ItemPortfolio::getId, Function.identity()));
        if (idsNaOrdem.size() != porId.size() || !new HashSet<>(idsNaOrdem).equals(porId.keySet())) {
            throw new RegraNegocioException("A nova ordem precisa conter todas as fotos uma vez");
        }
        for (int i = 0; i < idsNaOrdem.size(); i++) {
            ItemPortfolio item = porId.get(idsNaOrdem.get(i));
            item.setDisplayOrder(i);
            itens.save(item);
        }
    }

    /** Caminho da imagem para a rota publica: foto oculta nao e entregue. */
    @Transactional(readOnly = true)
    public String caminhoFotoPublica(Long id) {
        ItemPortfolio item = buscar(id);
        if (!item.isAtivo()) {
            throw new RecursoNaoEncontradoException("Foto não encontrada");
        }
        return item.getCaminhoImagem();
    }

    /** Caminho da imagem para o painel, que tambem mostra as fotos ocultas. */
    @Transactional(readOnly = true)
    public String caminhoFotoAdmin(Long id) {
        return buscar(id).getCaminhoImagem();
    }

    private ItemPortfolio buscar(Long id) {
        return itens.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto não encontrada"));
    }

    private int proximaOrdem() {
        return itens.findAllByOrderByDisplayOrderAsc().stream()
                .mapToInt(ItemPortfolio::getDisplayOrder).max().orElse(-1) + 1;
    }
}
