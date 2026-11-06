package br.com.inkagenda.service;

import java.util.List;
import java.util.Locale;

/** RN01 e RN02: quantidade, tamanho e formato real das imagens enviadas. */
public final class ValidadorArquivoReferencia {

    public static final int MAXIMO_IMAGENS = 5;
    public static final long TAMANHO_MAXIMO_BYTES = 5L * 1024 * 1024;

    private static final int[] ASSINATURA_PNG = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int[] ASSINATURA_JPG = {0xFF, 0xD8, 0xFF};

    private ValidadorArquivoReferencia() {
    }

    public static void validarConjunto(List<ArquivoEnviado> imagens) {
        if (imagens == null || imagens.isEmpty()) {
            throw new RegraNegocioException("Anexe ao menos uma imagem de referência");
        }
        if (imagens.size() > MAXIMO_IMAGENS) {
            throw new RegraNegocioException(
                    "Envie no máximo " + MAXIMO_IMAGENS + " imagens de referência");
        }
        imagens.forEach(ValidadorArquivoReferencia::validar);
    }

    public static void validar(ArquivoEnviado arquivo) {
        if (arquivo.tamanhoBytes() > TAMANHO_MAXIMO_BYTES) {
            throw new RegraNegocioException("Cada imagem deve ter até 5MB");
        }
        if (!ehPng(arquivo) && !ehJpg(arquivo)) {
            throw new RegraNegocioException("As imagens devem estar em formato JPG ou PNG");
        }
    }

    private static boolean ehPng(ArquivoEnviado arquivo) {
        return temExtensao(arquivo, ".png") && comecaCom(arquivo.conteudo(), ASSINATURA_PNG);
    }

    private static boolean ehJpg(ArquivoEnviado arquivo) {
        return (temExtensao(arquivo, ".jpg") || temExtensao(arquivo, ".jpeg"))
                && comecaCom(arquivo.conteudo(), ASSINATURA_JPG);
    }

    private static boolean temExtensao(ArquivoEnviado arquivo, String extensao) {
        return arquivo.nomeOriginal() != null
                && arquivo.nomeOriginal().toLowerCase(Locale.ROOT).endsWith(extensao);
    }

    private static boolean comecaCom(byte[] conteudo, int[] assinatura) {
        if (conteudo == null || conteudo.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if ((conteudo[i] & 0xFF) != assinatura[i]) {
                return false;
            }
        }
        return true;
    }
}
