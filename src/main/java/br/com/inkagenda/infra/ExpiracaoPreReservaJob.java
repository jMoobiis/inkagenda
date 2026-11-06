package br.com.inkagenda.infra;

import br.com.inkagenda.service.SinalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Libera periodicamente as pre-reservas com prazo vencido (RN06). */
@Configuration
@EnableScheduling
public class ExpiracaoPreReservaJob {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiracaoPreReservaJob.class);

    private final SinalService sinal;

    public ExpiracaoPreReservaJob(SinalService sinal) {
        this.sinal = sinal;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void executar() {
        int expiradas = sinal.expirarPreReservasVencidas();
        if (expiradas > 0) {
            LOG.info("Pre-reservas expiradas: {}", expiradas);
        }
    }
}
