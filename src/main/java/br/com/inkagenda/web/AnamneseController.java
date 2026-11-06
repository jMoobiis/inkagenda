package br.com.inkagenda.web;

import br.com.inkagenda.service.AnamneseService;
import br.com.inkagenda.service.RegraNegocioException;
import br.com.inkagenda.service.TokenExpiradoException;
import br.com.inkagenda.service.TokenInvalidoException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** Ficha de anamnese pelo link: respostas de saude nunca voltam para a tela publica (US07). */
@Controller
public class AnamneseController {

    private final AnamneseService anamnese;

    public AnamneseController(AnamneseService anamnese) {
        this.anamnese = anamnese;
    }

    @GetMapping("/anamnese/{token}")
    public String ficha(@PathVariable String token, Model model) {
        anamnese.abrirPorToken(token);
        model.addAttribute("form", new FichaForm());
        model.addAttribute("token", token);
        return "anamnese";
    }

    @PostMapping("/anamnese/{token}")
    public String enviar(@PathVariable String token, @Valid @ModelAttribute("form") FichaForm form,
            BindingResult validacao, Model model) {
        model.addAttribute("token", token);
        if (validacao.hasErrors()) {
            return "anamnese";
        }
        try {
            anamnese.enviarFicha(token, form.paraDados());
        } catch (TokenInvalidoException | TokenExpiradoException e) {
            throw e;
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return "anamnese";
        }
        return "redirect:/anamnese/concluida";
    }

    @GetMapping("/anamnese/concluida")
    public String concluida() {
        return "anamnese-concluida";
    }
}
