# Especificação do InkAgenda

Sistema web responsivo (mobile-first) de orçamentos, agendamento, sinais, anamnese digital e vitrine de portfólio para um tatuador autônomo. Projeto da disciplina de Engenharia de Software II (Fábrica de Software), com TDD em JUnit 5, cobertura de testes, cenários negativos e CI no GitHub Actions.

## 1. Identidade visual

- **Hero em split-grid:** painéis com `border-radius: 16px`, fotos do portfólio à esquerda e bloco sólido `#0F0F0F` à direita, com tipografia display/grotesca em caixa alta. Ex.: "TRANSFORME SUA IDEIA EM ARTE NA PELE".
- **Header minimalista:** logo `INKAGENDA // GUSTA` à esquerda, navegação central (Portfólio, Como Funciona, Orçamento) e botão discreto `Log In (Admin)` à direita.
- **Seção sobre o tatuador:** foto (`/img/gusta.jpeg`, arquivo só local e fora do Git, com fallback visual quando ausente) com moldura orgânica ou borda dupla vibrante e badges flutuantes: "⚡ Orçamento sem retrabalho", "📅 Horário garantido com sinal", "🔒 Anamnese 100% Digital".
- **Contraste:** blocos `#121212`, off-white `#F8F7F4` e destaque amarelo `#FFE01B` ou azul `#2563EB` nos CTAs e bordas interativas.
- **Persona:** autêntica, urbana/streetwear, direta. Uso rápido no celular, vindo da bio do Instagram.
- As referências são de estilo: não copiar marca, logo, texto ou imagem de terceiros.

## 2. Stack

- Java 21, Spring Boot 3.5, Spring Web, Data JPA, Security (sessão), Bean Validation, Thymeleaf e Alpine.js.
- H2 em arquivo, com PostgreSQL via profile `postgres`. Uploads em disco (`inkagenda.uploads-dir`).
- CSS próprio em `static/css`, sem etapa de build com Node.
- JUnit 5, Mockito, AssertJ e JaCoCo. GitHub Actions em `.github/workflows/ci.yml`.

## 3. Funcionalidades

### Vitrine dinâmica (admin: "Gerenciar Portfólio & Vitrine")
- Upload JPG/PNG até 5MB com título, estilo (Fineline, Blackwork, Old School, Cobertura, Realismo, Neo-Oriental etc.), tag de destaque e `displayOrder`.
- Modo de layout alternável em tempo real, aplicado à galeria e ao Hero:
  1. `STEEZY_SPLIT`: painéis verticais lado a lado no topo e grid de 3 colunas abaixo.
  2. `MASONRY_FLOW`: mosaico com proporções diferentes.
  3. `CAROUSEL_SHOWCASE`: carrossel em destaque e vitrine filtrável por estilo.
- Ativar/ocultar fotos, fixar no Hero e reordenar.

### F1 / US01 / UC01: solicitação de orçamento (público)
- Sem login: nome, WhatsApp (máscara e validação), tamanho (cm), região do corpo, estilo, "É cobertura?" (FA1: exige foto da tatuagem atual), descrição, preferência de dias e 1 a 5 imagens JPG/PNG de até 5MB.
- Aceite obrigatório do Aviso de Privacidade.
- `RN01`: ao menos 1 imagem, com o erro "Anexe ao menos uma imagem de referência".
- `RN02`: máximo de 5 imagens JPG/PNG de até 5MB.
- `FA2`: WhatsApp já cadastrado vincula ao cliente existente, sem duplicar.
- Nasce como `NOVA`, com data e hora.

### F2 / US02 e US03: painel e resposta (admin)
- `/login` leva a `/admin`, protegidos por autenticação.
- Kanban ou lista por status: `NOVA`, `ORCADA`, `AGUARDANDO_SINAL`, `CONFIRMADA`, `PERDIDA`.
- CA2 de US02: `ORCADA` há mais de N dias (configurável, padrão 3) recebe badge vermelho "Sem retorno".
- US03: informa `valorTotal`, `duracaoEstimadaHoras` e `valorSinal` (sugestão de 30%, editável).
- `RN05`: rejeita `valorSinal > valorTotal` ou valor menor ou igual a zero.
- Ao salvar: status `ORCADA`, token único de agendamento e botão "Enviar Orçamento no WhatsApp" (`wa.me/55...`) com resumo, valores e link.

### F3 / US04 / UC03: agenda com bloqueio por duração
- Expediente semanal (ex.: seg a sáb, 10h às 18h) e intervalo obrigatório de limpeza (30 ou 60 min).
- Bloqueios pessoais na agenda.
- Disponibilidade para duração D: só oferece slots em que `inicio + D <= fimExpediente`, sem sobreposição (`RN07`) com sessões confirmadas ou pré-reservadas, considerando o intervalo.
- `RN08` (US08, remarcação e cancelamento): antecedência mínima (ex.: 48h). Com antecedência suficiente, mantém o sinal e libera o horário antigo. Com menos, emite alerta de perda de sinal e exige `confirmacaoExplicitaPerdaSinal = true`.
- `RN09`: sessão não pode ultrapassar o expediente.

### F4 / US05 / UC02: escolha de horário, pré-reserva e sinal
- Cliente abre o link e escolhe slot compatível com a duração. Status `AGUARDANDO_SINAL`, pré-reserva com prazo (ex.: 24h), chave Pix e instruções de pagamento.
- Admin confere o Pix e registra `dataPagamento`, `valorRecebido` e comprovante opcional.
- `RN04`: só vira `CONFIRMADA` após registrar o sinal, mostrando `valorAAbater = valorTotal - valorRecebido`.
- Se `valorRecebido != valorSinalCombinado`: alerta de divergência e confirmação explícita.
- `RN06`: prazo vencido sem sinal libera o horário e devolve a solicitação para `ORCADA`.

### F5 / US07 / UC06: anamnese e consentimento (LGPD)
- Ao virar `CONFIRMADA`, gera link com token e validade para a ficha (alergias, medicamentos, cicatrização, diabetes, gestação etc.).
- LGPD art. 11, I: consentimento específico e destacado, separado de outros aceites. Sem ele, bloqueia o envio (CA2).
- CA3: leitura só pelo tatuador autenticado. Link expirado ou inválido retorna 403/404 amigável.

### F6 / US06 / UC05: confirmação de véspera
- Tela com as sessões `CONFIRMADA` de amanhã.
- Botão "Enviar Lembrete WhatsApp" com data, horário, endereço e cuidados pré-sessão.
- Sem telefone válido: botão desabilitado com aviso para corrigir o cadastro (CA3).
- Botão "Presença Confirmada".

## 4. Engenharia

- Lógica em services puros (`OrcamentoService`, `AgendaService`, `SinalService`, `AnamneseService`, `VitrineService`), sem detalhes de HTTP. Enums tipados, sem magic strings.
- Testes unitários e negativos para os CAs de US01 a US08 e RN01 a RN09, em português: `deve[Comportamento]Quando[Cenario]()`.
- `DataInitializer`, só com dados 100% fictícios:
  - admin com e-mail e senha lidos de `inkagenda.admin.email` e `inkagenda.admin.senha`, nunca gravados no repositório. Sem senha configurada, gerar uma aleatória a cada início e exibi-la uma vez no console;
  - perfil com expediente 10h às 18h, intervalo de 30 min e sinal de 30%;
  - 6 itens de portfólio;
  - solicitações `NOVA`, `ORCADA` recente, `ORCADA` há 5 dias, `AGUARDANDO_SINAL` e `CONFIRMADA` para amanhã.
- `./mvnw clean verify` com relatório JaCoCo e `README.md` com instruções e Matriz de Rastreabilidade completa.
