package br.com.inkagenda.service;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.model.ConfiguracaoTatuador;
import br.com.inkagenda.domain.model.FichaAnamnese;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.repository.ConfiguracaoTatuadorRepository;
import br.com.inkagenda.domain.repository.FichaAnamneseRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnamneseService {

    private final FichaAnamneseRepository fichas;
    private final ConfiguracaoTatuadorRepository configuracoes;
    private final Clock relogio;

    public AnamneseService(FichaAnamneseRepository fichas,
            ConfiguracaoTatuadorRepository configuracoes, Clock relogio) {
        this.fichas = fichas;
        this.configuracoes = configuracoes;
        this.relogio = relogio;
    }

    /** Gera o link da ficha quando a sessao e confirmada (US07). */
    @Transactional
    public FichaAnamnese gerarFicha(Sessao sessao) {
        if (sessao.getStatus() != StatusSessao.CONFIRMADA) {
            throw new RegraNegocioException("A ficha só é gerada para sessão confirmada");
        }
        Optional<FichaAnamnese> existente = fichas.findBySessaoId(sessao.getId());
        if (existente.isPresent()) {
            return existente.get();
        }
        ConfiguracaoTatuador config = configuracoes.findFirstByOrderByIdAsc()
                .orElseGet(ConfiguracaoTatuador::new);
        FichaAnamnese ficha = new FichaAnamnese();
        ficha.setSessao(sessao);
        ficha.setToken(UUID.randomUUID().toString().replace("-", ""));
        ficha.setExpiraEm(agora().plusHours(config.getValidadeAnamneseHoras()));
        return fichas.save(ficha);
    }

    /** Abre a ficha pendente. Ficha ja enviada nao reabre: o link nao expoe dados de saude. */
    @Transactional(readOnly = true)
    public FichaAnamnese abrirPorToken(String token) {
        if (token == null || token.isBlank()) {
            throw new RegraNegocioException("Link da ficha inválido");
        }
        FichaAnamnese ficha = fichas.findByToken(token)
                .orElseThrow(() -> new RegraNegocioException("Link da ficha inválido"));
        if (ficha.getPreenchidaEm() != null) {
            throw new RegraNegocioException("Esta ficha já foi enviada");
        }
        if (ficha.getExpiraEm().isBefore(agora())) {
            throw new RegraNegocioException("Link da ficha expirado");
        }
        return ficha;
    }

    /** Grava as respostas somente com o consentimento especifico (LGPD art. 11, I, CA2). */
    @Transactional
    public FichaAnamnese enviarFicha(String token, DadosAnamnese dados) {
        FichaAnamnese ficha = abrirPorToken(token);
        if (!dados.consentimentoDadosSaude()) {
            throw new RegraNegocioException(
                    "Para enviar a ficha, é preciso dar o consentimento para o uso dos dados de saúde");
        }
        LocalDateTime agora = agora();
        ficha.setAlergias(dados.alergias());
        ficha.setMedicamentos(dados.medicamentos());
        ficha.setProblemasCicatrizacao(dados.problemasCicatrizacao());
        ficha.setDiabetes(dados.diabetes());
        ficha.setGestacao(dados.gestacao());
        ficha.setObservacoes(dados.observacoes());
        ficha.setConsentimentoDadosSaude(true);
        ficha.setConsentimentoEm(agora);
        ficha.setPreenchidaEm(agora);
        return fichas.save(ficha);
    }

    /** Leitura da ficha pelo tatuador; a rota que chama exige autenticacao (CA3). */
    @Transactional(readOnly = true)
    public Optional<FichaAnamnese> fichaDaSessao(Long sessaoId) {
        return fichas.findBySessaoId(sessaoId);
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio);
    }
}
