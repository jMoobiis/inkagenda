package br.com.inkagenda.web;

import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.service.AnamneseService;
import br.com.inkagenda.service.ArmazenamentoArquivos;
import br.com.inkagenda.service.ArquivoEnviado;
import br.com.inkagenda.service.LembreteVespera;
import br.com.inkagenda.service.OrcamentoService;
import br.com.inkagenda.service.PainelService;
import br.com.inkagenda.service.RecursoNaoEncontradoException;
import br.com.inkagenda.service.RegistroSinal;
import br.com.inkagenda.service.RegraNegocioException;
import br.com.inkagenda.service.RespostaOrcamento;
import br.com.inkagenda.service.ResultadoSinal;
import br.com.inkagenda.service.SinalService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Painel do tatuador: exige login pela regra de /admin/** em SegurancaConfig. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final PainelService painel;
    private final OrcamentoService orcamento;
    private final SinalService sinal;
    private final AnamneseService anamnese;
    private final ArmazenamentoArquivos armazenamento;
    private final Clock relogio;
    private final String baseUrl;

    public AdminController(PainelService painel, OrcamentoService orcamento, SinalService sinal,
            AnamneseService anamnese, ArmazenamentoArquivos armazenamento, Clock relogio,
            @Value("${inkagenda.base-url}") String baseUrl) {
        this.painel = painel;
        this.orcamento = orcamento;
        this.sinal = sinal;
        this.anamnese = anamnese;
        this.armazenamento = armazenamento;
        this.relogio = relogio;
        this.baseUrl = baseUrl;
    }

    @GetMapping
    public String painel(Model model) {
        model.addAttribute("porStatus", painel.painel());
        return "admin/painel";
    }

    // ---------- orcamento (US03) ----------

    @GetMapping("/solicitacoes/{id}")
    public String solicitacao(@PathVariable Long id, Model model) {
        Solicitacao solicitacao = painel.solicitacao(id);
        model.addAttribute("solicitacao", solicitacao);
        model.addAttribute("sessao", painel.sessaoAtiva(id).orElse(null));
        if (solicitacao.getTokenAgendamento() != null) {
            model.addAttribute("linkWhatsApp", orcamento.gerarLinkWhatsApp(solicitacao));
        }
        return "admin/solicitacao";
    }

    @PostMapping("/solicitacoes/{id}/orcamento")
    public String responder(@PathVariable Long id, @RequestParam BigDecimal valorTotal,
            @RequestParam Integer duracaoEstimadaHoras, @RequestParam BigDecimal valorSinal,
            RedirectAttributes redirecionamento) {
        executar(redirecionamento, "Orçamento salvo", () -> orcamento.responderOrcamento(id,
                new RespostaOrcamento(valorTotal, duracaoEstimadaHoras, valorSinal)));
        return "redirect:/admin/solicitacoes/" + id;
    }

    /** Imagem de referencia: privada, entregue somente ao tatuador autenticado. */
    @GetMapping("/solicitacoes/{id}/imagens/{imagemId}")
    @ResponseBody
    public ResponseEntity<byte[]> imagem(@PathVariable Long id, @PathVariable Long imagemId) {
        String caminho = painel.caminhoImagem(id, imagemId);
        return ArquivosWeb.imagem(caminho, armazenamento.carregar(caminho),
                CacheControl.noStore());
    }

    // ---------- sessao, sinal e remarcacao (US05, US08) ----------

    @GetMapping("/sessoes/{id}")
    public String sessao(@PathVariable Long id, Model model) {
        Sessao sessao = painel.sessao(id);
        model.addAttribute("sessao", sessao);
        anamnese.fichaDaSessao(id).ifPresent(ficha -> {
            model.addAttribute("ficha", ficha);
            model.addAttribute("linkFicha", baseUrl + "/anamnese/" + ficha.getToken());
        });
        return "admin/sessao";
    }

    @PostMapping("/sessoes/{id}/sinal")
    public String registrarSinal(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataPagamento,
            @RequestParam(required = false) BigDecimal valorRecebido,
            @RequestParam(required = false) MultipartFile comprovante,
            @RequestParam(defaultValue = "false") boolean confirmacaoDivergencia,
            RedirectAttributes redirecionamento) {
        try {
            ArquivoEnviado arquivo = ArquivosWeb.arquivo(comprovante);
            String caminho = arquivo == null ? null : painel.salvarComprovante(arquivo);
            ResultadoSinal resultado = sinal.registrarSinal(id, new RegistroSinal(dataPagamento,
                    valorRecebido, caminho, confirmacaoDivergencia));
            redirecionamento.addFlashAttribute("mensagem",
                    "Sinal registrado. Valor a abater: " + resultado.valorAAbater());
        } catch (RecursoNaoEncontradoException e) {
            throw e;
        } catch (RegraNegocioException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/sessoes/" + id;
    }

    @GetMapping("/sessoes/{id}/comprovante")
    @ResponseBody
    public ResponseEntity<byte[]> comprovante(@PathVariable Long id) {
        String caminho = painel.caminhoComprovante(id);
        return ArquivosWeb.imagem(caminho, armazenamento.carregar(caminho),
                CacheControl.noStore());
    }

    @PostMapping("/sessoes/{id}/remarcar")
    public String remarcar(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime novoInicio,
            @RequestParam(defaultValue = "false") boolean confirmacaoExplicitaPerdaSinal,
            RedirectAttributes redirecionamento) {
        executar(redirecionamento, "Sessão remarcada", () -> painel.remarcarSessao(id, novoInicio,
                confirmacaoExplicitaPerdaSinal));
        return "redirect:/admin/sessoes/" + id;
    }

    @PostMapping("/sessoes/{id}/cancelar")
    public String cancelar(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean confirmacaoExplicitaPerdaSinal,
            RedirectAttributes redirecionamento) {
        executar(redirecionamento, "Sessão cancelada", () -> painel.cancelarSessao(id,
                confirmacaoExplicitaPerdaSinal));
        return "redirect:/admin/sessoes/" + id;
    }

    /** Respostas de saude: leitura somente aqui, pelo tatuador autenticado (US07, CA3). */
    @GetMapping("/sessoes/{id}/anamnese")
    public String anamnese(@PathVariable Long id, Model model) {
        model.addAttribute("sessao", painel.sessao(id));
        model.addAttribute("ficha", anamnese.fichaDaSessao(id).orElse(null));
        return "admin/anamnese";
    }

    // ---------- agenda e bloqueios (US04) ----------

    @GetMapping("/agenda")
    public String agenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
            Model model) {
        model.addAttribute("agenda", painel.agendaDoDia(dia != null ? dia : LocalDate.now(relogio)));
        return "admin/agenda";
    }

    @PostMapping("/bloqueios")
    public String criarBloqueio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @RequestParam(required = false) String descricao,
            RedirectAttributes redirecionamento) {
        executar(redirecionamento, "Bloqueio criado",
                () -> painel.criarBloqueio(inicio, fim, descricao));
        return "redirect:/admin/agenda?dia=" + inicio.toLocalDate();
    }

    @PostMapping("/bloqueios/{id}/remover")
    public String removerBloqueio(@PathVariable Long id, RedirectAttributes redirecionamento) {
        executar(redirecionamento, "Bloqueio removido", () -> painel.removerBloqueio(id));
        return "redirect:/admin/agenda";
    }

    // ---------- vespera (US06) ----------

    @GetMapping("/vespera")
    public String vespera(Model model) {
        List<LembreteVespera> lembretes = sinal.sessoesDeAmanha().stream()
                .map(sinal::lembreteVespera).toList();
        model.addAttribute("lembretes", lembretes);
        return "admin/vespera";
    }

    @PostMapping("/sessoes/{id}/presenca")
    public String confirmarPresenca(@PathVariable Long id, RedirectAttributes redirecionamento) {
        executar(redirecionamento, "Presença confirmada", () -> sinal.confirmarPresenca(id));
        return "redirect:/admin/vespera";
    }

    /** Erro de regra volta como aviso na tela; registro inexistente segue para o 404. */
    static void executar(RedirectAttributes redirecionamento, String sucesso, Runnable acao) {
        try {
            acao.run();
            redirecionamento.addFlashAttribute("mensagem", sucesso);
        } catch (RecursoNaoEncontradoException e) {
            throw e;
        } catch (RegraNegocioException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
        }
    }
}
