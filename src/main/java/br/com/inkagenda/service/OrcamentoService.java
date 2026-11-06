package br.com.inkagenda.service;

import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.ClienteRepository;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import java.math.BigDecimal;
import java.time.Clock;

public class OrcamentoService {

    public OrcamentoService(SolicitacaoRepository solicitacoes, ClienteRepository clientes,
            ConfiguracaoTatuadorRepository configuracoes, ArmazenamentoArquivos armazenamento,
            Clock relogio, String baseUrl) {
    }

    public Solicitacao criarSolicitacao(NovaSolicitacao dados) {
        throw new UnsupportedOperationException("Ainda nao implementado");
    }

    public BigDecimal sugerirSinal(BigDecimal valorTotal) {
        throw new UnsupportedOperationException("Ainda nao implementado");
    }

    public Solicitacao responderOrcamento(Long solicitacaoId, RespostaOrcamento resposta) {
        throw new UnsupportedOperationException("Ainda nao implementado");
    }

    public String gerarLinkWhatsApp(Solicitacao solicitacao) {
        throw new UnsupportedOperationException("Ainda nao implementado");
    }

    public boolean estaSemRetorno(Solicitacao solicitacao) {
        throw new UnsupportedOperationException("Ainda nao implementado");
    }
}
