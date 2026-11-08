package br.com.inkagenda.domain.enums;

public enum EstiloTatuagem {
    FINELINE("Fineline"),
    BLACKWORK("Blackwork"),
    OLD_SCHOOL("Old School"),
    COBERTURA("Cobertura"),
    REALISMO("Realismo"),
    NEO_ORIENTAL("Neo-Oriental"),
    OUTRO("Outro");

    private final String rotulo;

    EstiloTatuagem(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
