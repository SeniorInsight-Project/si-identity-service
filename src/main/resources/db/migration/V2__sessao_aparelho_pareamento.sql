-- HU-007: sessão do aparelho do idoso criada no resgate do código de pareamento.

-- Quem gerou o código resgatado (claim "sub" do familiar). A tabela ainda não tem linhas (nada a preencher).
ALTER TABLE sessao_aparelho ADD COLUMN pareado_por VARCHAR(255) NOT NULL;

COMMENT ON COLUMN sessao_aparelho.pareado_por IS 'Claim "sub" do familiar que gerou o código de pareamento';

ALTER TABLE sessao_aparelho
    ADD CONSTRAINT ck_sessao_aparelho_plataforma CHECK (plataforma IN ('ANDROID', 'IOS'));

-- Uma sessão ativa por perfil e aparelho: parear de novo o mesmo aparelho revoga a anterior.
CREATE UNIQUE INDEX uk_sessao_aparelho_ativa
    ON sessao_aparelho (perfil_id, identificador_aparelho)
    WHERE revogado_em IS NULL;
