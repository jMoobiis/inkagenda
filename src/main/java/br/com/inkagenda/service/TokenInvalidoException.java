package br.com.inkagenda.service;

/** O token do link nao existe ou nao foi informado; a camada web responde 404. */
public class TokenInvalidoException extends RegraNegocioException {

    public TokenInvalidoException(String mensagem) {
        super(mensagem);
    }
}
