package br.com.inkagenda.service;

/** O token existe, mas nao vale mais; a camada web responde 403. */
public class TokenExpiradoException extends RegraNegocioException {

    public TokenExpiradoException(String mensagem) {
        super(mensagem);
    }
}
