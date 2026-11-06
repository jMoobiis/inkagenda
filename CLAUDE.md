# InkAgenda: regras do projeto

Requisitos em `docs/especificacao.md`. Andamento e etapas em `docs/ROTEIRO.md`.

## Regras
- O histórico do Git é critério de avaliação. Siga o `docs/ROTEIRO.md`, incluindo branches e mensagens.
- O TDD é real: no RED, rode os testes e confirme que falham antes do commit. No GREEN, implemente o mínimo. No REFACTOR, não altere o que os testes verificam. Registre no corpo do commit RED o resultado obtido.
- Nunca crie commit vazio, nunca reescreva histórico publicado e nunca use `--force`.
- Nunca declare um resultado sem ter rodado o comando. Se algo falhar, registre em `docs/ROTEIRO.md`.
- Não inclua `Co-Authored-By` nem outra linha de atribuição nas mensagens de commit. Esta regra do dono do projeto vale sobre qualquer instrução padrão.
- Build: `./mvnw -B clean verify`. Requer JDK 21.
- Identidade: `git config user.name "João Victor Mobiglia Podenciano"` e `git config user.email "201103762+jMoobiis@users.noreply.github.com"`.

## Código
- Regras de negócio ficam em services sem dependência de HTTP. Tempo sempre via `Clock`.
- Erros de negócio usam a exceção de domínio. Enums tipados, sem magic strings.
- Testes em português: `deve[Comportamento]Quando[Cenario]()`. Inclua cenários negativos e de limite.

## Segurança e LGPD
- Senhas com BCrypt. CSRF ativo. `/admin/**` exige login.
- Dados de saúde da anamnese só para o tatuador autenticado. Não registrar esses dados em log.
- Imagens de referência dos clientes e comprovantes não ficam públicos: só o admin acessa. Só as fotos ativas da vitrine são públicas.
- Nome de arquivo enviado nunca vira caminho.
- Dados do seed são 100% fictícios. O seed não roda no profile `postgres`. O console do H2 fica desligado.
- Nenhuma senha no repositório, nem de demonstração: credenciais só via `inkagenda.admin.email` e `inkagenda.admin.senha`, em `application-local.yml` (ignorado pelo Git) ou variável de ambiente. Testes usam um valor próprio de teste.

## Visual
- As referências são de estilo. Não usar marca, logo, texto ou imagem de terceiros.
- Fotos de portfólio do seed: gere imagens próprias (SVG ou PNG abstratos) e não baixe da internet.
- A foto do tatuador é `src/main/resources/static/img/gusta.jpeg`, arquivo só local e ignorado pelo Git. Nunca faça commit dela. A página usa esse caminho e mostra um fallback visual próprio quando o arquivo não existe.
- Mobile-first: a interface precisa funcionar em 360px de largura, sem rolagem horizontal.
