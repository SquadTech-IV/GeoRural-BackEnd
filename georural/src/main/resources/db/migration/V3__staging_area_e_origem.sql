-- =====================================================================
-- GeoRural DataHub - V3: area e arquivo de origem na FEICAO_STAGING
-- =====================================================================


ALTER TABLE FEICAO_STAGING ADD (
    stg_area_ha      NUMBER(16,4),
    stg_arq_bruto_id NUMBER
);

ALTER TABLE FEICAO_STAGING
    ADD CONSTRAINT fk_stg_arquivo FOREIGN KEY (stg_arq_bruto_id)
        REFERENCES ARQUIVO_BRUTO (arq_bruto_id);

CREATE INDEX idx_stg_exe_arquivo ON FEICAO_STAGING (exe_id, stg_arq_bruto_id);
