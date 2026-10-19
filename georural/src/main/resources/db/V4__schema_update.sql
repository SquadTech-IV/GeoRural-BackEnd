-- US#07 / T003: ajustes mínimos em ARQUIVO_BRUTO para o recebimento imutável de arquivos.
-- Executar manualmente, em um schema de desenvolvimento primeiro.
--
-- Pré-condição: ARQUIVO_BRUTO sem registros com usu_id nulo (verificar antes):
--   SELECT COUNT(*) FROM ARQUIVO_BRUTO WHERE usu_id IS NULL;

-- 1. A unicidade passa a ser por conjunto (e não global), e não pode prender o hash de registros em FALHA.
ALTER TABLE ARQUIVO_BRUTO DROP CONSTRAINT uk_arquivo_hash;

CREATE UNIQUE INDEX uk_arq_con_hash ON ARQUIVO_BRUTO (
    CASE WHEN arq_status IS NULL OR arq_status <> 'FALHA' THEN con_id END,
    CASE WHEN arq_status IS NULL OR arq_status <> 'FALHA' THEN arq_hash END
);

-- 2. Algoritmo do hash e via de entrada (upload ou API).
ALTER TABLE ARQUIVO_BRUTO ADD (
    arq_algoritmo_hash VARCHAR2(10) DEFAULT 'SHA-256' NOT NULL,
    arq_canal_entrada  VARCHAR2(10) DEFAULT 'UPLOAD'  NOT NULL
);

ALTER TABLE ARQUIVO_BRUTO ADD CONSTRAINT ck_arq_canal
    CHECK (arq_canal_entrada IN ('UPLOAD', 'API'));

-- 3. Responsável pelo envio obrigatório.
ALTER TABLE ARQUIVO_BRUTO MODIFY (usu_id NOT NULL);

-- 4. Situações: acrescenta RECEBENDO e FALHA, mantendo as que outros fluxos já usam.
ALTER TABLE ARQUIVO_BRUTO DROP CONSTRAINT ck_arq_status;

ALTER TABLE ARQUIVO_BRUTO ADD CONSTRAINT ck_arq_status CHECK (arq_status IN (
    'RECEBENDO', 'RECEBIDO', 'VALIDANDO', 'APROVADO', 'REJEITADO', 'PROCESSADO', 'FALHA'
));

-- Reversão (somente se necessário e com a tabela vazia):
--   DROP INDEX uk_arq_con_hash;
--   ALTER TABLE ARQUIVO_BRUTO DROP CONSTRAINT ck_arq_canal;
--   ALTER TABLE ARQUIVO_BRUTO DROP (arq_algoritmo_hash, arq_canal_entrada);
--   ALTER TABLE ARQUIVO_BRUTO MODIFY (usu_id NULL);
--   ALTER TABLE ARQUIVO_BRUTO DROP CONSTRAINT ck_arq_status;
--   ALTER TABLE ARQUIVO_BRUTO ADD CONSTRAINT ck_arq_status CHECK (arq_status IN
--       ('RECEBIDO', 'VALIDANDO', 'APROVADO', 'REJEITADO', 'PROCESSADO'));
--   ALTER TABLE ARQUIVO_BRUTO ADD CONSTRAINT uk_arquivo_hash UNIQUE (arq_hash);