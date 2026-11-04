# ADR 0001 — Como o Keycloak emite o token do idoso

- **Status:** aceita
- **Data:** 2026-11-04
- **Card:** #8 (a issue #7, onde a decisão nasceu, foi juntada a ela)
- **Referências:** Documento de Arquitetura, seção 4.4 (Figura 10) e Tabela 3; HU-007; RF-006, RF-007, RN-020

## Contexto

O idoso não tem e-mail nem senha. O app dele recebe um código (ou QR) gerado pelo Familiar Administrador e precisa sair do resgate com uma sessão do Keycloak, sem nenhuma tela de login. A arquitetura prevê token exchange com impersonação, que no Keycloak 26 só existe no token exchange legado (V1, preview). O token exchange padrão (V2, já ligado no client `si-identity-service`) não aceita `requested_subject`.

Opções avaliadas:

1. **Token exchange V1** (`--features=token-exchange:v1`): o identity-service, com a service account do client `si-identity-service`, troca o próprio token por um token do usuário do idoso.
2. **Autenticador próprio no Keycloak**: um SPI em Java que valida o código de pareamento dentro de um fluxo de autenticação. Exige build e versionamento de um JAR na imagem do Keycloak e um fluxo novo no realm, e tira a regra do código do identity-service.

## Decisão

Usar o **token exchange V1** (opção 1).

O que foi verificado num Keycloak 26.3.3 com o realm do si-infra:

- Basta ligar `token-exchange:v1`. A service account do `si-identity-service` já tem `manage-users` e `impersonation` (realm-management) no realm, e isso é suficiente para a impersonação.
- A troca precisa mandar o token da própria service account como `subject_token`. A impersonação "pelada" (só `requested_subject`) é recusada sem permissões finas por usuário.
- `admin-fine-grained-authz:v1` **não é necessário**. Ele só seria exigido para trocar com `audience=si-mobile` (permissão `token-exchange` no client de destino), e mesmo assim o token sai com `azp = si-identity-service`: o refresh com `client_id=si-mobile` é recusado ("Token client and authorized client don't match"). Ligar o FGAP V1 não resolve o refresh e desliga o FGAP V2 do realm. Por isso fica fora.
- O realm usa o e-mail como username (`registrationEmailAsUsername`), então o Keycloak exige e-mail. O usuário do idoso recebe um e-mail técnico, não roteável: `idoso-<chave>@idoso.seniorinsight.invalid`. O **Perfil** do idoso continua com `email = null`, como manda o contrato.
- A default role do realm (`default-roles-seniorinsight`) inclui `familiar`. O identity-service tira essa default role do usuário do idoso e atribui `idoso` e `offline_access`. Sem isso o idoso poderia gerar códigos de pareamento.

### Fluxo do resgate

1. `client_credentials` do `si-identity-service` (token de serviço).
2. Admin API: busca o usuário do idoso pelo e-mail técnico. Se não existir, cria (nome do idoso, e-mail técnico verificado) e ajusta as roles.
3. Token exchange: `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token=<token de serviço>`, `requested_subject=<id do usuário>`, `requested_token_type=urn:ietf:params:oauth:token-type:refresh_token`.
4. O identity-service devolve `accessToken`, `refreshToken`, `expiresIn` e `refreshExpiresIn` no `ResultadoPareamento`.

A chave do usuário é o `idosoId` do care-service, quando o familiar informa, e um UUID novo quando não informa. Com `idosoId`, o mesmo idoso pareado de novo (aparelho trocado) reaproveita usuário e perfil.

## Consequências

- **Feature em preview.** O token exchange V1 pode mudar ou sumir numa versão futura do Keycloak. A versão fica fixada no si-infra (26.3.3) e a atualização precisa repetir os testes de `PareamentoTest`, que sobem um Keycloak real com a mesma feature.
- **Refresh preso ao `si-identity-service`.** O refresh token do idoso só renova com o `client_secret` do identity-service, então o app do idoso **não consegue renovar sozinho**. Hoje a sessão dura o que o realm permite (`refreshExpiresIn`, 30 min de inatividade no padrão). É preciso decidir, em outro card, entre:
  - uma rota de renovação no identity-service (por exemplo `POST /v1/pareamentos/sessoes/{sessaoAparelhoId}/renovacoes`), que confere se a `sessao_aparelho` não foi revogada e renova com o secret. Isso também dá ao familiar um jeito de revogar o aparelho;
  - ou pedir `offline_access` na troca e aumentar o tempo de sessão offline no realm, ainda dependendo do identity-service para renovar.
  As duas mudam o contrato `openapi/identity-v1.yaml`.
- **Mudança no si-infra:** subir o Keycloak com `--features=token-exchange:v1` (compose e Swarm) e passar ao identity-service o secret do client, o Redis e a URL do care-service. O `realm-seniorinsight.json` não precisa mudar.
- O secret do `si-identity-service` passa a ser usado pelo serviço (`KEYCLOAK_CLIENT_SECRET`) e dá poder de criar usuários e impersonar no realm. Ele fica em segredo do Swarm, nunca no repositório.
