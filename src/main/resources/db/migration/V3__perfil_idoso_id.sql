-- Id do idoso no care-service, guardado no perfil IDOSO para o report-service identificar o titular na exclusão (LGPD).
-- Nulo para o familiar e para o idoso cujo código de pareamento foi gerado sem idosoId (nada a preencher nas linhas existentes).
ALTER TABLE perfil ADD COLUMN idoso_id UUID;

COMMENT ON COLUMN perfil.idoso_id IS 'Id do idoso no care-service (idosos.id). Só para tipo IDOSO, e opcional';

-- Um idoso do care tem no máximo um perfil.
CREATE UNIQUE INDEX uk_perfil_idoso_id ON perfil (idoso_id) WHERE idoso_id IS NOT NULL;
