package br.com.inkagenda.service;

public interface ArmazenamentoArquivos {

    /** Salva o arquivo e devolve o caminho relativo gerado. */
    String salvar(ArquivoEnviado arquivo);

    /** Le um arquivo salvo; o caminho vem do banco, nunca do cliente. */
    byte[] carregar(String caminho);
}
