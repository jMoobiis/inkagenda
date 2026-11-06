package br.com.inkagenda.service;

/** O registro pedido nao existe; a camada web responde 404. */
public class RecursoNaoEncontradoException extends RegraNegocioException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
