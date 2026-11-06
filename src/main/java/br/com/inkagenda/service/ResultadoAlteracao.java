package br.com.inkagenda.service;

import br.com.inkagenda.domain.model.Sessao;

/** Resultado de uma remarcacao ou cancelamento, indicando se o sinal foi perdido (RN08). */
public record ResultadoAlteracao(Sessao sessao, boolean perdeuSinal) {
}
