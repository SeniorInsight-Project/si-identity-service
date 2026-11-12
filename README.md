# si-identity-service

Serviço de identidade: perfis de usuário e pareamento do idoso por código/QR ("convite VIP") com token exchange no Keycloak. Login, senha, SSO e MFA ficam no Keycloak.

Parte do SeniorInsight — ver o Documento de Arquitetura de Software (seção 6) para as convenções.

Branches: `main` (principal, protegida) e `develop` (testes). Trabalhe em branches curtas por issue e abra PR para `develop`.

## Stack

Java 21, Spring Boot 3.5, PostgreSQL (schema `identity`, migrações com Flyway), RabbitMQ (exchange topic `si.events`), Redis (códigos de pareamento) e Keycloak como emissor dos tokens. O serviço é resource server e, só no pareamento do idoso, usa a service account do client `si-identity-service` para criar o usuário e fazer o token exchange (ver [ADR 0001](docs/adr/0001-token-do-idoso.md)).

## Organização do código

Pacote base `com.seniorinsight.identity`, em arquitetura hexagonal:

| Pacote | O que tem | Pode depender de |
|---|---|---|
| `domain` | Modelo e eventos de domínio, Java puro | nada (nem Spring, nem JPA) |
| `application` | Casos de uso e portas (ex.: `porta.saida.PublicadorEventos`) | `domain` |
| `adapters.in` | Entrada: HTTP, segurança, OpenAPI | `application`, `domain` |
| `adapters.out` | Saída: banco, outbox, RabbitMQ, Redis, Keycloak, care-service | `application`, `domain` |

`adapters.in` e `adapters.out` não se conhecem. As regras são verificadas pelo `ArquiteturaTest` (ArchUnit) a cada build.

## Rodando localmente

Pré-requisitos: JDK 21, Maven 3.9+ e Docker.

### Sem infraestrutura própria (Testcontainers)

Sobe o serviço com PostgreSQL, RabbitMQ e Redis em containers descartáveis:

```bash
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/seniorinsight mvn spring-boot:test-run
```

Sem um Keycloak no ar o serviço sobe normalmente: o health e o Swagger funcionam, e as rotas protegidas respondem 401. Sem `KEYCLOAK_CLIENT_SECRET` o resgate do código responde 503.

### Com PostgreSQL, RabbitMQ, Redis e Keycloak já rodando

```bash
cp .env.example .env   # preencha as senhas
set -a && source .env && set +a
mvn spring-boot:run
```

O usuário do banco precisa poder criar o schema `identity` (ou o schema precisa existir com ele como dono): o Flyway cria o schema e aplica as migrações na subida.

### Endpoints

| Rota | Acesso |
|---|---|
| `GET /actuator/health` (e `/liveness`, `/readiness`) | público |
| `/swagger-ui.html` e `/v3/api-docs` | público |
| `GET /v1/eu` — devolve o `sub` e as roles do token, sem consultar o banco | token do Keycloak |
| `GET /v1/me` — perfil do usuário autenticado (ver abaixo) | token do Keycloak |
| `POST /v1/pareamentos/codigos` — gera o código de pareamento do aparelho do idoso | token com a role `familiar` |
| `POST /v1/pareamentos/resgates` — resgata o código no aparelho do idoso e devolve os tokens | público |
| todo o resto | token do Keycloak |

No gateway, `/api/v1/identity/**` é roteado para `/v1/**` deste serviço: o app chama `GET /api/v1/identity/me`, `POST /api/v1/identity/pareamentos/codigos` e `POST /api/v1/identity/pareamentos/resgates`.

### `GET /v1/me`

Devolve o schema `Perfil` do contrato (`openapi/identity-v1.yaml` do si-contracts): `id`, `tipo` (`FAMILIAR`/`IDOSO`), `nome`, `email` (pode ser `null`), `criadoEm` e `atualizadoEm`.

- **Primeiro acesso de um familiar:** se não existe perfil para o `sub` do token e o token tem a role `familiar` (o realm dá essa role a quem se cadastra), o perfil `FAMILIAR` é criado com o nome do claim `name` (ou `preferred_username`) e o e-mail do claim `email`, e o evento `user.created` é gravado na outbox **na mesma transação**.
- **Acessos seguintes:** só leem o perfil; nenhum evento é publicado.
- **Sem perfil e sem a role `familiar`:** `404` com o corpo de erro padrão (`timestamp`, `status`, `error`, `message`, `path`). O perfil `IDOSO` nasce no pareamento (HU-007).
- **Sem token ou token inválido:** `401`.
- **Primeiro acesso concorrente:** o insert usa `ON CONFLICT (keycloak_sub) DO NOTHING`; quem perde a corrida relê o perfil criado pela outra requisição. Resultado: um perfil, um evento, nenhum erro 500.

### Pareamento do idoso (HU-007)

Rotas e schemas do contrato `openapi/identity-v1.yaml`. Os erros usam o corpo padrão (`timestamp`, `status`, `error`, `message`, `path`).

**`POST /v1/pareamentos/codigos`** (role `familiar`): corpo `{"nomeIdoso": "...", "idosoId": "<uuid ou null>"}`, resposta `201` com `{"codigo", "qrCodeConteudo", "expiraEm"}`.

- `codigo`: 6 caracteres de `A-Z`/`2-9`, sem `0 O 1 I L` (formato `^[A-Z0-9]{6,8}$` do contrato). `qrCodeConteudo` = `seniorinsight://pareamento?codigo=<codigo>`.
- Com `idosoId`, só o **Familiar Administrador** do idoso gera. O serviço chama o care-service por REST interno (`GET {CARE_SERVICE_URL}/api/v1/idosos/{idosoId}/rede`, com o token do familiar) e confere se o `usuarioId` do familiar tem `papelAcesso` em `CARE_SERVICE_PAPEIS_ADMINISTRADOR`. Sem vínculo: `403`. Care-service fora: `503`.
- Sem `idosoId`, o idoso ainda não existe no care-service e quem gera é o familiar que vai cadastrá-lo.
- Uso único. Gerar de novo para o mesmo idoso (mesmo familiar e mesmo `idosoId`, ou ambos sem `idosoId`) invalida o código anterior, que passa a responder `404`.
- Validade: `IDENTITY_PAREAMENTO_VALIDADE`, padrão `PT24H` (RN-020). O contrato fala em "poucos minutos"; a divergência está em aberto.

**`POST /v1/pareamentos/resgates`** (pública): corpo `{"codigo", "identificadorAparelho", "plataforma": "ANDROID"|"IOS"}`, resposta `200` com `{"perfil", "sessaoAparelhoId", "tokens"}`.

- Cria (ou reaproveita, quando o código tem `idosoId`) o usuário do idoso no Keycloak e o perfil `IDOSO` com `email = null` e, quando o código tem `idosoId`, com esse id guardado em `perfil.idoso_id`. Se o perfil já existia sem `idosoId`, o id do código não o preenche. Quando o perfil é criado, `user.created` vai para a outbox na mesma transação.
- Registra a sessão em `sessao_aparelho` (quem pareou fica em `pareado_por`). Parear de novo o mesmo aparelho do mesmo idoso revoga a sessão anterior (`revogado_em`).
- `tokens` vem do token exchange V1 do Keycloak (ADR 0001). O refresh token é do client `si-identity-service`, então o app ainda não renova sozinho: ver as consequências na ADR.
- `404` código inexistente (ou substituído), `409` já resgatado, `410` expirado, `400` corpo fora do contrato, `503` Keycloak fora.

**Redis**: `identity:pairing:{codigo}` guarda o código pendente (hash com familiar, nome, `idosoId` e `expiraEm`) com TTL de validade + retenção. Gerar, resgatar e devolver são scripts Lua atômicos. O resgate apaga a chave e grava `identity:pairing:resgatado:{codigo}` na mesma operação, então dois resgates simultâneos não passam. Se o Keycloak ou o banco falharem depois disso, o código volta para o Redis e o idoso pode tentar de novo. Por `IDENTITY_PAREAMENTO_RETENCAO` (padrão `PT24H`) depois de vencido ou usado, o código ainda responde `410`/`409` em vez de `404`. `identity:pairing:emissor:{familiarSub}:{idosoId|novo}` aponta o código vigente de cada familiar para cada idoso.

## Variáveis de ambiente

O `application.yml` não tem segredos; tudo vem do ambiente. O modelo está no [`.env.example`](.env.example).

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `seniorinsight` | Banco (cada serviço usa o seu schema) |
| `DB_USERNAME` | — | Usuário do banco |
| `DB_PASSWORD` | — | Senha do banco |
| `RABBITMQ_HOST` | `localhost` | Host do RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Porta AMQP |
| `RABBITMQ_VHOST` | `/` | Virtual host |
| `RABBITMQ_USERNAME` | — | Usuário do RabbitMQ |
| `RABBITMQ_PASSWORD` | — | Senha do RabbitMQ |
| `KEYCLOAK_ISSUER_URI` | — | Issuer do realm, ex.: `http://localhost:8180/realms/seniorinsight` |
| `KEYCLOAK_URL` | `http://localhost:8180` | Keycloak visto de dentro do serviço (Admin API e token exchange), sem `/realms/...` |
| `KEYCLOAK_REALM` | `seniorinsight` | Realm |
| `KEYCLOAK_CLIENT_ID` | `si-identity-service` | Client confidencial com service account (`manage-users`, `impersonation`) |
| `KEYCLOAK_CLIENT_SECRET` | — | Secret do client. Sem ele, o resgate responde 503 |
| `KEYCLOAK_DOMINIO_EMAIL_IDOSO` | `idoso.seniorinsight.invalid` | Domínio do e-mail técnico do usuário do idoso no Keycloak |
| `REDIS_HOST` | `localhost` | Host do Redis |
| `REDIS_PORT` | `6379` | Porta do Redis |
| `REDIS_PASSWORD` | — | Senha do Redis, se houver |
| `CARE_SERVICE_URL` | `http://localhost:8081` | care-service por REST interno, ex.: `http://care-service:8080` |
| `CARE_SERVICE_PAPEIS_ADMINISTRADOR` | `ADMINISTRADOR` | Valores de `papelAcesso` (separados por vírgula) que contam como Familiar Administrador |
| `IDENTITY_PAREAMENTO_VALIDADE` | `PT24H` | Validade do código de pareamento |
| `IDENTITY_PAREAMENTO_RETENCAO` | `PT24H` | Quanto tempo o código vencido ou usado ainda responde 410/409 |
| `IDENTITY_REPUBLICAR_FAMILIARES` | `false` | Ligue uma vez para regravar na outbox o `user.created` de todos os familiares (backfill do care-service) |
| `IDENTITY_REPUBLICAR_FAMILIARES_LOTE` | `200` | Tamanho do lote da republicação |
| `SERVER_PORT` | `8080` | Porta HTTP |

`KEYCLOAK_ISSUER_URI` precisa ser **exatamente** o valor do claim `iss` dos tokens e precisa ser acessível de dentro do serviço (é dele que vêm as chaves públicas). Em Docker, configure o hostname do Keycloak (`KC_HOSTNAME`) para que o issuer seja o mesmo dentro e fora da rede dos containers.

Ajustes opcionais do relay da outbox (valores padrão no `application.yml`): `IDENTITY_OUTBOX_RELAY_INTERVALO` (`PT1S`), `IDENTITY_OUTBOX_RELAY_TAMANHO_LOTE` (`100`) e `IDENTITY_OUTBOX_RELAY_TIMEOUT_CONFIRMACAO` (`PT5S`).

## Segurança

O serviço não tem login: valida o JWT do Keycloak pelo issuer, sem sessão (stateless). A exceção é o resgate do código, em que o serviço pede ao Keycloak os tokens do idoso por token exchange (ADR 0001); para isso o Keycloak precisa subir com `--features=token-exchange:v1`. As roles de `realm_access.roles` viram authorities `ROLE_<role>`, com o nome da role como está no realm (ex.: `familiar` → `ROLE_familiar`); os scopes do token viram `SCOPE_<scope>`.

## Eventos (outbox)

Quem precisa publicar um evento chama a porta `PublicadorEventos` dentro da transação do caso de uso. O evento é gravado na tabela `outbox_evento` junto com a mudança de estado, e um job agendado (`RelayOutbox`) publica os pendentes:

- exchange topic `si.events` (declarada pelo serviço), routing key = tipo do evento (ex.: `user.created`);
- corpo = envelope [CloudEvents 1.0](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md) em modo estruturado (`content_type: application/json`), como exigem os contratos de `events/` do si-contracts:

  | Atributo | Valor |
  |---|---|
  | `specversion` | `1.0` |
  | `id` | id da linha na outbox (o mesmo valor do `message_id` AMQP) |
  | `source` | `/identity-service` |
  | `type` | tipo do evento, ex.: `user.created` |
  | `time` | instante em que o evento foi gravado na outbox |
  | `datacontenttype` | `application/json` |
  | `dataschema` | `https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/<tipo>.v1.json` |
  | `subject` | id do agregado de origem |
  | `data` | os campos do evento |

- propriedades AMQP: `message_id` = id da linha na outbox, `type` = tipo do evento, `app_id` = `identity-service`, headers `tipo_agregado` e `agregado_id`;
- publisher confirms: só marca `publicado_em` depois do ack do broker; em falha incrementa `tentativas` e tenta de novo no próximo ciclo;
- `FOR UPDATE SKIP LOCKED` permite várias réplicas rodando o relay.

A entrega é "pelo menos uma vez": consumidores devem ser idempotentes, deduplicando pelo `message_id` (ou pelo `id` do envelope, que é o mesmo valor).

### Eventos publicados

| Routing key | Quando | `data` | Contrato |
|---|---|---|---|
| `user.created` | primeiro `GET /v1/me` de um familiar ou primeiro resgate de código do idoso (perfil criado) | `perfilId`, `keycloakSub`, `tipo`, `nome`, `email` (ou `null`), `criadoEm`, `idosoId` (ou `null`) | [`events/user.created.v1.json`](https://github.com/SeniorInsight-Project/si-contracts/blob/develop/events/user.created.v1.json) |

O consumidor do `user.created` é o si-care-service (fila `care.q`, criada por ele). O teste `ContratoUserCreatedTest` valida o payload contra uma cópia do schema em `src/test/resources/contracts/`; ao mudar o contrato no si-contracts, atualize a cópia.

## Testes

```bash
mvn verify
```

Precisa de Docker: os testes de integração sobem PostgreSQL, RabbitMQ e Redis com Testcontainers. O `PrimeiroAcessoTest` sobe também um Keycloak 26 com um realm mínimo de teste (`src/test/resources/keycloak/realm-teste.json`, com um client público que só existe ali e usuários com senha de teste) para obter tokens reais. O `PareamentoTest` sobe um Keycloak com `token-exchange:v1` e o realm `realm-pareamento.json`, que reproduz o do si-infra (default role com `familiar`, e-mail como username, client `si-identity-service` com secret de teste). O care-service é simulado. O mesmo comando roda no CI (`.github/workflows/ci.yml`) em push e PR para `develop` e `main`.

## Docker

```bash
docker build -t si-identity-service .
docker run --rm -p 8080:8080 --env-file .env si-identity-service
```

Build multi-stage (Maven → JRE 21 Alpine), roda com usuário não root e tem `HEALTHCHECK` em `/actuator/health`. Opções extras da JVM vão em `JAVA_TOOL_OPTIONS`. Com `--env-file`, lembre que `localhost` dentro do container é o próprio container.

### Entrada para o Docker Compose do si-infra

O compose ainda não existe (nasce na si-infra#1). Trecho pronto para colar em `services:` — ajuste os nomes `postgres`, `rabbitmq` e `keycloak` e o realm para os que forem definidos lá:

```yaml
  identity-service:
    build:
      context: ../si-identity-service
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: seniorinsight
      DB_USERNAME: ${IDENTITY_DB_USERNAME}
      DB_PASSWORD: ${IDENTITY_DB_PASSWORD}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${IDENTITY_RABBITMQ_USERNAME}
      RABBITMQ_PASSWORD: ${IDENTITY_RABBITMQ_PASSWORD}
      KEYCLOAK_ISSUER_URI: ${KEYCLOAK_ISSUER_URI}
      KEYCLOAK_URL: http://keycloak:8080
      KEYCLOAK_CLIENT_SECRET: ${SI_IDENTITY_SERVICE_CLIENT_SECRET}
      REDIS_HOST: redis
      CARE_SERVICE_URL: http://care-service:8080
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      keycloak:
        condition: service_started
    expose:
      - "8080"
    restart: unless-stopped
```

O healthcheck já vem da imagem, então o gateway pode usar `depends_on: { identity-service: { condition: service_healthy } }`.

### Backfill do care-service

Familiares cadastrados antes de o care-service consumir o `user.created` não têm Familiar lá. Para criá-los, suba o identity uma vez com `IDENTITY_REPUBLICAR_FAMILIARES=true`: ao iniciar, ele grava de novo na outbox um `user.created` (com id novo) para cada perfil FAMILIAR, em lotes, e o relay publica. Depois desligue a variável. O care ignora quem já existe pelo `keycloakSub`, então repetir é seguro.
