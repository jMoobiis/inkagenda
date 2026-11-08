package br.com.inkagenda.web;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.domain.enums.ModoLayout;
import br.com.inkagenda.service.ArmazenamentoArquivos;
import br.com.inkagenda.service.NovaFoto;
import br.com.inkagenda.service.VitrineService;
import java.util.List;
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

/** Gerenciar Portfolio e Vitrine. */
@Controller
@RequestMapping("/admin/vitrine")
public class AdminVitrineController {

    private static final String VOLTAR = "redirect:/admin/vitrine";

    private final VitrineService vitrine;
    private final ArmazenamentoArquivos armazenamento;

    public AdminVitrineController(VitrineService vitrine, ArmazenamentoArquivos armazenamento) {
        this.vitrine = vitrine;
        this.armazenamento = armazenamento;
    }

    @GetMapping
    public String vitrine(Model model) {
        model.addAttribute("fotos", vitrine.todasAsFotos());
        model.addAttribute("modo", vitrine.modoAtual());
        model.addAttribute("modos", ModoLayout.values());
        model.addAttribute("estilos", EstiloTatuagem.values());
        return "admin/vitrine";
    }

    @GetMapping("/fotos/{id}/imagem")
    @ResponseBody
    public ResponseEntity<byte[]> imagem(@PathVariable Long id) {
        String caminho = vitrine.caminhoFotoAdmin(id);
        return ArquivosWeb.imagem(caminho, armazenamento.carregar(caminho),
                CacheControl.noStore());
    }

    @PostMapping("/fotos")
    public String adicionar(@RequestParam(required = false) String titulo,
            @RequestParam(required = false) EstiloTatuagem estilo,
            @RequestParam(required = false) String tagDestaque,
            @RequestParam(required = false) Integer displayOrder,
            @RequestParam(required = false) MultipartFile arquivo,
            RedirectAttributes redirecionamento) {
        AdminController.executar(redirecionamento, "Foto adicionada",
                () -> vitrine.adicionarFoto(new NovaFoto(titulo, estilo, tagDestaque, displayOrder,
                        ArquivosWeb.arquivo(arquivo))));
        return VOLTAR;
    }

    @PostMapping("/modo")
    public String alterarModo(@RequestParam ModoLayout modo, RedirectAttributes redirecionamento) {
        AdminController.executar(redirecionamento, "Modo de exibição alterado",
                () -> vitrine.alterarModo(modo));
        return VOLTAR;
    }

    @PostMapping("/fotos/{id}/ativa")
    public String definirAtiva(@PathVariable Long id, @RequestParam boolean valor,
            RedirectAttributes redirecionamento) {
        AdminController.executar(redirecionamento, valor ? "Foto ativada" : "Foto ocultada",
                () -> vitrine.definirAtiva(id, valor));
        return VOLTAR;
    }

    @PostMapping("/fotos/{id}/hero")
    public String definirHero(@PathVariable Long id, @RequestParam boolean valor,
            RedirectAttributes redirecionamento) {
        AdminController.executar(redirecionamento, valor ? "Foto fixada no Hero" : "Foto retirada do Hero",
                () -> vitrine.definirHero(id, valor));
        return VOLTAR;
    }

    @PostMapping("/ordem")
    public String reordenar(@RequestParam List<Long> ids, RedirectAttributes redirecionamento) {
        AdminController.executar(redirecionamento, "Ordem atualizada", () -> vitrine.reordenar(ids));
        return VOLTAR;
    }
}
