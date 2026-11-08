package br.com.inkagenda.web;

import br.com.inkagenda.service.ArquivoEnviado;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/** Conversao entre os arquivos de HTTP e os tipos usados pelos services. */
final class ArquivosWeb {

    private ArquivosWeb() {
    }

    /** O navegador envia uma parte vazia quando nenhum arquivo foi escolhido. */
    static ArquivoEnviado arquivo(MultipartFile enviado) {
        if (enviado == null || enviado.isEmpty()) {
            return null;
        }
        try {
            return new ArquivoEnviado(enviado.getOriginalFilename(), enviado.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado", e);
        }
    }

    static List<ArquivoEnviado> arquivos(List<MultipartFile> enviados) {
        if (enviados == null) {
            return List.of();
        }
        return enviados.stream().filter(enviado -> !enviado.isEmpty()).map(ArquivosWeb::arquivo)
                .toList();
    }

    static ResponseEntity<byte[]> imagem(String caminho, byte[] conteudo, CacheControl cache) {
        MediaType tipo = caminho.toLowerCase(Locale.ROOT).endsWith(".png") ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(tipo).cacheControl(cache).body(conteudo);
    }
}
