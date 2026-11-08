package br.com.inkagenda.service;

import java.util.UUID;

/** Geracao e validacao dos tokens dos links de agendamento e de anamnese. */
final class TokenAcesso {

    private TokenAcesso() {
    }

    static String gerar() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    static String exigirInformado(String token, String mensagemErro) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException(mensagemErro);
        }
        return token;
    }
}
