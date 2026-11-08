# InkAgenda

Sistema web responsivo (mobile-first) de orçamentos, agendamento, sinais, anamnese digital e vitrine de portfólio para um tatuador autônomo. Projeto de Engenharia de Software II. Requisitos em [`docs/especificacao.md`](docs/especificacao.md); andamento em [`docs/ROTEIRO.md`](docs/ROTEIRO.md).

## Como executar

Requisitos: JDK 21. O Maven vem pelo wrapper.

```
./mvnw -B clean verify      # compila, roda os testes e gera a cobertura (JaCoCo)
./mvnw spring-boot:run      # sobe a aplicação em http://localhost:8080
```

Por padrão a base é H2 em arquivo (`./data`) e os uploads vão para `./uploads` (`inkagenda.uploads-dir`). Para PostgreSQL, use o profile `postgres` com a variável `DATABASE_URL`; nesse profile o seed de dados fictícios não roda.

### Credenciais do administrador

Nenhuma senha fica no repositório. Defina `inkagenda.admin.email` e `inkagenda.admin.senha` em um destes lugares:

- arquivo `application-local.yml` (na raiz do projeto ou em `config/`), que o Git ignora:
  ```yaml
  inkagenda:
    admin:
      email: <seu e-mail>
      senha: <sua senha>
  ```
  e rode com `--spring.profiles.active=local`;
- ou variáveis de ambiente `INKAGENDA_ADMIN_EMAIL` e `INKAGENDA_ADMIN_SENHA`.

Sem senha configurada, o sistema gera uma senha aleatória a cada início e a mostra uma única vez no console. O login fica em `/login` e o painel em `/admin`.

### Foto do tatuador

Coloque a foto em `src/main/resources/static/img/gusta.jpeg`. O arquivo é só local e está no `.gitignore`: nunca faça commit dele. Sem o arquivo, a página mostra um fallback visual próprio.

## Arquitetura

| Camada | Pacote | Papel |
|---|---|---|
| Domínio | `domain.model`, `domain.enums`, `domain.repository` | Entidades JPA, enums tipados (`StatusSolicitacao`, `StatusSessao`, `ModoLayout`...) e repositórios |
| Serviços | `service` | Regras de negócio puras, sem HTTP: `OrcamentoService`, `AgendaService`, `SinalService`, `AnamneseService`, `VitrineService`, `PainelService`. Tempo sempre via `Clock` |
| Web | `web` | Controllers Thymeleaf (públicos e `/admin/**`), formulários e tratamento de erros |
| Infra | `infra` | Segurança (Spring Security, BCrypt, CSRF), armazenamento de arquivos, job de expiração e `DataInitializer` |

Interface em Thymeleaf com CSS próprio (`static/css/inkagenda.css`), sem etapa de build. Erros de negócio usam `RegraNegocioException` e derivadas.

### Segurança e LGPD

- Senhas com BCrypt, CSRF ativo, `/admin/**` exige login.
- Dados de saúde da anamnese só aparecem para o tatuador autenticado, com consentimento específico (LGPD art. 11, I), e não vão para log.
- Imagens de referência e comprovantes são privados (só o admin); só as fotos ativas da vitrine são públicas.
- Nome de arquivo enviado nunca vira caminho. Console do H2 desligado.

## Testes e cobertura

JUnit 5, Mockito, AssertJ, MockMvc e JaCoCo. Nomes em português: `deve[Comportamento]Quando[Cenario]()`. Na última execução de `./mvnw -B clean verify` (pelo Maven instalado): **248 testes, 0 falhas**, cerca de **94% de cobertura de instruções**. O relatório fica em `target/site/jacoco/index.html`.

## Matriz de rastreabilidade

Classes: `OrcamentoServiceTest` (O), `AgendaServiceTest` (A), `SinalServiceTest` (S), `AnamneseServiceTest` (N), `VitrineServiceTest` (V), `PainelServiceTest` (P), `FluxoCompletoIntegrationTest` (F), `SegurancaIntegrationTest` (G), `InterfacePublicaIntegrationTest` (IP), `InterfaceAdminIntegrationTest` (IA), `DataInitializerTest` (D). Os nomes abaixo são os métodos reais.

| Requisito | Descrição | Testes |
|---|---|---|
| US01 / F1 / UC01 | Solicitar orçamento sem login | O: `deveCriarSolicitacaoComStatusNovaEDataHoraQuandoDadosForemValidos`, `deveBloquearEnvioQuandoWhatsappForInvalido`, `deveBloquearEnvioQuandoAvisoDePrivacidadeNaoForAceito`; F: `deveExibirErroQuandoCampoObrigatorioFaltando`; IP: `deveExigirAvisoDePrivacidadeEImagensNoFormularioQuandoAbrirOOrcamento` |
| RN01 | Ao menos 1 imagem | O: `deveBloquearEnvioDeOrcamentoQuandoNenhumaImagemForAnexada`, `deveBloquearEnvioDeOrcamentoQuandoListaDeImagensForNula`; F: `deveExibirErroQuandoOrcamentoSemImagem` |
| RN02 | Máx. 5 imagens JPG/PNG de até 5MB | O: `deveBloquearEnvioQuandoHouverMaisDeCincoImagens`, `deveAceitarEnvioQuandoHouverExatamenteCincoImagens`, `deveBloquearEnvioQuandoImagemUltrapassarCincoMB`, `deveAceitarEnvioQuandoImagemTiverExatamenteCincoMB`, `deveBloquearEnvioQuandoExtensaoNaoForJpgOuPng`, `deveBloquearEnvioQuandoConteudoNaoCorresponderAoFormatoDeclarado` |
| FA1 | Cobertura exige foto da tatuagem atual | O: `deveExigirFotoDaTatuagemAtualQuandoForCobertura`, `deveGuardarFotoDaTatuagemAtualQuandoForCobertura` |
| FA2 | WhatsApp existente vincula ao cliente | O: `deveVincularClienteExistenteSemDuplicarQuandoWhatsappJaEstiverCadastrado`, `deveCadastrarNovoClienteQuandoWhatsappNaoExistir` |
| US02 / F2 | Painel por status e "Sem retorno" | P: `deveMarcarSemRetornoQuandoOrcamentoEstaParado`, `deveTrazerTodosOsStatusQuandoNaoHaSolicitacoes`; O: `deveMarcarSemRetornoQuandoOrcadaHaMaisDeTresDias`, `deveRespeitarPrazoConfiguradoQuandoDiasSemRetornoForAlterado`; IA: `deveAgruparSolicitacoesEmColunasPorStatusComBadgeSemRetornoQuandoAbrirOPainel`; G: `deveRedirecionarParaLoginQuandoAnonimoAcessaPainel` |
| US03 | Responder orçamento e WhatsApp | O: `deveSugerirSinalDeTrintaPorCentoQuandoConfiguracaoForPadrao`, `deveMudarParaOrcadaEGerarTokenQuandoRespostaForValida`, `deveGerarLinkWhatsAppComResumoValoresELinkDeAgendamentoQuandoOrcada`; F: `devePercorrerOFluxoInteiroQuandoClienteETatuadorSeguemAsEtapas` |
| RN05 | Sinal não pode passar do total; valores positivos | O: `deveRejeitarRespostaQuandoSinalForMaiorQueValorTotal`, `deveRejeitarRespostaQuandoAlgumValorForMenorOuIgualAZero`, `deveAceitarRespostaQuandoSinalForIgualAoValorTotal` |
| US04 / F3 / UC03 | Agenda com bloqueio por duração e intervalo | A: `deveBloquearHorariosPorDuracaoMaisIntervaloQuandoHaSessaoNoDia`, `deveRespeitarIntervaloDeLimpezaConfiguradoQuandoEleForMaior`, `deveOferecerTodosOsHorariosCabiveisQuandoDiaEstaLivre`, `deveBloquearHorariosQuandoHaBloqueioPessoal`, `deveRetornarVazioQuandoDiaNaoTemAtendimento`; P: `deveCriarBloqueioQuandoIntervaloValido`, `deveRemoverBloqueioQuandoExiste`; F: `deveCriarERemoverBloqueioQuandoTatuadorUsaAgenda` |
| RN07 | Sem sobreposição de sessões | A: `deveRecusarSessaoQueSobrepoeOutraConfirmada`, `deveRecusarSessaoQueNaoRespeitaIntervaloDeLimpezaDepoisDeOutra`, `deveRecusarSessaoQueNaoRespeitaIntervaloDeLimpezaAntesDeOutra`, `deveAceitarSessaoQueComecaExatamenteAposOIntervaloDeLimpeza`; F: `deveRecusarSegundaReservaQuandoHorarioJaOcupado` |
| RN09 | Sessão não ultrapassa o expediente | A: `deveRecusarSessaoQueUltrapassaOFimDoExpediente`, `deveAceitarSessaoQueTerminaExatamenteNoFimDoExpediente`, `deveRetornarVazioQuandoDuracaoExcedeOExpediente`, `deveRecusarSessaoQueComecaAntesDaAberturaDoExpediente`, `deveRecusarSessaoEmDiaSemAtendimento` |
| US08 / RN08 | Remarcar e cancelar com antecedência de 48h | A: `deveRemarcarMantendoSinalQuandoHaAntecedenciaSuficiente`, `deveExigirConfirmacaoExplicitaQuandoRemarcacaoTemMenosDe48Horas`, `deveRemarcarComPerdaDeSinalQuandoMenosDe48HorasECOnfirmacaoExplicita`, `deveManterSinalQuandoFaltamExatamente48Horas`, `deveExigirConfirmacaoQuandoFaltamUmMinutoParaCompletar48Horas`, `deveCancelarLiberandoHorarioQuandoHaAntecedenciaSuficiente`, `deveExigirConfirmacaoExplicitaQuandoCancelamentoTemMenosDe48Horas`; P: `deveDevolverParaOrcadaQuandoCancelaMantendoSinal`, `deveMarcarComoPerdidaQuandoCancelaPerdendoSinal`; F: `deveDevolverParaOrcadaQuandoCancelarComAntecedencia` |
| US05 / F4 / UC02 | Escolha de horário, pré-reserva e sinal | S: `deveCriarPreReservaEAguardarSinalQuandoTokenValido`, `deveRejeitarEscolhaQuandoTokenNaoExiste`, `deveGuardarDataValorEComprovanteQuandoSinalRegistrado`, `deveAceitarSemComprovanteQuandoComprovanteEhOpcional`; P: `deveSalvarComprovanteQuandoArquivoValido`, `deveRejeitarComprovanteQuandoFormatoInvalido`; G: `deveNegarImagemDeReferenciaQuandoAnonimo` |
| RN04 | Confirma só com sinal; valor a abater; divergência | S: `deveConfirmarSessaoESolicitacaoQuandoSinalRegistrado`, `deveCalcularValorAAbaterQuandoSinalRegistrado`, `deveAlertarENaoConfirmarQuandoValorDivergeSemConfirmacao`, `deveConfirmarQuandoValorDivergeComConfirmacaoExplicita`, `deveRejeitarSinalQuandoSemValor`, `deveRejeitarSinalQuandoValorZero`; F: `deveAvisarSemConfirmarQuandoSinalDivergente` |
| RN06 | Prazo vencido libera o horário e volta para `ORCADA` | S: `deveExpirarSessaoEDevolverParaOrcadaQuandoPrazoVencido`, `deveRejeitarSinalQuandoPrazoDaPreReservaVenceu`, `deveAceitarSinalQuandoPrazoVenceExatamenteAgora`, `deveExpirarTodasQuandoHaVariasVencidas`; F: `deveLiberarHorarioEVoltarParaOrcadaQuandoJobExpiraPreReserva` |
| US07 / F5 / UC06 | Anamnese com consentimento LGPD | N: `deveGerarFichaComTokenEValidadeQuandoSessaoConfirmada`, `deveBloquearEnvioENaoGravarNadaQuandoSemConsentimento`, `deveGravarDadosDeSaudeQuandoComConsentimento`, `deveRegistrarMomentoDoConsentimentoQuandoEnviar`, `deveRejeitarAberturaQuandoTokenExpirado`, `deveRejeitarAberturaQuandoTokenNaoExiste`, `deveDevolverFichaQuandoTatuadorConsultaSessao`; G: `deveMostrarRespostasDeSaudeQuandoTatuadorAutenticado`, `deveResponder403AmigavelQuandoLinkDaFichaExpirado`, `deveResponder404AmigavelQuandoLinkDaFichaInvalido`, `deveRecusarEnvioDeFichaQuandoSemCsrf` |
| US06 / F6 / UC05 | Confirmação de véspera | S: `deveListarSessoesConfirmadasDeAmanhaQuandoConsultarVespera`, `deveMontarLinkComDataHorarioEnderecoECuidadosQuandoTelefoneValido`, `deveDesabilitarLembreteEAvisarQuandoTelefoneInvalido`, `deveRegistrarPresencaQuandoSessaoConfirmada`; IA: `deveExibirCartoesDeLembreteComAcoesQuandoAbrirAVespera`; F: `deveMostrarSessoesDoSeedQuandoAbrirPainelEVespera` |
| Vitrine | Portfólio dinâmico, 3 modos, ocultar, fixar, reordenar | V: `deveSalvarFotoAtivaComDadosQuandoUploadValido`, `deveRejeitarUploadQuandoFormatoInvalido`, `deveRejeitarUploadQuandoArquivoMaiorQueLimite`, `deveAlterarModoQuandoJaHaConfiguracao`, `deveListarSomenteAtivasEmOrdemQuandoVitrinePublica`, `deveFiltrarPorEstiloQuandoEstiloInformado`, `deveOcultarFotoQuandoDesativada`, `deveFixarNoHeroQuandoFotoAtiva`, `deveLimitarHeroATresQuandoHaMaisFixadas`, `deveAplicarNovaOrdemQuandoReordenar`; F: `deveAdicionarFotoEMostrarNaHomeQuandoTatuadorEnviaUpload`; IP: `deveAplicarOModoDeLayoutDaVitrineNaGaleriaQuandoAbrirAHome`; IA: `deveExibirGradeDeFotosEControlesQuandoAbrirOGerenciadorDaVitrine`; G: `deveEntregarFotoQuandoAtiva` |
| Identidade visual | Header, Hero, seção sobre, badges, CSS | IP: `deveExibirHeaderComLogoNavegacaoELoginDiscretoQuandoAbrirAHome`, `deveExibirHeroSecaoSobreBadgesEComoFuncionaQuandoAbrirAHome`, `deveApontarParaFotoLocalComFallbackVisualQuandoAbrirASecaoSobre`, `deveServirOCssPublicamenteSemLoginQuandoPedirAFolhaDeEstilo`; IA: `deveDeclararViewportEFolhaDeEstiloQuandoRenderizarQualquerTelaAdmin`, `deveExibirMenuAdminEstilizadoComLinkParaAVitrineQuandoRenderizarQualquerTelaAdmin` |
| Segurança | Login, CSRF, `/admin/**`, imagens privadas | G: `deveRedirecionarParaLoginQuandoAnonimoAcessaSubrotaDoAdmin`, `deveRecusarLoginQuandoSenhaErrada`, `deveRecusarPostAdminQuandoSemCsrf`, `deveRecusarOrcamentoPublicoQuandoSemCsrf`, `deveEntregarImagemDeReferenciaQuandoAutenticado` |
| Seed | Dados 100% fictícios, fora do `postgres` | D: `deveCriarPerfilComExpedienteIntervaloESinalQuandoBaseVazia`, `deveCriarSeisItensDePortfolioQuandoBaseVazia`, `deveCriarSolicitacoesEmCadaEstadoEsperadoQuandoBaseVazia`, `deveGuardarSenhaDoAdminComBcryptQuandoConfigurada`; `SeedForaDoPostgresTest.deveCriarAdminQuandoProfilePostgres` |

## Fluxo de uso

1. O cliente abre a vitrine (vindo da bio do Instagram), pede um orçamento e anexa as imagens.
2. O tatuador responde no painel e envia o link pelo WhatsApp.
3. O cliente escolhe o horário e paga o sinal por Pix; o tatuador confere e confirma.
4. O cliente preenche a anamnese com consentimento; na véspera o tatuador envia o lembrete.
