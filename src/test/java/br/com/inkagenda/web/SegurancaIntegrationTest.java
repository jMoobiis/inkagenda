package br.com.inkagenda.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.inkagenda.domain.enums.EstiloTatuagem;
import br.com.inkagenda.domain.enums.StatusSessao;
import br.com.inkagenda.domain.enums.StatusSolicitacao;
import br.com.inkagenda.domain.enums.TipoImagem;
import br.com.inkagenda.domain.model.Cliente;
import br.com.inkagenda.domain.model.FichaAnamnese;
import br.com.inkagenda.domain.model.ImagemReferencia;
import br.com.inkagenda.domain.model.ItemPortfolio;
import br.com.inkagenda.domain.model.Sessao;
import br.com.inkagenda.domain.model.Solicitacao;
import br.com.inkagenda.domain.repository.ClienteRepository;
import br.com.inkagenda.domain.repository.FichaAnamneseRepository;
import br.com.inkagenda.domain.repository.ItemPortfolioRepository;
import br.com.inkagenda.domain.repository.SessaoRepository;
import br.com.inkagenda.domain.repository.SolicitacaoRepository;
import br.com.inkagenda.service.ArmazenamentoArquivos;
import br.com.inkagenda.service.ArquivoEnviado;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Acesso negado, CSRF, entrega de arquivos privados e links invalidos ou expirados. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:seguranca-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SegurancaIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};
    private static final AtomicLong NUMERO = new AtomicLong(16911100000L);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ClienteRepository clientes;
    @Autowired
    private SolicitacaoRepository solicitacoes;
    @Autowired
    private SessaoRepository sessoes;
    @Autowired
    private FichaAnamneseRepository fichas;
    @Autowired
    private ItemPortfolioRepository portfolio;
    @Autowired
    private ArmazenamentoArquivos armazenamento;

    private String salvarPng() {
        return armazenamento.salvar(new ArquivoEnviado("x.png", PNG));
    }

    private Solicitacao solicitacaoComImagem() {
        LocalDateTime agora = LocalDateTime.now();
        Cliente cliente = clientes.save(new Cliente("Cliente de Teste",
                String.valueOf(NUMERO.incrementAndGet()), agora));
        Solicitacao s = new Solicitacao();
        s.setCliente(cliente);
        s.setStatus(StatusSolicitacao.CONFIRMADA);
        s.setCriadaEm(agora);
        s.getImagens().add(new ImagemReferencia(s, salvarPng(), TipoImagem.REFERENCIA));
        return solicitacoes.save(s);
    }

    private FichaAnamnese ficha(LocalDateTime expiraEm, LocalDateTime preenchidaEm) {
        Sessao sessao = new Sessao();
        sessao.setSolicitacao(solicitacaoComImagem());
        sessao.setInicio(LocalDateTime.now().plusDays(5));
        sessao.setFim(LocalDateTime.now().plusDays(5).plusHours(1));
        sessao.setStatus(StatusSessao.CONFIRMADA);
        FichaAnamnese ficha = new FichaAnamnese();
        ficha.setSessao(sessoes.save(sessao));
        ficha.setToken(UUID.randomUUID().toString());
        ficha.setExpiraEm(expiraEm);
        ficha.setPreenchidaEm(preenchidaEm);
        ficha.setAlergias("Alergia-confidencial");
        return fichas.save(ficha);
    }

    private ItemPortfolio foto(boolean ativa) {
        ItemPortfolio item = new ItemPortfolio();
        item.setTitulo("Foto de teste");
        item.setEstilo(EstiloTatuagem.FINELINE);
        item.setCaminhoImagem(salvarPng());
        item.setAtivo(ativa);
        return portfolio.save(item);
    }

    // ---------- autenticacao ----------

    @Test
    void deveRedirecionarParaLoginQuandoAnonimoAcessaPainel() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void deveRedirecionarParaLoginQuandoAnonimoAcessaSubrotaDoAdmin() throws Exception {
        for (String rota : new String[] {"/admin/agenda", "/admin/vespera", "/admin/vitrine",
            "/admin/solicitacoes/1", "/admin/sessoes/1", "/admin/sessoes/1/anamnese"}) {
            mvc.perform(get(rota)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Test
    void deveAbrirPainelQuandoAutenticado() throws Exception {
        for (String rota : new String[] {"/admin", "/admin/agenda", "/admin/vespera",
            "/admin/vitrine"}) {
            mvc.perform(get(rota).with(user("admin"))).andExpect(status().isOk());
        }
    }

    @Test
    void deveEntrarNoPainelQuandoCredenciaisCorretas() throws Exception {
        mvc.perform(formLogin("/login").user("admin@teste.local")
                .password("valor-proprio-de-teste"))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void deveRecusarLoginQuandoSenhaErrada() throws Exception {
        mvc.perform(formLogin("/login").user("admin@teste.local").password("errada"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void deveAbrirPaginasPublicasQuandoAnonimo() throws Exception {
        for (String rota : new String[] {"/", "/login", "/orcamento", "/orcamento/enviado"}) {
            mvc.perform(get(rota)).andExpect(status().isOk());
        }
    }

    @Test
    void naoDeveExporConsoleH2QuandoAnonimo() throws Exception {
        mvc.perform(get("/h2-console")).andExpect(status().isNotFound());
    }

    // ---------- CSRF ----------

    @Test
    void deveRecusarPostAdminQuandoSemCsrf() throws Exception {
        mvc.perform(post("/admin/vitrine/modo").param("modo", "MASONRY_FLOW").with(user("admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveAceitarPostAdminQuandoComCsrf() throws Exception {
        mvc.perform(post("/admin/vitrine/modo").param("modo", "STEEZY_SPLIT").with(user("admin"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void deveRedirecionarParaLoginQuandoAnonimoEnviaPostAdminComCsrf() throws Exception {
        mvc.perform(post("/admin/vitrine/modo").param("modo", "MASONRY_FLOW").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void deveRecusarOrcamentoPublicoQuandoSemCsrf() throws Exception {
        mvc.perform(multipart("/orcamento").file(new MockMultipartFile("imagens", "a.png",
                "image/png", PNG)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRecusarEnvioDeFichaQuandoSemCsrf() throws Exception {
        FichaAnamnese ficha = ficha(LocalDateTime.now().plusDays(1), null);

        mvc.perform(post("/anamnese/" + ficha.getToken()).param("consentimentoDadosSaude", "true"))
                .andExpect(status().isForbidden());
    }

    // ---------- imagens privadas ----------

    @Test
    void deveNegarImagemDeReferenciaQuandoAnonimo() throws Exception {
        Solicitacao s = solicitacaoComImagem();
        Long imagem = s.getImagens().get(0).getId();

        mvc.perform(get("/admin/solicitacoes/" + s.getId() + "/imagens/" + imagem))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void deveEntregarImagemDeReferenciaQuandoAutenticado() throws Exception {
        Solicitacao s = solicitacaoComImagem();
        Long imagem = s.getImagens().get(0).getId();

        mvc.perform(get("/admin/solicitacoes/" + s.getId() + "/imagens/" + imagem)
                .with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void naoDeveEntregarImagemQuandoEhDeOutraSolicitacao() throws Exception {
        Solicitacao dona = solicitacaoComImagem();
        Solicitacao outra = solicitacaoComImagem();
        Long imagem = dona.getImagens().get(0).getId();

        mvc.perform(get("/admin/solicitacoes/" + outra.getId() + "/imagens/" + imagem)
                .with(user("admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoDeveExporArquivoDeUploadQuandoCaminhoDiretoNaUrl() throws Exception {
        String caminho = salvarPng();

        mvc.perform(get("/uploads/" + caminho)).andExpect(status().isNotFound());
        mvc.perform(get("/" + caminho)).andExpect(status().isNotFound());
    }

    // ---------- vitrine publica ----------

    @Test
    void deveEntregarFotoQuandoAtiva() throws Exception {
        mvc.perform(get("/portfolio/" + foto(true).getId() + "/imagem"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void naoDeveEntregarFotoPublicaQuandoOculta() throws Exception {
        mvc.perform(get("/portfolio/" + foto(false).getId() + "/imagem"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveEntregarFotoOcultaNoPainelQuandoAutenticado() throws Exception {
        mvc.perform(get("/admin/vitrine/fotos/" + foto(false).getId() + "/imagem")
                .with(user("admin")))
                .andExpect(status().isOk());
    }

    // ---------- links de token (US07, CA3) ----------

    @Test
    void deveResponder404AmigavelQuandoLinkDaFichaInvalido() throws Exception {
        mvc.perform(get("/anamnese/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Link da ficha inválido")));
    }

    @Test
    void deveResponder403AmigavelQuandoLinkDaFichaExpirado() throws Exception {
        FichaAnamnese ficha = ficha(LocalDateTime.now().minusDays(1), null);

        mvc.perform(get("/anamnese/" + ficha.getToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Link da ficha expirado")));
    }

    @Test
    void naoDeveMostrarRespostasQuandoLinkDeFichaJaEnviada() throws Exception {
        FichaAnamnese ficha = ficha(LocalDateTime.now().plusDays(1), LocalDateTime.now());

        String corpo = mvc.perform(get("/anamnese/" + ficha.getToken()))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("Alergia-confidencial");
    }

    @Test
    void naoDeveMostrarRespostasDeSaudeQuandoAnonimoAcessaAdmin() throws Exception {
        FichaAnamnese ficha = ficha(LocalDateTime.now().plusDays(1), LocalDateTime.now());

        mvc.perform(get("/admin/sessoes/" + ficha.getSessao().getId() + "/anamnese"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void deveMostrarRespostasDeSaudeQuandoTatuadorAutenticado() throws Exception {
        FichaAnamnese ficha = ficha(LocalDateTime.now().plusDays(1), LocalDateTime.now());
        ficha.setConsentimentoEm(LocalDateTime.now());
        fichas.save(ficha);

        mvc.perform(get("/admin/sessoes/" + ficha.getSessao().getId() + "/anamnese")
                .with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alergia-confidencial")));
    }

    @Test
    void deveResponder404AmigavelQuandoLinkDeAgendamentoInvalido() throws Exception {
        mvc.perform(get("/agendar/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Link de agendamento inválido")));
    }

    @Test
    void deveResponder404QuandoSessaoNaoExisteNoPainel() throws Exception {
        mvc.perform(get("/admin/sessoes/999999").with(user("admin")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Sessão não encontrada")));
    }
}
