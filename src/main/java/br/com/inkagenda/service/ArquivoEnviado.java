package br.com.inkagenda.service;

/** Arquivo recebido do cliente, sem dependencia de detalhes de HTTP. */
public record ArquivoEnviado(String nomeOriginal, byte[] conteudo) {

    public long tamanhoBytes() {
        return conteudo == null ? 0 : conteudo.length;
    }
}
