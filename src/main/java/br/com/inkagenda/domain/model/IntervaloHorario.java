package br.com.inkagenda.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** Value Object de intervalo de tempo [inicio, fim), usado para detectar colisoes (RN07). */
public record IntervaloHorario(LocalDateTime inicio, LocalDateTime fim) {

    public IntervaloHorario {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new IllegalArgumentException("O fim do intervalo deve ser posterior ao inicio");
        }
    }

    /** Dois intervalos se sobrepoem quando compartilham algum instante; encostar nao conta. */
    public boolean sobrepoe(IntervaloHorario outro) {
        return inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }

    /** Intervalo acrescido de uma folga depois do fim (ex.: limpeza entre sessoes). */
    public IntervaloHorario comFolgaApos(Duration folga) {
        return new IntervaloHorario(inicio, fim.plus(folga));
    }

    public boolean cabeEm(IntervaloHorario janela) {
        return !inicio.isBefore(janela.inicio) && !fim.isAfter(janela.fim);
    }
}
