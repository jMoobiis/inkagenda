package br.com.inkagenda.web;

import br.com.inkagenda.service.RecursoNaoEncontradoException;
import br.com.inkagenda.service.RegraNegocioException;
import br.com.inkagenda.service.TokenExpiradoException;
import br.com.inkagenda.service.TokenInvalidoException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Transforma as excecoes de negocio em paginas de erro amigaveis (US07, CA3). */
@ControllerAdvice
public class TratamentoErros {

    @ExceptionHandler({TokenInvalidoException.class, RecursoNaoEncontradoException.class})
    public String naoEncontrado(RegraNegocioException e, Model model,
            HttpServletResponse resposta) {
        return pagina(HttpStatus.NOT_FOUND, e.getMessage(), model, resposta);
    }

    @ExceptionHandler(TokenExpiradoException.class)
    public String expirado(TokenExpiradoException e, Model model, HttpServletResponse resposta) {
        return pagina(HttpStatus.FORBIDDEN, e.getMessage(), model, resposta);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public String regraDeNegocio(RegraNegocioException e, Model model,
            HttpServletResponse resposta) {
        return pagina(HttpStatus.BAD_REQUEST, e.getMessage(), model, resposta);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String arquivoGrande(Model model, HttpServletResponse resposta) {
        return pagina(HttpStatus.BAD_REQUEST, "Cada imagem deve ter até 5MB", model, resposta);
    }

    private static String pagina(HttpStatus status, String mensagem, Model model,
            HttpServletResponse resposta) {
        resposta.setStatus(status.value());
        model.addAttribute("status", status.value());
        model.addAttribute("mensagem", mensagem);
        return "error";
    }
}
