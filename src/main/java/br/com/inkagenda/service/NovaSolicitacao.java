package br.com.inkagenda.service;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import java.util.List;

public record NovaSolicitacao(
        String nome,
        String whatsapp,
        Integer tamanhoCm,
        String regiaoCorpo,
        EstiloTatuagem estilo,
        boolean cobertura,
        String descricao,
        String preferenciaDias,
        boolean aceitePrivacidade,
        List<ArquivoEnviado> imagensReferencia,
        ArquivoEnviado fotoTatuagemAtual) {
}
