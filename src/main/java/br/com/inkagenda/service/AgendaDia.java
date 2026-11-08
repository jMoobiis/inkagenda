package br.com.inkagenda.service;

import br.com.inkagenda.domain.model.BloqueioAgenda;
import br.com.inkagenda.domain.model.Sessao;
import java.time.LocalDate;
import java.util.List;

/** Sessoes ativas e bloqueios de um dia da agenda. */
public record AgendaDia(LocalDate dia, List<Sessao> sessoes, List<BloqueioAgenda> bloqueios) {
}
