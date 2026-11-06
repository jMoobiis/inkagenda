package br.com.inkagenda.service;

public interface ArmazenamentoArquivos {

    /** Salva o arquivo e devolve o caminho relativo gerado. */
    String salvar(ArquivoEnviado arquivo);
}
