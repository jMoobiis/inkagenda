package br.com.inkagenda.service;

import br.com.inkagenda.domain.model.Sessao;

/** Lembrete de vespera: traz o link ou, sem telefone valido, o aviso (US06, CA3). */
public record LembreteVespera(Sessao sessao, String linkWhatsApp, String aviso) {

    public boolean podeEnviar() {
        return linkWhatsApp != null;
    }
}
