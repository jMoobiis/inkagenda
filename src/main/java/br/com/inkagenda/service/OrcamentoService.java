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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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

        String digitos = dados.whatsapp() == null ? "" : dados.whatsapp().replaceAll("\\D", "");
        if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() != 10 && digitos.length() != 11) {
            throw new RegraNegocioException("Informe um WhatsApp válido com DDD");
        }

        List<ArquivoEnviado> imagens = dados.imagensReferencia();
        if (imagens == null || imagens.isEmpty()) {
            throw new RegraNegocioException("Anexe ao menos uma imagem de referência");
        }
        if (imagens.size() > 5) {
            throw new RegraNegocioException("Envie no máximo 5 imagens de referência");
        }
        for (ArquivoEnviado imagem : imagens) {
            validarArquivo(imagem);
        }
        if (dados.cobertura()) {
            if (dados.fotoTatuagemAtual() == null) {
                throw new RegraNegocioException(
                        "Anexe a foto da tatuagem atual para orçamento de cobertura");
            }
            validarArquivo(dados.fotoTatuagemAtual());
        }

        LocalDateTime agora = LocalDateTime.now(relogio);
        String whatsapp = digitos;
        Cliente cliente = clientes.findByWhatsapp(whatsapp)
                .orElseGet(() -> clientes.save(new Cliente(dados.nome(), whatsapp, agora)));

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

        for (ArquivoEnviado imagem : imagens) {
            solicitacao.getImagens().add(new ImagemReferencia(solicitacao,
                    armazenamento.salvar(imagem), TipoImagem.REFERENCIA));
        }
        if (dados.cobertura()) {
            solicitacao.getImagens().add(new ImagemReferencia(solicitacao,
                    armazenamento.salvar(dados.fotoTatuagemAtual()), TipoImagem.TATUAGEM_ATUAL));
        }
        return solicitacoes.save(solicitacao);
    }

    private void validarArquivo(ArquivoEnviado arquivo) {
        if (arquivo.tamanhoBytes() > 5L * 1024 * 1024) {
            throw new RegraNegocioException("Cada imagem deve ter até 5MB");
        }
        String nome = arquivo.nomeOriginal() == null ? ""
                : arquivo.nomeOriginal().toLowerCase(Locale.ROOT);
        byte[] c = arquivo.conteudo();
        boolean png = nome.endsWith(".png") && c != null && c.length >= 8
                && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G';
        boolean jpg = (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) && c != null
                && c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8
                && (c[2] & 0xFF) == 0xFF;
        if (!png && !jpg) {
            throw new RegraNegocioException("As imagens devem estar em formato JPG ou PNG");
        }
    }

    public BigDecimal sugerirSinal(BigDecimal valorTotal) {
        return valorTotal.multiply(BigDecimal.valueOf(configuracao().getPercentualSinal()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Solicitacao responderOrcamento(Long solicitacaoId, RespostaOrcamento resposta) {
        Solicitacao solicitacao = solicitacoes.findById(solicitacaoId)
                .orElseThrow(() -> new RegraNegocioException("Solicitação não encontrada"));

        StatusSolicitacao status = solicitacao.getStatus();
        if (status != StatusSolicitacao.NOVA && status != StatusSolicitacao.ORCADA) {
            throw new RegraNegocioException(
                    "O orçamento não pode ser respondido no status " + status);
        }

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

        solicitacao.setValorTotal(total);
        solicitacao.setValorSinal(sinal);
        solicitacao.setDuracaoEstimadaHoras(horas);
        solicitacao.setStatus(StatusSolicitacao.ORCADA);
        solicitacao.setOrcadaEm(LocalDateTime.now(relogio));
        if (solicitacao.getTokenAgendamento() == null) {
            solicitacao.setTokenAgendamento(UUID.randomUUID().toString().replace("-", ""));
        }
        return solicitacoes.save(solicitacao);
    }

    public String gerarLinkWhatsApp(Solicitacao solicitacao) {
        DecimalFormat moeda = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        Cliente cliente = solicitacao.getCliente();
        String primeiroNome = cliente.getNome().split(" ")[0];
        String texto = "Olá, " + primeiroNome + "! Aqui é o Gusta. Seu orçamento ficou pronto:\n"
                + "Valor total: R$ " + moeda.format(solicitacao.getValorTotal()) + "\n"
                + "Duração estimada: " + solicitacao.getDuracaoEstimadaHoras() + "h\n"
                + "Sinal: R$ " + moeda.format(solicitacao.getValorSinal()) + "\n"
                + "Escolha seu horário: " + baseUrl + "/agendar/"
                + solicitacao.getTokenAgendamento();
        String codificado = URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/55" + cliente.getWhatsapp() + "?text=" + codificado;
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

    private ConfiguracaoTatuador configuracao() {
        return configuracoes.findFirstByOrderByIdAsc().orElseGet(ConfiguracaoTatuador::new);
    }
}
