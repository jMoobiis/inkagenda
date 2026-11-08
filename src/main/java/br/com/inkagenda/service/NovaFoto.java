package br.com.inkagenda.service;

import br.com.inkagenda.domain.enums.EstiloTatuagem;

/** Foto enviada pelo tatuador; sem {@code displayOrder}, ela vai para o fim da vitrine. */
public record NovaFoto(String titulo, EstiloTatuagem estilo, String tagDestaque,
        Integer displayOrder, ArquivoEnviado arquivo) {
}
