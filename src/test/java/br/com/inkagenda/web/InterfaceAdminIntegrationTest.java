package br.com.inkagenda.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Confere o painel administrativo estilizado: layout, colunas por status, agenda, vespera e vitrine. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ui-admin-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class InterfaceAdminIntegrationTest {

    private static final String[] ROTAS_ADMIN = {"/admin", "/admin/agenda", "/admin/vespera", "/admin/vitrine"};

    @Autowired
    private MockMvc mvc;

    @Test
    void deveDeclararViewportEFolhaDeEstiloQuandoRenderizarQualquerTelaAdmin() throws Exception {
        for (String rota : ROTAS_ADMIN) {
            mvc.perform(get(rota).with(user("admin")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("width=device-width, initial-scale=1")))
                    .andExpect(content().string(containsString("/css/inkagenda.css")));
        }
    }

    @Test
    void deveExibirMenuAdminEstilizadoComLinkParaAVitrineQuandoRenderizarQualquerTelaAdmin() throws Exception {
        for (String rota : ROTAS_ADMIN) {
            mvc.perform(get(rota).with(user("admin")))
                    .andExpect(content().string(containsString("class=\"menu-admin\"")))
                    .andExpect(content().string(containsString("Gerenciar Portfólio &amp; Vitrine")));
        }
    }

    @Test
    void deveAgruparSolicitacoesEmColunasPorStatusComBadgeSemRetornoQuandoAbrirOPainel() throws Exception {
        mvc.perform(get("/admin").with(user("admin")))
                .andExpect(content().string(containsString("class=\"kanban\"")))
                .andExpect(content().string(containsString("class=\"coluna\"")))
                .andExpect(content().string(containsString("class=\"cartao-item\"")))
                .andExpect(content().string(containsString("badge-sem-retorno")))
                .andExpect(content().string(containsString("Sem retorno")));
    }

    @Test
    void deveRotularAsColunasDoPainelComTextoLegivelQuandoAbrirOPainel() throws Exception {
        mvc.perform(get("/admin").with(user("admin")))
                .andExpect(content().string(containsString("Aguardando sinal")))
                .andExpect(content().string(containsString("Confirmada")));
    }

    @Test
    void deveExibirFormulariosEstilizadosNaAgendaQuandoAbrirAAgenda() throws Exception {
        mvc.perform(get("/admin/agenda").with(user("admin")))
                .andExpect(content().string(containsString("class=\"cartao\"")))
                .andExpect(content().string(containsString("Novo bloqueio")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void deveExibirCartoesDeLembreteComAcoesQuandoAbrirAVespera() throws Exception {
        mvc.perform(get("/admin/vespera").with(user("admin")))
                .andExpect(content().string(containsString("class=\"cartao-item\"")))
                .andExpect(content().string(containsString("Enviar Lembrete WhatsApp")))
                .andExpect(content().string(containsString("Presença Confirmada")));
    }

    @Test
    void deveExibirGradeDeFotosEControlesQuandoAbrirOGerenciadorDaVitrine() throws Exception {
        mvc.perform(get("/admin/vitrine").with(user("admin")))
                .andExpect(content().string(containsString("class=\"grade-admin\"")))
                .andExpect(content().string(containsString("Fixar no Hero")))
                .andExpect(content().string(containsString("Salvar ordem")));
    }

    @Test
    void deveServirEstilosDoPainelAdminNaFolhaDeEstiloQuandoPedirOCss() throws Exception {
        mvc.perform(get("/css/inkagenda.css"))
                .andExpect(content().string(containsString(".menu-admin")))
                .andExpect(content().string(containsString(".kanban")))
                .andExpect(content().string(containsString(".grade-admin")))
                .andExpect(content().string(containsString(".badge-sem-retorno")));
    }
}
