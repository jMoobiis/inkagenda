package br.com.inkagenda.service;

import br.com.inkagenda.domain.model.Solicitacao;

/** Linha do painel: a solicitacao e o alerta "Sem retorno" (US02, CA2). */
public record ItemPainel(Solicitacao solicitacao, boolean semRetorno) {
}
