-- HU-022: exclusão da conta a pedido do report-service (lgpd.erasure.requested).

-- Um pedido por protocolo. O sub fica só enquanto o pedido está pendente: serve para retomar a exclusão e é apagado
-- na conclusão.
CREATE TABLE exclusao_pedido (
    protocolo    UUID         NOT NULL,
    tipo_titular VARCHAR(20)  NOT NULL,
    titular_sub  VARCHAR(255),
    situacao     VARCHAR(20)  NOT NULL,
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    concluido_em TIMESTAMPTZ,
    CONSTRAINT pk_exclusao_pedido PRIMARY KEY (protocolo),
    CONSTRAINT ck_exclusao_pedido_tipo CHECK (tipo_titular IN ('FAMILIAR', 'IDOSO')),
    CONSTRAINT ck_exclusao_pedido_situacao CHECK (situacao IN ('PENDENTE', 'CONCLUIDO')),
    CONSTRAINT ck_exclusao_pedido_sub CHECK (situacao = 'CONCLUIDO' OR titular_sub IS NOT NULL)
);

COMMENT ON COLUMN exclusao_pedido.titular_sub IS 'Claim "sub" do titular; nulo depois de concluído';

-- Só o hash do sub: impede que um access token ainda válido recrie o perfil depois da exclusão.
CREATE TABLE titular_excluido (
    chave       BYTEA       NOT NULL,
    excluido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_titular_excluido PRIMARY KEY (chave)
);

COMMENT ON COLUMN titular_excluido.chave IS 'sha256 do claim "sub" do titular excluído';
