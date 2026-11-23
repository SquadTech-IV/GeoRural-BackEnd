-- US#20 - Trilha de auditoria
-- 1. Amplia as operacoes aceitas (o codigo ja grava ATUALIZACAO_SITUACAO, REENVIO_CONVITE e ACESSO_NEGADO)
ALTER TABLE AUDITORIA DROP CONSTRAINT ck_aud_operacao;

ALTER TABLE AUDITORIA ADD CONSTRAINT ck_aud_operacao CHECK (aud_operacao IN (
    'INSERT','UPDATE','DELETE','LOGIN','LOGOUT','EXPORT','DOWNLOAD','API_ACCESS',
    'UPLOAD','EXECUCAO','ATUALIZACAO_SITUACAO','REENVIO_CONVITE','ACESSO_NEGADO'));

-- 2. Indice para a consulta por periodo (US#20.02)
CREATE INDEX idx_auditoria_data ON AUDITORIA(aud_data_hora);
