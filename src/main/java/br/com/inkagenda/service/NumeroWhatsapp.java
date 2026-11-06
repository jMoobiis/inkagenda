package br.com.inkagenda.service;

/** Numero brasileiro normalizado: somente digitos, com DDD e sem o codigo do pais. */
public record NumeroWhatsapp(String digitos) {

    private static final String CODIGO_PAIS = "55";

    public static NumeroWhatsapp de(String informado) {
        String digitos = informado == null ? "" : informado.replaceAll("\\D", "");
        if (digitos.startsWith(CODIGO_PAIS) && (digitos.length() == 12 || digitos.length() == 13)) {
            digitos = digitos.substring(CODIGO_PAIS.length());
        }
        if (digitos.length() != 10 && digitos.length() != 11) {
            throw new RegraNegocioException("Informe um WhatsApp válido com DDD");
        }
        return new NumeroWhatsapp(digitos);
    }

    public String comCodigoPais() {
        return CODIGO_PAIS + digitos;
    }
}
