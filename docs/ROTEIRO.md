# Roteiro de execução

Cada execução da rotina faz **uma** etapa: a primeira não marcada, na ordem. Prazo final: domingo, 08/11/2026, 22h (America/Sao_Paulo).

Mensagens de commit entre crases são exigidas pelo edital: use o texto exato. O andamento é registrado só na branch `develop`.

## Andamento

- [x] E0. Estrutura, domínio e Ciclo TDD 1 (`feature/orcamento`)
- [x] E1. Ciclo TDD 2 (`feature/agenda`)
- [x] E2. Ciclo TDD 3 (`feature/sinal-anamnese`)
- [x] E3. Vitrine e back-end web (`feature/ui-vitrine-admin`)
- [x] E4. Interface pública (`feature/ui-vitrine-admin`)
- [x] E5. Painel admin, CI e README (`feature/ui-vitrine-admin`)
- [ ] E6. Verificação final e release

## Ao terminar cada execução

1. Rode `./mvnw -B clean verify` na branch em que trabalhou. Se falhar, corrija. Se não conseguir, não faça merge: anote o bloqueio em "Registro".
2. Faça push de todas as branches alteradas.
3. Em `develop`, marque a etapa, acrescente uma linha ao "Registro" e faça commit `docs: atualiza andamento do roteiro (Ex)` e push.

Se o tempo acabar com a etapa incompleta, faça push do que estiver testado e anote o que falta, sem marcar a etapa.

## E1. Ciclo TDD 2: agenda

`git checkout -b feature/agenda develop`

1. **RED:** `AgendaServiceTest.java` com cenários positivos e negativos de US04 e US08 (RN07, RN08, RN09): bloqueio por duração mais intervalo de limpeza, recusa de sobreposição, rejeição de sessão além do expediente, dia sem atendimento, bloqueios pessoais, antecedência mínima de 48h com e sem `confirmacaoExplicitaPerdaSinal`. Commit:
   `test(agenda): [RED] cria testes de conflito de horario, intervalo e antecedencia de remarcacao (US04, US08, RN07, RN08, RN09)`
2. **GREEN:** `AgendaService.java` com disponibilidade, criação e remarcação. Commit:
   `feat(agenda): [GREEN] implementa calculo de disponibilidade com bloqueio por duracao e intervalo (US04, US08, RF04)`
3. **REFACTOR:** Value Object `IntervaloHorario` com `sobrepoe()`, sem duplicação. Commit:
   `refactor(agenda): [REFACTOR] centraliza verificacao de colisao temporal e elimina duplicacao (US04, RN07)`
4. `git checkout develop && git merge --no-ff feature/agenda -m "merge: integra feature/agenda na develop (US04, US08)"`

## E2. Ciclo TDD 3: sinal e anamnese

`git checkout -b feature/sinal-anamnese develop`

1. **RED:** `SinalServiceTest.java` e `AnamneseServiceTest.java` cobrindo US05, US06 e US07 (RN04, RN06, LGPD art. 11): confirmação só com sinal, expiração da pré-reserva liberando a agenda e voltando para `ORCADA`, alerta de valor divergente, `valorAAbater`, ficha bloqueada sem consentimento específico, token inválido ou expirado, lembrete de véspera sem telefone válido. Commit:
   `test(anamnese-sinal): [RED] adiciona testes de confirmacao de sinal e consentimento LGPD na anamnese (US05, US07, RN04, RN06)`
2. **GREEN:** `SinalService.java` e `AnamneseService.java`. Commit:
   `feat(anamnese-sinal): [GREEN] implementa confirmacao de sinal, expiracao de pre-reserva e ficha LGPD (US05, US06, US07)`
3. **REFACTOR:** enums e exceções de negócio padronizadas. Commit:
   `refactor(anamnese-sinal): [REFACTOR] padroniza excecoes de negocio e validacao de token de sessao (US05, US07)`
4. `git checkout develop && git merge --no-ff feature/sinal-anamnese -m "merge: integra feature/sinal-anamnese na develop (US05, US06, US07)"`

## E3. Vitrine e back-end web

`git checkout -b feature/ui-vitrine-admin develop`

1. `VitrineService` com testes (upload, modos de layout, ordenação, ativar/ocultar, fixar no Hero). Commit:
   `feat(vitrine): implementa gerenciador dinamico de fotos e modos de exibicao do portfolio com testes`
2. Spring Security (`/login`, `/admin/**`), controllers, tratamento de erros, job de expiração, `DataInitializer`, entrega protegida das imagens privadas, com testes de integração (`MockMvc`) de acesso negado e CSRF. Commit: `feat(web): adiciona seguranca, controllers e seed de dados ficticios`

## E4. Interface pública

Na mesma branch: layout base com design system em CSS, home com Hero e os 3 modos de layout, seção sobre o tatuador, "Como Funciona", formulário de orçamento, escolha de horário, tela de Pix e ficha de anamnese. Suba a aplicação, confira as páginas e rode testes `MockMvc` das rotas. Commit: `feat(ui-publica): constroi paginas publicas responsivas`

## E5. Painel admin, CI e README

Na mesma branch:

1. Login, painel por status com "Sem retorno", resposta de orçamento e WhatsApp, agenda semanal/diária com bloqueios, controle de sinal, confirmações de véspera e gerenciador de vitrine. Commit:
   `feat(ui): constroi interface publica Steezy/Mailchimp, area do cliente e painel administrativo completo`
2. `README.md` (execução, como definir as credenciais em `application-local.yml` sem citar valores, onde colocar a foto local, arquitetura, cobertura e Matriz de Rastreabilidade RF/US/RN/teste com os nomes reais dos testes). Commit: `docs(readme): documenta execucao, arquitetura e matriz de rastreabilidade`
3. Faça push. O pipeline já existe em `.github/workflows/`: a nuvem não pode alterar esses arquivos. Se precisar mudá-los, anote em "Registro" para o dono do projeto.

## E6. Verificação final e release

1. Rode `./mvnw -B clean verify`, suba a aplicação e percorra o fluxo: orçamento, resposta, escolha de horário, sinal, anamnese e véspera. Corrija o que falhar, com commits `fix(...)`.
2. Confira a matriz contra os testes reais e US01 a US08 e RN01 a RN09 contra `docs/especificacao.md`.
3. Só faça o release se tudo estiver verde e as etapas anteriores concluídas. Caso contrário, não faça: registre o que falta.

```
git checkout develop && git merge --no-ff feature/ui-vitrine-admin -m "merge: integra interface completa, vitrine e pipeline CI"
git checkout main && git merge --no-ff develop -m "release: promove v1.0.0-mvp do sistema InkAgenda"
git push origin develop main
```

A nuvem não pode criar tags. O workflow `release-tag.yml` cria a `v1.0.0-mvp` quando o commit `release:` chega na `main`. Registre em "Registro" se a tag ainda não tiver aparecido ao fim da execução.

## Registro

- 05/11/2026: E0 concluída. Ciclo 1 com RED real (47 testes falhando) e GREEN/REFACTOR com 48 testes passando. Pipeline de CI criado antes do previsto, para validar todas as etapas seguintes, porque o ambiente da nuvem não tem permissão para enviar workflows.
- 06/11/2026: E1 concluída. Ciclo 2 com RED real (falha de compilação, `AgendaService` inexistente), GREEN com 34 testes de agenda passando e REFACTOR com `IntervaloHorario`. `./mvnw` não pôde baixar o Maven no ambiente (wget sem proxy); o build foi rodado com `mvn -B clean verify` (Maven 3.9.11): 82 testes, 0 falhas. O corpo do commit RED cita 33 testes, o total real é 34. Sem pendências.
- 06/11/2026: E2 concluída, feita pelo PC local a pedido do dono, fora da rotina. Ciclo 3 com RED real (falha de compilação, `SinalService` e `AnamneseService` inexistentes), GREEN com 55 testes novos passando e REFACTOR com exceções derivadas de `RegraNegocioException` e `TokenAcesso`. `./mvnw -B clean verify`: 137 testes, 0 falhas. Sem pendências.
- 06/11/2026: E3 concluída, feita pelo PC local a pedido do dono, fora da rotina. `VitrineService`, segurança, controllers, erros 403/404, job de expiração, seed e imagens privadas na branch `feature/ui-vitrine-admin`, que só entra na `develop` na E6. `./mvnw -B clean verify`: 232 testes, 0 falhas, 94% de cobertura de instruções. Aplicação conferida com HTTP real: CSRF em multipart, login, imagens privadas e o fluxo do orçamento à anamnese. **Para a E4 e a E5:** os templates em `templates/` são mínimos e sem estilo, de propósito; refaça o visual mantendo as rotas, os nomes dos campos e os `th:action` (CSRF), e rode os testes de `web/`, que verificam textos dessas telas. Decisão registrada: cancelar com o sinal mantido devolve a solicitação para `ORCADA`; com o sinal perdido, para `PERDIDA`. Pendência: não há tela para editar o perfil (endereço e chave Pix), que hoje vem só do seed, então no profile `postgres` esses dados ficam vazios. O primeiro CI da branch falhou porque os testes usavam o fuso do sistema; corrigido no commit `fix(web)`, com CI verde. **Nos testes de integração, use sempre o bean `Clock`, nunca `LocalDate.now()` sem argumento:** o CI roda em UTC e a aplicação em America/Sao_Paulo.
- 07/11/2026: E4 concluída na `feature/ui-vitrine-admin`. Layout base (fragmento Thymeleaf), `inkagenda.css`, home com Hero, 3 modos de layout, sobre com fallback da foto local, Como Funciona e telas de orçamento, horário, Pix e anamnese. Novos testes `InterfacePublicaIntegrationTest`: RED real (8 testes, 4 falhas) e depois verde. `mvn -B clean verify`: 240 testes, 0 falhas; 360px sem rolagem horizontal. Pendências: o visual do painel admin fica para a E5 (os templates `admin/` continuam mínimos); o Alpine.js não foi usado (CSS puro bastou).
- 07/11/2026: E5 concluída na `feature/ui-vitrine-admin`. Painel admin estilizado (menu, kanban por status com rótulos e badge "Sem retorno", agenda, véspera, solicitação, sessão, anamnese e gerenciador de vitrine) e `README.md` com execução, credenciais, foto local, arquitetura e Matriz de Rastreabilidade (nomes de testes conferidos contra o código). Novos testes `InterfaceAdminIntegrationTest`: RED real (8 testes, 8 falhas) e depois verde. `mvn -B clean verify`: 248 testes, 0 falhas, ~94% de cobertura de instruções. Pendências: a tela de editar o perfil (endereço e chave Pix) continua inexistente (só seed); o Alpine.js não foi usado; o `./mvnw` segue sem baixar o Maven na nuvem, então o build usou `mvn`; o CI do GitHub não foi conferido nesta execução. A E6 (release) ainda depende de percorrer o fluxo com a aplicação no ar.
