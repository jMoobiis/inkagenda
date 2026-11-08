package br.com.inkagenda.service;

import br.com.inkagenda.domain.model.FichaAnamnese;
import br.com.inkagenda.domain.model.Sessao;
import java.math.BigDecimal;

/** Resultado do registro do sinal: valor a abater, divergencia e ficha gerada. */
public record ResultadoSinal(Sessao sessao, BigDecimal valorAAbater, boolean divergente,
        FichaAnamnese ficha) {
}
