package br.com.inkagenda.service;

import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.enums.TipoImagem;
import br.com.inkagenda.domain.model.Cliente;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.ImagemReferencia;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.ClienteRepository;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrcamentoService {

    private final SolicitacaoRepository solicitacoes;
    private final ClienteRepository clientes;
    private final ConfiguracaoTatuadorRepository configuracoes;
    private final ArmazenamentoArquivos armazenamento;
    private final Clock relogio;
    private final String baseUrl;

    public OrcamentoService(SolicitacaoRepository solicitacoes, ClienteRepository clientes,
            ConfiguracaoTatuadorRepository configuracoes, ArmazenamentoArquivos armazenamento,
            Clock relogio, @Value("${inkagenda.base-url}") String baseUrl) {
        this.solicitacoes = solicitacoes;
        this.clientes = clientes;
        this.configuracoes = configuracoes;
        this.armazenamento = armazenamento;
        this.relogio = relogio;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public Solicitacao criarSolicitacao(NovaSolicitacao dados) {
        if (!dados.aceitePrivacidade()) {
            throw new RegraNegocioException("O aceite do Aviso de Privacidade é obrigatório");
        }
        NumeroWhatsapp whatsapp = NumeroWhatsapp.de(dados.whatsapp());
        validarImagens(dados);

        LocalDateTime agora = LocalDateTime.now(relogio);
        Solicitacao solicitacao = montarSolicitacao(dados, vincularCliente(dados, whatsapp, agora),
                agora);
        anexarImagens(solicitacao, dados);
        return solicitacoes.save(solicitacao);
    }

    public BigDecimal sugerirSinal(BigDecimal valorTotal) {
        return valorTotal.multiply(BigDecimal.valueOf(configuracao().getPercentualSinal()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Solicitacao responderOrcamento(Long solicitacaoId, RespostaOrcamento resposta) {
        Solicitacao solicitacao = solicitacoes.findById(solicitacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada"));
        validarStatusParaResposta(solicitacao.getStatus());
        validarValores(resposta);

        solicitacao.setValorTotal(resposta.valorTotal());
        solicitacao.setValorSinal(resposta.valorSinal());
        solicitacao.setDuracaoEstimadaHoras(resposta.duracaoEstimadaHoras());
        solicitacao.setStatus(StatusSolicitacao.ORCADA);
        solicitacao.setOrcadaEm(LocalDateTime.now(relogio));
        if (solicitacao.getTokenAgendamento() == null) {
            solicitacao.setTokenAgendamento(TokenAcesso.gerar());
        }
        return solicitacoes.save(solicitacao);
    }

    public String gerarLinkWhatsApp(Solicitacao solicitacao) {
        return GeradorLinkWhatsApp.linkOrcamento(solicitacao, baseUrl);
    }

    public boolean estaSemRetorno(Solicitacao solicitacao) {
        if (solicitacao.getStatus() != StatusSolicitacao.ORCADA
                || solicitacao.getOrcadaEm() == null) {
            return false;
        }
        LocalDateTime limite = LocalDateTime.now(relogio)
                .minusDays(configuracao().getDiasSemRetorno());
        return solicitacao.getOrcadaEm().isBefore(limite);
    }

    private void validarImagens(NovaSolicitacao dados) {
        ValidadorArquivoReferencia.validarConjunto(dados.imagensReferencia());
        if (dados.cobertura()) {
            if (dados.fotoTatuagemAtual() == null) {
                throw new RegraNegocioException(
                        "Anexe a foto da tatuagem atual para orçamento de cobertura");
            }
            ValidadorArquivoReferencia.validar(dados.fotoTatuagemAtual());
        }
    }

    private Cliente vincularCliente(NovaSolicitacao dados, NumeroWhatsapp whatsapp,
            LocalDateTime agora) {
        return clientes.findByWhatsapp(whatsapp.digitos())
                .orElseGet(() -> clientes.save(
                        new Cliente(dados.nome(), whatsapp.digitos(), agora)));
    }

    private Solicitacao montarSolicitacao(NovaSolicitacao dados, Cliente cliente,
            LocalDateTime agora) {
        Solicitacao solicitacao = new Solicitacao();
        solicitacao.setCliente(cliente);
        solicitacao.setTamanhoCm(dados.tamanhoCm());
        solicitacao.setRegiaoCorpo(dados.regiaoCorpo());
        solicitacao.setEstilo(dados.estilo());
        solicitacao.setCobertura(dados.cobertura());
        solicitacao.setDescricao(dados.descricao());
        solicitacao.setPreferenciaDias(dados.preferenciaDias());
        solicitacao.setAceitePrivacidade(true);
        solicitacao.setStatus(StatusSolicitacao.NOVA);
        solicitacao.setCriadaEm(agora);
        return solicitacao;
    }

    private void anexarImagens(Solicitacao solicitacao, NovaSolicitacao dados) {
        dados.imagensReferencia().forEach(imagem ->
                anexar(solicitacao, imagem, TipoImagem.REFERENCIA));
        if (dados.cobertura()) {
            anexar(solicitacao, dados.fotoTatuagemAtual(), TipoImagem.TATUAGEM_ATUAL);
        }
    }

    private void anexar(Solicitacao solicitacao, ArquivoEnviado arquivo, TipoImagem tipo) {
        solicitacao.getImagens().add(
                new ImagemReferencia(solicitacao, armazenamento.salvar(arquivo), tipo));
    }

    private void validarStatusParaResposta(StatusSolicitacao status) {
        if (status != StatusSolicitacao.NOVA && status != StatusSolicitacao.ORCADA) {
            throw new RegraNegocioException(
                    "O orçamento não pode ser respondido no status " + status);
        }
    }

    private void validarValores(RespostaOrcamento resposta) {
        BigDecimal total = resposta.valorTotal();
        BigDecimal sinal = resposta.valorSinal();
        Integer horas = resposta.duracaoEstimadaHoras();
        if (total == null || sinal == null || horas == null || total.signum() <= 0
                || sinal.signum() <= 0 || horas <= 0) {
            throw new RegraNegocioException("Cada valor informado deve ser maior que zero");
        }
        if (sinal.compareTo(total) > 0) {
            throw new RegraNegocioException("O valor do sinal não pode ser maior que o valor total");
        }
    }

    private ConfiguracaoTatuador configuracao() {
        return configuracoes.findFirstByOrderByIdAsc().orElseGet(ConfiguracaoTatuador::new);
    }
}
