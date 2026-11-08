package br.com.inkagenda.web;

import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.service.AgendaService;
import br.com.inkagenda.service.RegraNegocioException;
import br.com.inkagenda.service.SinalService;
import br.com.inkagenda.service.TokenExpiradoException;
import br.com.inkagenda.service.TokenInvalidoException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Escolha de horario e instrucoes do Pix pelo link enviado ao cliente (US05, UC02). */
@Controller
public class AgendamentoController {

    private final SinalService sinal;
    private final AgendaService agenda;
    private final Clock relogio;

    public AgendamentoController(SinalService sinal, AgendaService agenda, Clock relogio) {
        this.sinal = sinal;
        this.agenda = agenda;
        this.relogio = relogio;
    }

    @GetMapping("/agendar/{token}")
    public String escolherHorario(@PathVariable String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
            Model model) {
        Solicitacao solicitacao = sinal.solicitacaoPorToken(token);
        if (solicitacao.getStatus() == StatusSolicitacao.AGUARDANDO_SINAL) {
            return "redirect:/agendar/" + token + "/pix";
        }
        if (solicitacao.getStatus() != StatusSolicitacao.ORCADA) {
            throw new TokenExpiradoException("Este orçamento não está mais disponível para agendamento");
        }
        LocalDate amanha = LocalDate.now(relogio).plusDays(1);
        LocalDate diaEscolhido = dia != null && !dia.isBefore(amanha) ? dia : amanha;
        model.addAttribute("solicitacao", solicitacao);
        model.addAttribute("token", token);
        model.addAttribute("dia", diaEscolhido);
        model.addAttribute("diaMinimo", amanha);
        model.addAttribute("horarios",
                agenda.horariosDisponiveis(diaEscolhido, solicitacao.getDuracaoEstimadaHoras()));
        return "agendar";
    }

    @PostMapping("/agendar/{token}")
    public String reservarHorario(@PathVariable String token,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            RedirectAttributes redirecionamento) {
        try {
            sinal.escolherHorario(token, inicio);
        } catch (TokenInvalidoException e) {
            throw e;
        } catch (RegraNegocioException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
            return "redirect:/agendar/" + token;
        }
        return "redirect:/agendar/" + token + "/pix";
    }

    @GetMapping("/agendar/{token}/pix")
    public String instrucoesPix(@PathVariable String token, Model model) {
        Solicitacao solicitacao = sinal.solicitacaoPorToken(token);
        Optional<Sessao> preReserva = sinal.preReservaDe(solicitacao);
        if (preReserva.isEmpty()) {
            return "redirect:/agendar/" + token;
        }
        model.addAttribute("solicitacao", solicitacao);
        model.addAttribute("sessao", preReserva.get());
        model.addAttribute("chavePix", sinal.configuracao().getChavePix());
        return "pix";
    }
}
