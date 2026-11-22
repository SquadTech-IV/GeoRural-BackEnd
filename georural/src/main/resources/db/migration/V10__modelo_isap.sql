-- =====================================================================
-- GeoRural DataHub - V10 - Modelagem do Indicador ISAP (US#24.04)
-- =====================================================================

-- 1. Enriquecimento da tabela AREA_PROTEGIDA
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
     WHERE table_name = 'AREA_PROTEGIDA' AND column_name = 'APR_SUBTIPO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE AREA_PROTEGIDA ADD (apr_subtipo VARCHAR2(100))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
     WHERE table_name = 'AREA_PROTEGIDA' AND column_name = 'APR_CODIGO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE AREA_PROTEGIDA ADD (apr_codigo VARCHAR2(80))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
     WHERE table_name = 'AREA_PROTEGIDA' AND column_name = 'APR_COMPETENCIA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE AREA_PROTEGIDA ADD (apr_competencia VARCHAR2(20))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes
     WHERE table_name = 'AREA_PROTEGIDA' AND index_name = 'IDX_APR_VER_TIPO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_apr_ver_tipo ON AREA_PROTEGIDA (ver_id, apr_tipo)';
    END IF;

    -- Verifica pela coluna (evita ORA-29879 caso indice espacial ja exista com outro nome)
    SELECT COUNT(*) INTO v_count FROM user_ind_columns
     WHERE table_name = 'AREA_PROTEGIDA' AND column_name = 'APR_GEOMETRIA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_apr_geometria ON AREA_PROTEGIDA (apr_geometria) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2';
    END IF;
END;
/

-- 2. Camada dissolvida por categoria e versao (evita dupla contagem intra-categoria)
CREATE TABLE AREA_PROTEGIDA_DISSOLVIDA (
    apd_id               NUMBER        GENERATED ALWAYS AS IDENTITY,
    ver_id               NUMBER        NOT NULL,
    apd_categoria        VARCHAR2(20)  NOT NULL,
    apd_filtro_aplicado  VARCHAR2(500) DEFAULT '*' NOT NULL,
    apd_qtd_feicoes      NUMBER(10)    DEFAULT 0 NOT NULL,
    apd_area_total_ha    NUMBER(16,4),
    apd_geometria        SDO_GEOMETRY,
    apd_data_geracao     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_area_prot_dissolvida PRIMARY KEY (apd_id),
    CONSTRAINT uk_apd_ver_cat_filtro UNIQUE (ver_id, apd_categoria, apd_filtro_aplicado),
    CONSTRAINT fk_apd_versao FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT ck_apd_categoria CHECK (apd_categoria IN ('UC','TERRA_INDIGENA','QUILOMBOLA','FLORESTA_PUBLICA'))
);

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM user_sdo_geom_metadata
     WHERE table_name = 'AREA_PROTEGIDA_DISSOLVIDA'
       AND column_name = 'APD_GEOMETRIA';

    IF v_count = 0 THEN
        INSERT INTO USER_SDO_GEOM_METADATA (table_name, column_name, diminfo, srid)
        VALUES ('AREA_PROTEGIDA_DISSOLVIDA', 'APD_GEOMETRIA',
                SDO_DIM_ARRAY(
                    SDO_DIM_ELEMENT('Longitude', -180, 180, 0.05),
                    SDO_DIM_ELEMENT('Latitude',   -90,  90, 0.05)
                ),
                4674);
    END IF;
END;
/

CREATE INDEX idx_apd_geometria ON AREA_PROTEGIDA_DISSOLVIDA (apd_geometria)
    INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;

-- 3. Tabela de Resumo do ISAP por Imovel e Categoria + TOTAL (US#24.02 / US#24.04)
CREATE TABLE RESULTADO_ISAP_RESUMO (
    isr_id                  NUMBER        GENERATED ALWAYS AS IDENTITY,
    imo_id                  NUMBER        NOT NULL,
    ver_id                  NUMBER        NOT NULL,
    isr_camada_ver_id       NUMBER,
    exe_id                  NUMBER,
    rca_id                  NUMBER,
    isr_categoria           VARCHAR2(20)  NOT NULL,
    isr_sobrepoe            CHAR(1),
    isr_area_sobreposta_ha  NUMBER(14,4),
    isr_pct_sobreposicao    NUMBER(7,4),
    isr_area_imovel_ha      NUMBER(14,4)  NOT NULL,
    isr_filtro_aplicado     VARCHAR2(500),
    isr_competencia_camada  VARCHAR2(20),
    isr_status              VARCHAR2(25)  NOT NULL,
    isr_vigente             CHAR(1)       DEFAULT 'N' NOT NULL,
    isr_data_calculo        TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_resultado_isap_resumo PRIMARY KEY (isr_id),
    CONSTRAINT uk_isr_imo_ver_cat UNIQUE (imo_id, ver_id, isr_categoria),
    CONSTRAINT fk_isr_imovel     FOREIGN KEY (imo_id) REFERENCES IMOVEL_RURAL (imo_id),
    CONSTRAINT fk_isr_versao     FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT fk_isr_camada_ver FOREIGN KEY (isr_camada_ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT fk_isr_execucao   FOREIGN KEY (exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id),
    CONSTRAINT fk_isr_regra      FOREIGN KEY (rca_id) REFERENCES REGRA_CALCULO (rca_id),
    CONSTRAINT ck_isr_categoria CHECK (isr_categoria IN
        ('UC','TERRA_INDIGENA','QUILOMBOLA','FLORESTA_PUBLICA','TOTAL')),
    CONSTRAINT ck_isr_sobrepoe CHECK (isr_sobrepoe IS NULL OR isr_sobrepoe IN ('S','N')),
    CONSTRAINT ck_isr_area_sobreposta CHECK (isr_area_sobreposta_ha IS NULL OR isr_area_sobreposta_ha >= 0),
    CONSTRAINT ck_isr_pct_sobreposicao CHECK (isr_pct_sobreposicao IS NULL OR (isr_pct_sobreposicao >= 0 AND isr_pct_sobreposicao <= 100)),
    CONSTRAINT ck_isr_area_imovel CHECK (isr_area_imovel_ha >= 0),
    CONSTRAINT ck_isr_status CHECK (isr_status IN
        ('COM_SOBREPOSICAO','SEM_SOBREPOSICAO','CAMADA_INDISPONIVEL')),
    CONSTRAINT ck_isr_vigente CHECK (isr_vigente IN ('S','N')),
    CONSTRAINT ck_isr_coerencia_status CHECK (
        (isr_status = 'CAMADA_INDISPONIVEL' AND isr_sobrepoe IS NULL AND isr_area_sobreposta_ha IS NULL AND isr_pct_sobreposicao IS NULL)
        OR
        (isr_status = 'SEM_SOBREPOSICAO' AND isr_sobrepoe = 'N' AND isr_area_sobreposta_ha = 0 AND isr_pct_sobreposicao = 0)
        OR
        (isr_status = 'COM_SOBREPOSICAO' AND isr_sobrepoe = 'S' AND isr_area_sobreposta_ha > 0 AND isr_pct_sobreposicao >= 0)
    )
);

CREATE INDEX idx_isr_ver_vigente ON RESULTADO_ISAP_RESUMO (ver_id, isr_vigente);
CREATE INDEX idx_isr_imo_vigente ON RESULTADO_ISAP_RESUMO (imo_id, isr_vigente);
CREATE INDEX idx_isr_exe_id      ON RESULTADO_ISAP_RESUMO (exe_id);
CREATE INDEX idx_isr_status      ON RESULTADO_ISAP_RESUMO (isr_status);

-- 4. Tabela de Detalhe do ISAP por Imovel e Feicao Atingida (US#24.02 / US#24.04)
CREATE TABLE RESULTADO_ISAP_DETALHE (
    isd_id                NUMBER        GENERATED ALWAYS AS IDENTITY,
    isr_id                NUMBER        NOT NULL,
    imo_id                NUMBER        NOT NULL,
    ver_id                NUMBER        NOT NULL,
    apr_id                NUMBER,
    isd_categoria         VARCHAR2(20)  NOT NULL,
    isd_nome_feicao       VARCHAR2(255),
    isd_tipo_feicao       VARCHAR2(100),
    isd_codigo_feicao     VARCHAR2(80),
    isd_area_ha           NUMBER(14,4)  NOT NULL,
    isd_pct_sobreposicao  NUMBER(7,4)   NOT NULL,
    CONSTRAINT pk_resultado_isap_detalhe PRIMARY KEY (isd_id),
    CONSTRAINT fk_isd_resumo   FOREIGN KEY (isr_id) REFERENCES RESULTADO_ISAP_RESUMO (isr_id) ON DELETE CASCADE,
    CONSTRAINT fk_isd_imovel   FOREIGN KEY (imo_id) REFERENCES IMOVEL_RURAL (imo_id),
    CONSTRAINT fk_isd_versao   FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT fk_isd_area_pro FOREIGN KEY (apr_id) REFERENCES AREA_PROTEGIDA (apr_id) ON DELETE SET NULL,
    CONSTRAINT ck_isd_categoria CHECK (isd_categoria IN
        ('UC','TERRA_INDIGENA','QUILOMBOLA','FLORESTA_PUBLICA')),
    CONSTRAINT ck_isd_area CHECK (isd_area_ha >= 0),
    CONSTRAINT ck_isd_pct  CHECK (isd_pct_sobreposicao >= 0 AND isd_pct_sobreposicao <= 100)
);

CREATE INDEX idx_isd_isr_id  ON RESULTADO_ISAP_DETALHE (isr_id);
CREATE INDEX idx_isd_imo_ver ON RESULTADO_ISAP_DETALHE (imo_id, ver_id);
CREATE INDEX idx_isd_cat     ON RESULTADO_ISAP_DETALHE (isd_categoria);

-- 5. Parametro configuravel de tipos de UC incluidos na regra v1 do ISAP
MERGE INTO PARAMETRO_REGRA_CALCULO dst
USING (
    SELECT r.rca_id,
           'tipos_uc_incluidos' AS prc_nome
      FROM REGRA_CALCULO r
      JOIN INDICADOR i ON i.ind_id = r.ind_id
     WHERE i.ind_sigla = 'ISAP'
       AND r.rca_versao = 'v1'
) src
ON (dst.rca_id = src.rca_id AND dst.prc_nome = src.prc_nome)
WHEN NOT MATCHED THEN
    INSERT (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel)
    VALUES (src.rca_id, 'tipos_uc_incluidos', 'Tipos/Categorias de UC incluídos (* = todos)', 'TEXTO', 'lista', '*', 'S');