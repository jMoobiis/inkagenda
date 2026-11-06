package br.com.inkagenda.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.model.FichaAnamnese;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.FichaAnamneseRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import br.com.inkagenda.infra.ExpiracaoPreReservaJob;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Percorre o fluxo real pelas rotas: orcamento, resposta, horario, sinal, ficha e vespera. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fluxo-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FluxoCompletoIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SolicitacaoRepository solicitacoes;
    @Autowired
    private SessaoRepository sessoes;
    @Autowired
    private FichaAnamneseRepository fichas;
    @Autowired
    private ExpiracaoPreReservaJob job;

    private void enviarOrcamento(String whatsapp) throws Exception {
        mvc.perform(multipart("/orcamento")
                .file(new MockMultipartFile("imagens", "ref.png", "image/png", PNG))
                .param("nome", "Cliente Fluxo")
                .param("whatsapp", whatsapp)
                .param("tamanhoCm", "10")
                .param("regiaoCorpo", "Braço")
                .param("estilo", "FINELINE")
                .param("aceitePrivacidade", "true")
                .with(csrf()))
                .andExpect(redirectedUrl("/orcamento/enviado"));
    }

    private Solicitacao ultimaSolicitacao() {
        return solicitacoes.findAll().stream().max(Comparator.comparing(Solicitacao::getId))
                .orElseThrow();
    }

    private Solicitacao orcamentoRespondido(String whatsapp) throws Exception {
        enviarOrcamento(whatsapp);
        Solicitacao nova = ultimaSolicitacao();
        mvc.perform(post("/admin/solicitacoes/" + nova.getId() + "/orcamento")
                .param("valorTotal", "1000.00")
                .param("duracaoEstimadaHoras", "2")
                .param("valorSinal", "300.00")
                .with(user("admin")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        return solicitacoes.findById(nova.getId()).orElseThrow();
    }

    /** Dia de atendimento distante, para nao colidir com o seed nem com a antecedencia. */
    private static LocalDateTime horario(int semanas, int hora) {
        LocalDate dia = LocalDate.now().plusWeeks(semanas);
        while (dia.getDayOfWeek() != DayOfWeek.TUESDAY) {
            dia = dia.plusDays(1);
        }
        return dia.atTime(hora, 0);
    }

    private Sessao sessaoDe(Solicitacao solicitacao) {
        return sessoes.findAll().stream()
                .filter(s -> s.getSolicitacao().getId().equals(solicitacao.getId()))
                .max(Comparator.comparing(Sessao::getId)).orElseThrow();
    }

    @Test
    void devePercorrerOFluxoInteiroQuandoClienteETatuadorSeguemAsEtapas() throws Exception {
        Solicitacao orcada = orcamentoRespondido("16922200001");
        assertThat(orcada.getStatus()).isEqualTo(StatusSolicitacao.ORCADA);
        String token = orcada.getTokenAgendamento();

        mvc.perform(get("/admin/solicitacoes/" + orcada.getId()).with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://wa.me/5516922200001")));

        LocalDateTime inicio = horario(2, 10);
        mvc.perform(get("/agendar/" + token).param("dia", inicio.toLocalDate().toString()))
                .andExpect(status().isOk());
        mvc.perform(post("/agendar/" + token).param("inicio", inicio.toString()).with(csrf()))
                .andExpect(redirectedUrl("/agendar/" + token + "/pix"));
        mvc.perform(get("/agendar/" + token + "/pix"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pix-ficticio@inkagenda.local")));
        mvc.perform(get("/agendar/" + token))
                .andExpect(redirectedUrl("/agendar/" + token + "/pix"));

        Sessao sessao = sessaoDe(orcada);
        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.PRE_RESERVADA);
        mvc.perform(multipart("/admin/sessoes/" + sessao.getId() + "/sinal")
                .file(new MockMultipartFile("comprovante", "pix.png", "image/png", PNG))
                .param("dataPagamento", LocalDate.now().toString())
                .param("valorRecebido", "300.00")
                .with(user("admin")).with(csrf()))
                .andExpect(flash().attributeExists("mensagem"));

        Sessao confirmada = sessoes.findById(sessao.getId()).orElseThrow();
        assertThat(confirmada.getStatus()).isEqualTo(StatusSessao.CONFIRMADA);
        assertThat(confirmada.getValorAAbater()).isEqualByComparingTo("700.00");
        mvc.perform(get("/admin/sessoes/" + sessao.getId() + "/comprovante").with(user("admin")))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/sessoes/" + sessao.getId() + "/comprovante"))
                .andExpect(status().is3xxRedirection());

        FichaAnamnese ficha = fichas.findBySessaoId(sessao.getId()).orElseThrow();
        mvc.perform(get("/anamnese/" + ficha.getToken())).andExpect(status().isOk());
        mvc.perform(post("/anamnese/" + ficha.getToken()).param("alergias", "Latex").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("consentimento")));
        assertThat(fichas.findById(ficha.getId()).orElseThrow().getPreenchidaEm()).isNull();
        mvc.perform(post("/anamnese/" + ficha.getToken()).param("alergias", "Latex")
                .param("consentimentoDadosSaude", "true").with(csrf()))
                .andExpect(redirectedUrl("/anamnese/concluida"));
        mvc.perform(get("/anamnese/" + ficha.getToken())).andExpect(status().isForbidden());

        mvc.perform(post("/admin/sessoes/" + sessao.getId() + "/presenca")
                .with(user("admin")).with(csrf()))
                .andExpect(redirectedUrl("/admin/vespera"));
        assertThat(sessoes.findById(sessao.getId()).orElseThrow().isPresencaConfirmada()).isTrue();
    }

    @Test
    void deveExibirErroQuandoOrcamentoSemImagem() throws Exception {
        mvc.perform(multipart("/orcamento")
                .param("nome", "Sem Imagem")
                .param("whatsapp", "16922200002")
                .param("tamanhoCm", "10")
                .param("regiaoCorpo", "Braço")
                .param("estilo", "FINELINE")
                .param("aceitePrivacidade", "true")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Anexe ao menos uma imagem de referência")));
    }

    @Test
    void deveExibirErroQuandoCampoObrigatorioFaltando() throws Exception {
        mvc.perform(multipart("/orcamento").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe seu nome")));
    }

    @Test
    void deveLiberarHorarioEVoltarParaOrcadaQuandoJobExpiraPreReserva() throws Exception {
        Solicitacao orcada = orcamentoRespondido("16922200003");
        LocalDateTime inicio = horario(3, 10);
        mvc.perform(post("/agendar/" + orcada.getTokenAgendamento())
                .param("inicio", inicio.toString()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/agendar/" + orcada.getTokenAgendamento() + "/pix"))
                .andExpect(status().isOk());
        Sessao sessao = sessaoDe(orcada);
        sessao.setExpiraEm(LocalDateTime.now().minusMinutes(1));
        sessoes.save(sessao);

        job.executar();

        assertThat(sessoes.findById(sessao.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSessao.EXPIRADA);
        assertThat(solicitacoes.findById(orcada.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.ORCADA);
        mvc.perform(post("/agendar/" + orcada.getTokenAgendamento())
                .param("inicio", inicio.toString()).with(csrf()))
                .andExpect(redirectedUrl("/agendar/" + orcada.getTokenAgendamento() + "/pix"));
    }

    @Test
    void deveAvisarSemConfirmarQuandoSinalDivergente() throws Exception {
        Solicitacao orcada = orcamentoRespondido("16922200004");
        mvc.perform(post("/agendar/" + orcada.getTokenAgendamento())
                .param("inicio", horario(4, 10).toString()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Sessao sessao = sessaoDe(orcada);

        mvc.perform(multipart("/admin/sessoes/" + sessao.getId() + "/sinal")
                .param("dataPagamento", LocalDate.now().toString())
                .param("valorRecebido", "250.00")
                .with(user("admin")).with(csrf()))
                .andExpect(flash().attributeExists("erro"));

        assertThat(sessoes.findById(sessao.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSessao.PRE_RESERVADA);
    }

    @Test
    void deveRecusarSegundaReservaQuandoHorarioJaOcupado() throws Exception {
        Solicitacao primeira = orcamentoRespondido("16922200005");
        Solicitacao segunda = orcamentoRespondido("16922200006");
        LocalDateTime inicio = horario(5, 10);
        mvc.perform(post("/agendar/" + primeira.getTokenAgendamento())
                .param("inicio", inicio.toString()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/agendar/" + segunda.getTokenAgendamento())
                .param("inicio", inicio.toString()).with(csrf()))
                .andExpect(redirectedUrl("/agendar/" + segunda.getTokenAgendamento()))
                .andExpect(flash().attributeExists("erro"));

        assertThat(solicitacoes.findById(segunda.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.ORCADA);
    }

    @Test
    void deveDevolverParaOrcadaQuandoCancelarComAntecedencia() throws Exception {
        Solicitacao orcada = orcamentoRespondido("16922200007");
        mvc.perform(post("/agendar/" + orcada.getTokenAgendamento())
                .param("inicio", horario(6, 10).toString()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Sessao sessao = sessaoDe(orcada);

        mvc.perform(post("/admin/sessoes/" + sessao.getId() + "/cancelar")
                .with(user("admin")).with(csrf()))
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(sessoes.findById(sessao.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSessao.CANCELADA);
        assertThat(solicitacoes.findById(orcada.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.ORCADA);
    }

    @Test
    void deveCriarERemoverBloqueioQuandoTatuadorUsaAgenda() throws Exception {
        LocalDateTime inicio = horario(7, 10);
        mvc.perform(post("/admin/bloqueios").param("inicio", inicio.toString())
                .param("fim", inicio.plusHours(2).toString()).param("descricao", "Pessoal")
                .with(user("admin")).with(csrf()))
                .andExpect(flash().attributeExists("mensagem"));

        mvc.perform(get("/admin/agenda").param("dia", inicio.toLocalDate().toString())
                .with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pessoal")));
    }

    @Test
    void deveMostrarAvisoQuandoBloqueioComFimAntesDoInicio() throws Exception {
        LocalDateTime inicio = horario(7, 15);

        mvc.perform(post("/admin/bloqueios").param("inicio", inicio.toString())
                .param("fim", inicio.minusHours(1).toString())
                .with(user("admin")).with(csrf()))
                .andExpect(flash().attributeExists("erro"));
    }

    @Test
    void deveMostrarSessoesDoSeedQuandoAbrirPainelEVespera() throws Exception {
        mvc.perform(get("/admin").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sem retorno")));
        mvc.perform(get("/admin/vespera").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enviar Lembrete WhatsApp")));
    }

    @Test
    void deveAdicionarFotoEMostrarNaHomeQuandoTatuadorEnviaUpload() throws Exception {
        mvc.perform(multipart("/admin/vitrine/fotos")
                .file(new MockMultipartFile("arquivo", "nova.png", "image/png", PNG))
                .param("titulo", "Foto do Upload")
                .param("estilo", "REALISMO")
                .with(user("admin")).with(csrf()))
                .andExpect(flash().attributeExists("mensagem"));

        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Foto do Upload")));
        mvc.perform(get("/").param("estilo", "FINELINE")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("Foto do Upload"))));
    }
}
