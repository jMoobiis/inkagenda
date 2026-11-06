package br.com.inkagenda.service;

import java.math.BigDecimal;

public record RespostaOrcamento(BigDecimal valorTotal, Integer duracaoEstimadaHoras,
        BigDecimal valorSinal) {
}
