package br.com.inkagenda.web;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.service.ArmazenamentoArquivos;
import br.com.inkagenda.service.OrcamentoService;
import br.com.inkagenda.service.RegraNegocioException;
import br.com.inkagenda.service.VitrineService;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PublicoController {

    private final VitrineService vitrine;
    private final OrcamentoService orcamento;
    private final ArmazenamentoArquivos armazenamento;

    public PublicoController(VitrineService vitrine, OrcamentoService orcamento,
            ArmazenamentoArquivos armazenamento) {
        this.vitrine = vitrine;
        this.orcamento = orcamento;
        this.armazenamento = armazenamento;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) EstiloTatuagem estilo, Model model) {
        model.addAttribute("modo", vitrine.modoAtual());
        model.addAttribute("hero", vitrine.fotosHero());
        model.addAttribute("fotos", vitrine.fotosAtivas(estilo));
        model.addAttribute("estilos", vitrine.estilosDisponiveis());
        model.addAttribute("estiloSelecionado", estilo);
        return "home";
    }

    /** Somente fotos ativas sao publicas; a rota nao da acesso a outros arquivos. */
    @GetMapping("/portfolio/{id}/imagem")
    @ResponseBody
    public ResponseEntity<byte[]> fotoPublica(@PathVariable Long id) {
        String caminho = vitrine.caminhoFotoPublica(id);
        return ArquivosWeb.imagem(caminho, armazenamento.carregar(caminho),
                CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic());
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/orcamento")
    public String formularioOrcamento(Model model) {
        prepararFormulario(model, new OrcamentoForm());
        return "orcamento";
    }

    @PostMapping("/orcamento")
    public String enviarOrcamento(@Valid @ModelAttribute("form") OrcamentoForm form,
            BindingResult validacao, Model model) {
        model.addAttribute("estilosDisponiveis", EstiloTatuagem.values());
        if (validacao.hasErrors()) {
            return "orcamento";
        }
        try {
            orcamento.criarSolicitacao(form.paraSolicitacao());
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return "orcamento";
        }
        return "redirect:/orcamento/enviado";
    }

    @GetMapping("/orcamento/enviado")
    public String orcamentoEnviado() {
        return "orcamento-enviado";
    }

    private void prepararFormulario(Model model, OrcamentoForm form) {
        model.addAttribute("form", form);
        model.addAttribute("estilosDisponiveis", EstiloTatuagem.values());
    }
}
