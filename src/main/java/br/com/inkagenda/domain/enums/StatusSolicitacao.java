package br.com.inkagenda.domain.enums;

public enum StatusSolicitacao {
    NOVA("Nova"),
    ORCADA("Orçada"),
    AGUARDANDO_SINAL("Aguardando sinal"),
    CONFIRMADA("Confirmada"),
    PERDIDA("Perdida");

    private final String rotulo;

    StatusSolicitacao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
