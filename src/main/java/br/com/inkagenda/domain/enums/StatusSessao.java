package br.com.inkagenda.domain.enums;

public enum StatusSessao {
    PRE_RESERVADA,
    CONFIRMADA,
    CANCELADA,
    EXPIRADA;

    /** Sessoes nestes estados ocupam a agenda (RN07). */
    public boolean ocupaAgenda() {
        return this == PRE_RESERVADA || this == CONFIRMADA;
    }
}
