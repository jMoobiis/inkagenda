package br.com.inkagenda.service;

import br.com.inkagenda.domain.model.Solicitacao;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Monta links wa.me com a mensagem pronta. */
public final class GeradorLinkWhatsApp {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private GeradorLinkWhatsApp() {
    }

    public static String link(NumeroWhatsapp numero, String mensagem) {
        String codificada = URLEncoder.encode(mensagem, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "https://wa.me/" + numero.comCodigoPais() + "?text=" + codificada;
    }

    public static String linkOrcamento(Solicitacao solicitacao, String baseUrl) {
        String primeiroNome = solicitacao.getCliente().getNome().split(" ")[0];
        String mensagem = "Olá, " + primeiroNome + "! Aqui é o Gusta. Seu orçamento ficou pronto:\n"
                + "Valor total: " + reais(solicitacao.getValorTotal()) + "\n"
                + "Duração estimada: " + solicitacao.getDuracaoEstimadaHoras() + "h\n"
                + "Sinal: " + reais(solicitacao.getValorSinal()) + "\n"
                + "Escolha seu horário: " + baseUrl + "/agendar/"
                + solicitacao.getTokenAgendamento();
        return link(NumeroWhatsapp.de(solicitacao.getCliente().getWhatsapp()), mensagem);
    }

    public static String reais(BigDecimal valor) {
        return "R$ " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR))
                .format(valor);
    }
}
