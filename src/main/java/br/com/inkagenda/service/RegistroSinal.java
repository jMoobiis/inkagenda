package br.com.inkagenda.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados do Pix conferido pelo tatuador (US05). */
public record RegistroSinal(LocalDate dataPagamento, BigDecimal valorRecebido, String comprovante,
        boolean confirmacaoDivergencia) {
}
