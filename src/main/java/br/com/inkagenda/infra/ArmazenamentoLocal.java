package br.com.inkagenda.infra;

import br.com.inkagenda.service.ArmazenamentoArquivos;
import br.com.inkagenda.service.ArquivoEnviado;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Salva em disco com nome aleatorio; o nome enviado pelo cliente nunca vira caminho. */
@Component
public class ArmazenamentoLocal implements ArmazenamentoArquivos {

    private final Path diretorio;

    public ArmazenamentoLocal(@Value("${inkagenda.uploads-dir}") String diretorio) {
        this.diretorio = Path.of(diretorio);
    }

    @Override
    public String salvar(ArquivoEnviado arquivo) {
        String extensao = arquivo.nomeOriginal().toLowerCase(Locale.ROOT).endsWith(".png")
                ? ".png" : ".jpg";
        String nome = UUID.randomUUID() + extensao;
        try {
            Files.createDirectories(diretorio);
            Files.write(diretorio.resolve(nome), arquivo.conteudo());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao salvar o arquivo enviado", e);
        }
        return nome;
    }
}
