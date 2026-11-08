package br.com.inkagenda.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.inkagenda.service.ArquivoEnviado;
import br.com.inkagenda.service.RecursoNaoEncontradoException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArmazenamentoLocalTest {

    private static final byte[] BYTES = {1, 2, 3};

    @TempDir
    private Path raiz;

    private ArmazenamentoLocal armazenamento() {
        return new ArmazenamentoLocal(raiz.resolve("uploads").toString());
    }

    @Test
    void deveDevolverMesmoConteudoQuandoSalvarECarregar() {
        ArmazenamentoLocal armazenamento = armazenamento();

        String caminho = armazenamento.salvar(new ArquivoEnviado("foto.png", BYTES));

        assertThat(armazenamento.carregar(caminho)).isEqualTo(BYTES);
    }

    @Test
    void naoDeveUsarNomeEnviadoComoCaminhoQuandoNomeTentaSairDaPasta() {
        ArmazenamentoLocal armazenamento = armazenamento();

        String caminho = armazenamento.salvar(new ArquivoEnviado("../../fora.png", BYTES));

        assertThat(caminho).doesNotContain("..", "/", "\\", "fora");
        assertThat(raiz.resolve("fora.png")).doesNotExist();
        assertThat(raiz.resolve("uploads").resolve(caminho)).exists();
    }

    @Test
    void naoDeveLerArquivoQuandoCaminhoSaiDaPastaDeUploads() throws Exception {
        Files.write(raiz.resolve("segredo.txt"), BYTES);
        ArmazenamentoLocal armazenamento = armazenamento();
        armazenamento.salvar(new ArquivoEnviado("foto.png", BYTES));

        assertThatThrownBy(() -> armazenamento.carregar("../segredo.txt"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveAvisarQuandoArquivoNaoExiste() {
        assertThatThrownBy(() -> armazenamento().carregar("inexistente.png"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
