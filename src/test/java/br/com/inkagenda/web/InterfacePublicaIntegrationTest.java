package br.com.inkagenda.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Confere o conteudo e a responsividade das paginas publicas (identidade visual da especificacao). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ui-publica-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class InterfacePublicaIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void deveExibirHeaderComLogoNavegacaoELoginDiscretoQuandoAbrirAHome() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INKAGENDA")))
                .andExpect(content().string(containsString("GUSTA")))
                .andExpect(content().string(containsString("Portfólio")))
                .andExpect(content().string(containsString("Como Funciona")))
                .andExpect(content().string(containsString("Log In (Admin)")));
    }

    @Test
    void deveDeclararViewportEFolhaDeEstiloQuandoRenderizarQualquerPaginaPublica() throws Exception {
        for (String rota : new String[] {"/", "/orcamento", "/orcamento/enviado", "/login"}) {
            mvc.perform(get(rota))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("width=device-width, initial-scale=1")))
                    .andExpect(content().string(containsString("/css/inkagenda.css")));
        }
    }

    @Test
    void deveExibirHeroSecaoSobreBadgesEComoFuncionaQuandoAbrirAHome() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("TRANSFORME SUA IDEIA EM <em>ARTE NA PELE</em>")))
                .andExpect(content().string(containsString("Orçamento sem retrabalho")))
                .andExpect(content().string(containsString("Horário garantido com sinal")))
                .andExpect(content().string(containsString("Anamnese 100% Digital")))
                .andExpect(content().string(containsString("id=\"como-funciona\"")));
    }

    @Test
    void deveApontarParaFotoLocalComFallbackVisualQuandoAbrirASecaoSobre() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("/img/gusta.jpeg")))
                .andExpect(content().string(containsString("retrato-fallback")));
    }

    @Test
    void deveAplicarOModoDeLayoutDaVitrineNaGaleriaQuandoAbrirAHome() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("data-modo=\"STEEZY_SPLIT\"")));
    }

    @Test
    void deveServirOCssPublicamenteSemLoginQuandoPedirAFolhaDeEstilo() throws Exception {
        mvc.perform(get("/css/inkagenda.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#FFE01B".toLowerCase())))
                .andExpect(content().string(containsString("#0f0f0f")))
                .andExpect(content().string(containsString("border-radius: var(--raio)")));
    }

    @Test
    void deveExigirAvisoDePrivacidadeEImagensNoFormularioQuandoAbrirOOrcamento() throws Exception {
        mvc.perform(get("/orcamento"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aviso de Privacidade")))
                .andExpect(content().string(containsString("name=\"imagens\"")))
                .andExpect(content().string(containsString("multipart/form-data")))
                .andExpect(content().string(containsString("_csrf")));
    }

    @Test
    void naoDeveExporDadosDeSaudeNemCredenciaisNaHomeQuandoAbrirAPaginaPublica() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(not(containsString("alergia"))))
                .andExpect(content().string(not(containsString("password"))));
    }
}
