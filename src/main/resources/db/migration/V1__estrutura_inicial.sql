-- Estrutura inicial do identity-service. Roda no schema "identity" (spring.flyway.default-schema).

CREATE TABLE perfil (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    keycloak_sub  VARCHAR(255) NOT NULL,
    tipo          VARCHAR(20)  NOT NULL,
    nome          VARCHAR(150) NOT NULL,
    email         VARCHAR(320),
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_perfil PRIMARY KEY (id),
    CONSTRAINT uk_perfil_keycloak_sub UNIQUE (keycloak_sub),
    CONSTRAINT ck_perfil_tipo CHECK (tipo IN ('FAMILIAR', 'IDOSO'))
);

COMMENT ON COLUMN perfil.keycloak_sub IS 'Claim "sub" do usuário no Keycloak';
COMMENT ON COLUMN perfil.email IS 'Opcional: o idoso pareado por código/QR pode não ter e-mail';

CREATE TABLE sessao_aparelho (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    perfil_id              UUID         NOT NULL,
    identificador_aparelho VARCHAR(255) NOT NULL,
    plataforma             VARCHAR(20)  NOT NULL,
    criado_em              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revogado_em            TIMESTAMPTZ,
    CONSTRAINT pk_sessao_aparelho PRIMARY KEY (id),
    CONSTRAINT fk_sessao_aparelho_perfil FOREIGN KEY (perfil_id) REFERENCES perfil (id)
);

CREATE INDEX ix_sessao_aparelho_perfil_id ON sessao_aparelho (perfil_id);

CREATE TABLE outbox_evento (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tipo_agregado VARCHAR(100) NOT NULL,
    agregado_id   UUID         NOT NULL,
    tipo_evento   VARCHAR(150) NOT NULL,
    payload       JSONB        NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_em  TIMESTAMPTZ,
    tentativas    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_evento PRIMARY KEY (id)
);

COMMENT ON COLUMN outbox_evento.tipo_evento IS 'Routing key na exchange si.events, ex.: user.created';

-- O relay só lê pendentes, em ordem de criação.
CREATE INDEX ix_outbox_evento_pendentes ON outbox_evento (criado_em) WHERE publicado_em IS NULL;
