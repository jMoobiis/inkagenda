package br.com.inkagenda.service;

/** Respostas da ficha, com o consentimento especifico para dados de saude (LGPD art. 11, I). */
public record DadosAnamnese(String alergias, String medicamentos, String problemasCicatrizacao,
        boolean diabetes, boolean gestacao, String observacoes, boolean consentimentoDadosSaude) {
}
