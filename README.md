# si-identity-service

Serviço de identidade: perfis de usuário e pareamento do idoso por código/QR ("convite VIP") com token exchange no Keycloak. Login, senha, SSO e MFA ficam no Keycloak.

Parte do SeniorInsight — ver o Documento de Arquitetura de Software (seção 6) para as convenções.

Branches: `main` (principal, protegida) e `develop` (testes). Trabalhe em branches curtas por issue e abra PR para `develop`.

## Stack

Java 21, Spring Boot 3.5, PostgreSQL (schema `identity`, migrações com Flyway), RabbitMQ (exchange topic `si.events`) e Keycloak como emissor dos tokens (o serviço é só resource server).

## Organização do código

Pacote base `com.seniorinsight.identity`, em arquitetura hexagonal:

| Pacote | O que tem | Pode depender de |
|---|---|---|
| `domain` | Modelo e eventos de domínio, Java puro | nada (nem Spring, nem JPA) |
| `application` | Casos de uso e portas (ex.: `porta.saida.PublicadorEventos`) | `domain` |
| `adapters.in` | Entrada: HTTP, segurança, OpenAPI | `application`, `domain` |
| `adapters.out` | Saída: banco, outbox, RabbitMQ | `application`, `domain` |

`adapters.in` e `adapters.out` não se conhecem. As regras são verificadas pelo `ArquiteturaTest` (ArchUnit) a cada build.

## Rodando localmente

Pré-requisitos: JDK 21, Maven 3.9+ e Docker.

### Sem infraestrutura própria (Testcontainers)

Sobe o serviço com PostgreSQL e RabbitMQ em containers descartáveis:

```bash
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/seniorinsight mvn spring-boot:test-run
```

Sem um Keycloak no ar o serviço sobe normalmente: o health e o Swagger funcionam, e as rotas protegidas respondem 401.

### Com PostgreSQL, RabbitMQ e Keycloak já rodando

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
| todo o resto | token do Keycloak |

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
| `SERVER_PORT` | `8080` | Porta HTTP |

`KEYCLOAK_ISSUER_URI` precisa ser **exatamente** o valor do claim `iss` dos tokens e precisa ser acessível de dentro do serviço (é dele que vêm as chaves públicas). Em Docker, configure o hostname do Keycloak (`KC_HOSTNAME`) para que o issuer seja o mesmo dentro e fora da rede dos containers.

Ajustes opcionais do relay da outbox (valores padrão no `application.yml`): `IDENTITY_OUTBOX_RELAY_INTERVALO` (`PT1S`), `IDENTITY_OUTBOX_RELAY_TAMANHO_LOTE` (`100`) e `IDENTITY_OUTBOX_RELAY_TIMEOUT_CONFIRMACAO` (`PT5S`).

## Segurança

O serviço não tem login nem emite tokens: valida o JWT do Keycloak pelo issuer, sem sessão (stateless). As roles de `realm_access.roles` viram authorities `ROLE_<role>`, com o nome da role como está no realm (ex.: `familiar` → `ROLE_familiar`); os scopes do token viram `SCOPE_<scope>`.

## Eventos (outbox)

Quem precisa publicar um evento chama a porta `PublicadorEventos` dentro da transação do caso de uso. O evento é gravado na tabela `outbox_evento` junto com a mudança de estado, e um job agendado (`RelayOutbox`) publica os pendentes:

- exchange topic `si.events` (declarada pelo serviço), routing key = tipo do evento (ex.: `user.created`);
- corpo JSON com os campos do evento; `message_id` = id da linha na outbox, `type` = tipo do evento, headers `tipo_agregado` e `agregado_id`;
- publisher confirms: só marca `publicado_em` depois do ack do broker; em falha incrementa `tentativas` e tenta de novo no próximo ciclo;
- `FOR UPDATE SKIP LOCKED` permite várias réplicas rodando o relay.

A entrega é "pelo menos uma vez": consumidores devem ser idempotentes, deduplicando pelo `message_id`.

## Testes

```bash
mvn verify
```

Precisa de Docker: os testes de integração sobem PostgreSQL e RabbitMQ com Testcontainers. O mesmo comando roda no CI (`.github/workflows/ci.yml`) em push e PR para `develop` e `main`.

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
