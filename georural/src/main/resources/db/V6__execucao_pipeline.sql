-- Migration para adequação da EXECUCAO_PIPELINE aos requisitos da US#08 (T02)
ALTER TABLE EXECUCAO_PIPELINE ADD (
    arq_bruto_id            NUMBER,
    exe_registros_recebidos NUMBER DEFAULT 0,
    exe_registros_aprovados NUMBER DEFAULT 0
);

ALTER TABLE EXECUCAO_PIPELINE ADD CONSTRAINT fk_exe_arquivo_bruto
    FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id);

CREATE INDEX idx_execucao_arquivo ON EXECUCAO_PIPELINE(arq_bruto_id);