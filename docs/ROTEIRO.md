# Roteiro de execução

Cada execução da rotina faz **uma** etapa: a primeira não marcada, na ordem. Prazo final: domingo, 08/11/2026, 22h (America/Sao_Paulo).

Mensagens de commit entre crases são exigidas pelo edital: use o texto exato. O andamento é registrado só na branch `develop`.

## Andamento

- [x] E0. Estrutura, domínio e Ciclo TDD 1 (`feature/orcamento`)
- [ ] E1. Ciclo TDD 2 (`feature/agenda`)
- [ ] E2. Ciclo TDD 3 (`feature/sinal-anamnese`)
- [ ] E3. Vitrine e back-end web (`feature/ui-vitrine-admin`)
- [ ] E4. Interface pública (`feature/ui-vitrine-admin`)
- [ ] E5. Painel admin, CI e README (`feature/ui-vitrine-admin`)
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
2. `.github/workflows/ci.yml` (Java 21, `./mvnw -B clean verify`, publica o relatório JaCoCo como artefato) e `README.md` (execução, como definir as credenciais em `application-local.yml` sem citar valores, onde colocar a foto local, arquitetura, cobertura e Matriz de Rastreabilidade RF/US/RN/teste com os nomes reais dos testes). Commit:
   `ci(devops): configura pipeline GitHub Actions com build, testes JUnit e relatorio JaCoCo`
3. Faça push e confirme que o workflow passou, se tiver acesso.

## E6. Verificação final e release

1. Rode `./mvnw -B clean verify`, suba a aplicação e percorra o fluxo: orçamento, resposta, escolha de horário, sinal, anamnese e véspera. Corrija o que falhar, com commits `fix(...)`.
2. Confira a matriz contra os testes reais e US01 a US08 e RN01 a RN09 contra `docs/especificacao.md`.
3. Só faça o release se tudo estiver verde e as etapas anteriores concluídas. Caso contrário, não faça: registre o que falta.

```
git checkout develop && git merge --no-ff feature/ui-vitrine-admin -m "merge: integra interface completa, vitrine e pipeline CI"
git checkout main && git merge --no-ff develop -m "release: promove v1.0.0-mvp do sistema InkAgenda"
git tag -a v1.0.0-mvp -m "Release v1.0.0-mvp - InkAgenda MVP Validado"
git push origin develop main v1.0.0-mvp
```

## Registro

- 05/11/2026: E0 concluída. Ciclo 1 com RED real (47 testes falhando) e GREEN/REFACTOR com 48 testes passando.
