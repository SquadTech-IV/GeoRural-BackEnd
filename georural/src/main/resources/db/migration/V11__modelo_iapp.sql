-- =====================================================================
-- GeoRural DataHub - V11 - Modelagem do Indicador IAPP (US#23.04)
-- =====================================================================

-- 1. Enriquecimento das tabelas APP e VEGETACAO_NATIVA para filtros parametrizaveis
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
     WHERE table_name = 'APP' AND column_name = 'APP_TIPO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE APP ADD (app_tipo VARCHAR2(100))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes
     WHERE table_name = 'APP' AND index_name = 'IDX_APP_VER_TIPO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_app_ver_tipo ON APP (ver_id, app_tipo)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_ind_columns
     WHERE table_name = 'APP' AND column_name = 'APP_GEOMETRIA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_app_geometria ON APP (app_geometria) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
     WHERE table_name = 'VEGETACAO_NATIVA' AND column_name = 'VEG_CLASSE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE VEGETACAO_NATIVA ADD (veg_classe VARCHAR2(100))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes
     WHERE table_name = 'VEGETACAO_NATIVA' AND index_name = 'IDX_VEG_VER_CLASSE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_veg_ver_classe ON VEGETACAO_NATIVA (ver_id, veg_classe)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_ind_columns
     WHERE table_name = 'VEGETACAO_NATIVA' AND column_name = 'VEG_GEOMETRIA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_veg_geometria ON VEGETACAO_NATIVA (veg_geometria) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2';
    END IF;
END;
/

-- 2. Camadas dissolvidas por versao e filtro (evita dupla contagem intra-camada - US#23.05)
CREATE TABLE APP_DISSOLVIDA (
    apd_id               NUMBER        GENERATED ALWAYS AS IDENTITY,
    ver_id               NUMBER        NOT NULL,
    apd_filtro_aplicado  VARCHAR2(500) DEFAULT '*' NOT NULL,
    apd_qtd_feicoes      NUMBER(10)    DEFAULT 0 NOT NULL,
    apd_area_total_ha    NUMBER(16,4),
    apd_geometria        SDO_GEOMETRY,
    apd_data_geracao     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_app_dissolvida PRIMARY KEY (apd_id),
    CONSTRAINT uk_app_diss_ver_filtro UNIQUE (ver_id, apd_filtro_aplicado),
    CONSTRAINT fk_app_diss_versao FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

CREATE TABLE VEGETACAO_DISSOLVIDA (
    ved_id               NUMBER        GENERATED ALWAYS AS IDENTITY,
    ver_id               NUMBER        NOT NULL,
    ved_filtro_aplicado  VARCHAR2(500) DEFAULT '*' NOT NULL,
    ved_qtd_feicoes      NUMBER(10)    DEFAULT 0 NOT NULL,
    ved_area_total_ha    NUMBER(16,4),
    ved_geometria        SDO_GEOMETRY,
    ved_data_geracao     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_veg_dissolvida PRIMARY KEY (ved_id),
    CONSTRAINT uk_ved_ver_filtro UNIQUE (ver_id, ved_filtro_aplicado),
    CONSTRAINT fk_ved_versao FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM user_sdo_geom_metadata
     WHERE table_name = 'APP_DISSOLVIDA'
       AND column_name = 'APD_GEOMETRIA';

    IF v_count = 0 THEN
        INSERT INTO USER_SDO_GEOM_METADATA (table_name, column_name, diminfo, srid)
        VALUES ('APP_DISSOLVIDA', 'APD_GEOMETRIA',
                SDO_DIM_ARRAY(
                    SDO_DIM_ELEMENT('Longitude', -180, 180, 0.05),
                    SDO_DIM_ELEMENT('Latitude',   -90,  90, 0.05)
                ),
                4674);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_sdo_geom_metadata
     WHERE table_name = 'VEGETACAO_DISSOLVIDA'
       AND column_name = 'VED_GEOMETRIA';

    IF v_count = 0 THEN
        INSERT INTO USER_SDO_GEOM_METADATA (table_name, column_name, diminfo, srid)
        VALUES ('VEGETACAO_DISSOLVIDA', 'VED_GEOMETRIA',
                SDO_DIM_ARRAY(
                    SDO_DIM_ELEMENT('Longitude', -180, 180, 0.05),
                    SDO_DIM_ELEMENT('Latitude',   -90,  90, 0.05)
                ),
                4674);
    END IF;

    COMMIT;
END;
/

CREATE INDEX idx_app_diss_geometria ON APP_DISSOLVIDA (apd_geometria)
    INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;

CREATE INDEX idx_ved_geometria ON VEGETACAO_DISSOLVIDA (ved_geometria)
    INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;

-- 3. Tabela de Resultado do IAPP por Imovel e Versao (US#23.02 / US#23.04)
CREATE TABLE RESULTADO_IAPP (
    iap_id                    NUMBER        GENERATED ALWAYS AS IDENTITY,
    imo_id                    NUMBER        NOT NULL,
    ver_id                    NUMBER        NOT NULL,
    iap_app_ver_id            NUMBER,
    iap_veg_ver_id            NUMBER,
    exe_id                    NUMBER,
    rca_id                    NUMBER,
    iap_app_total_ha          NUMBER(14,4),
    iap_app_vegetada_ha       NUMBER(14,4),
    iap_passivo_ha            NUMBER(14,4),
    iap_iapp_pct              NUMBER(7,4),
    iap_area_imovel_ha        NUMBER(14,4)  NOT NULL,
    iap_filtro_tipos_app      VARCHAR2(500),
    iap_filtro_classes_veg    VARCHAR2(500),
    iap_competencia_app       VARCHAR2(20),
    iap_competencia_veg       VARCHAR2(20),
    iap_status                VARCHAR2(25)  NOT NULL,
    iap_mensagem              VARCHAR2(500) NOT NULL,
    iap_app_vegetada_geom     SDO_GEOMETRY,
    iap_passivo_geom          SDO_GEOMETRY,
    iap_vigente               CHAR(1)       DEFAULT 'N' NOT NULL,
    iap_data_calculo          TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_resultado_iapp PRIMARY KEY (iap_id),
    CONSTRAINT uk_iap_imo_ver UNIQUE (imo_id, ver_id),
    CONSTRAINT fk_iap_imovel    FOREIGN KEY (imo_id) REFERENCES IMOVEL_RURAL (imo_id),
    CONSTRAINT fk_iap_versao    FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT fk_iap_app_ver   FOREIGN KEY (iap_app_ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT fk_iap_veg_ver   FOREIGN KEY (iap_veg_ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT fk_iap_execucao  FOREIGN KEY (exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id),
    CONSTRAINT fk_iap_regra     FOREIGN KEY (rca_id) REFERENCES REGRA_CALCULO (rca_id),
    CONSTRAINT ck_iap_app_total    CHECK (iap_app_total_ha IS NULL OR iap_app_total_ha >= 0),
    CONSTRAINT ck_iap_app_vegetada CHECK (iap_app_vegetada_ha IS NULL OR iap_app_vegetada_ha >= 0),
    CONSTRAINT ck_iap_passivo      CHECK (iap_passivo_ha IS NULL OR iap_passivo_ha >= 0),
    CONSTRAINT ck_iap_pct          CHECK (iap_iapp_pct IS NULL OR (iap_iapp_pct >= 0 AND iap_iapp_pct <= 100)),
    CONSTRAINT ck_iap_area_imovel  CHECK (iap_area_imovel_ha >= 0),
    CONSTRAINT ck_iap_status       CHECK (iap_status IN ('CALCULADO', 'SEM_APP', 'CAMADA_INDISPONIVEL')),
    CONSTRAINT ck_iap_vigente      CHECK (iap_vigente IN ('S', 'N')),
    CONSTRAINT ck_iap_coerencia_status CHECK (
        (iap_status IN ('SEM_APP', 'CAMADA_INDISPONIVEL')
            AND iap_app_total_ha IS NULL
            AND iap_app_vegetada_ha IS NULL
            AND iap_passivo_ha IS NULL
            AND iap_iapp_pct IS NULL)
        OR
        (iap_status = 'CALCULADO'
            AND iap_app_total_ha > 0
            AND iap_app_vegetada_ha >= 0
            AND iap_app_vegetada_ha <= iap_app_total_ha
            AND iap_passivo_ha >= 0
            AND iap_iapp_pct >= 0
            AND iap_iapp_pct <= 100)
    )
);

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM user_sdo_geom_metadata
     WHERE table_name = 'RESULTADO_IAPP'
       AND column_name = 'IAP_APP_VEGETADA_GEOM';

    IF v_count = 0 THEN
        INSERT INTO USER_SDO_GEOM_METADATA (table_name, column_name, diminfo, srid)
        VALUES ('RESULTADO_IAPP', 'IAP_APP_VEGETADA_GEOM',
                SDO_DIM_ARRAY(
                    SDO_DIM_ELEMENT('Longitude', -180, 180, 0.05),
                    SDO_DIM_ELEMENT('Latitude',   -90,  90, 0.05)
                ),
                4674);
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_sdo_geom_metadata
     WHERE table_name = 'RESULTADO_IAPP'
       AND column_name = 'IAP_PASSIVO_GEOM';

    IF v_count = 0 THEN
        INSERT INTO USER_SDO_GEOM_METADATA (table_name, column_name, diminfo, srid)
        VALUES ('RESULTADO_IAPP', 'IAP_PASSIVO_GEOM',
                SDO_DIM_ARRAY(
                    SDO_DIM_ELEMENT('Longitude', -180, 180, 0.05),
                    SDO_DIM_ELEMENT('Latitude',   -90,  90, 0.05)
                ),
                4674);
    END IF;

    COMMIT;
END;
/

CREATE INDEX idx_iap_ver_vigente ON RESULTADO_IAPP (ver_id, iap_vigente);
CREATE INDEX idx_iap_imo_vigente ON RESULTADO_IAPP (imo_id, iap_vigente);
CREATE INDEX idx_iap_exe_id      ON RESULTADO_IAPP (exe_id);
CREATE INDEX idx_iap_status      ON RESULTADO_IAPP (iap_status);

-- 4. Parametros configuraveis da regra v1 do IAPP (US#23.01 / US#23.02 / US#23.04)
MERGE INTO PARAMETRO_REGRA_CALCULO dst
USING (
    SELECT r.rca_id,
           'tipos_app_incluidos' AS prc_nome
      FROM REGRA_CALCULO r
      JOIN INDICADOR i ON i.ind_id = r.ind_id
     WHERE i.ind_sigla = 'IAPP'
       AND r.rca_versao = 'v1'
) src
ON (dst.rca_id = src.rca_id AND dst.prc_nome = src.prc_nome)
WHEN NOT MATCHED THEN
    INSERT (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel)
    VALUES (src.rca_id, 'tipos_app_incluidos', 'Tipos de APP incluídos (* = todos)', 'TEXTO', 'lista', '*', 'S');

MERGE INTO PARAMETRO_REGRA_CALCULO dst
USING (
    SELECT r.rca_id,
           'classes_vegetacao_incluidas' AS prc_nome
      FROM REGRA_CALCULO r
      JOIN INDICADOR i ON i.ind_id = r.ind_id
     WHERE i.ind_sigla = 'IAPP'
       AND r.rca_versao = 'v1'
) src
ON (dst.rca_id = src.rca_id AND dst.prc_nome = src.prc_nome)
WHEN NOT MATCHED THEN
    INSERT (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel)
    VALUES (src.rca_id, 'classes_vegetacao_incluidas', 'Classes de vegetação nativa incluídas (* = todas)', 'TEXTO', 'lista', '*', 'S');

MERGE INTO PARAMETRO_REGRA_CALCULO dst
USING (
    SELECT r.rca_id,
           'minimo_iapp_conforme_pct' AS prc_nome
      FROM REGRA_CALCULO r
      JOIN INDICADOR i ON i.ind_id = r.ind_id
     WHERE i.ind_sigla = 'IAPP'
       AND r.rca_versao = 'v1'
) src
ON (dst.rca_id = src.rca_id AND dst.prc_nome = src.prc_nome)
WHEN NOT MATCHED THEN
    INSERT (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel, prc_min, prc_max)
    VALUES (src.rca_id, 'minimo_iapp_conforme_pct', 'Percentual mínimo de APP vegetada para conformidade', 'PERCENTUAL', '%', '100', 'S', 0, 100);