-- V4: nome do imovel rural e a origem do nome
ALTER TABLE IMOVEL_RURAL ADD (
    imo_nome        VARCHAR2(200),
    imo_nome_origem VARCHAR2(12)
);

ALTER TABLE IMOVEL_RURAL
    ADD CONSTRAINT ck_imo_nome_origem CHECK (
        imo_nome_origem IS NULL OR imo_nome_origem IN ('ARQUIVO','MANUAL','PLANILHA'));

-- indice so para busca por nome
CREATE INDEX idx_imovel_nome ON IMOVEL_RURAL (UPPER(imo_nome));
