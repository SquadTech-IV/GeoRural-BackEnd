-- GeoRural DataHub - Database Script - 01 Schema

-- Seguranca e auditoria
CREATE TABLE PERFIL (
    per_id      NUMBER        GENERATED ALWAYS AS IDENTITY,
    per_funcao  VARCHAR2(20)  NOT NULL,
    per_nome    VARCHAR2(60),
    CONSTRAINT pk_perfil PRIMARY KEY (per_id),
    CONSTRAINT uk_perfil_funcao UNIQUE (per_funcao),
    CONSTRAINT ck_perfil_funcao CHECK (per_funcao IN ('ADMIN','OPERADOR','ANALISTA','GESTOR','AUDITOR'))
);

CREATE TABLE USUARIO (
    usu_id            NUMBER        GENERATED ALWAYS AS IDENTITY,
    per_id            NUMBER        NOT NULL,
    usu_nome          VARCHAR2(120) NOT NULL,
    usu_email         VARCHAR2(255) NOT NULL,
    usu_senha_hash    VARCHAR2(60)  NOT NULL,
    usu_status        CHAR(1)       DEFAULT 'S' NOT NULL,
    usu_data_cadastro TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_usuario PRIMARY KEY (usu_id),
    CONSTRAINT uk_usuario_email UNIQUE (usu_email),
    CONSTRAINT fk_usu_perfil FOREIGN KEY (per_id) REFERENCES PERFIL (per_id),
    CONSTRAINT ck_usuario_status CHECK (usu_status IN ('S','N'))
);

CREATE TABLE AUDITORIA (
    aud_id               NUMBER        GENERATED ALWAYS AS IDENTITY,
    usu_id               NUMBER,
    aud_operacao         VARCHAR2(20)  NOT NULL,
    aud_entidade_afetada VARCHAR2(30)  NOT NULL,
    aud_registro_id      NUMBER,
    aud_dados_antigos    CLOB,
    aud_dados_novos      CLOB,
    aud_ip_origem        VARCHAR2(45),
    aud_data_hora        TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_auditoria PRIMARY KEY (aud_id),
    CONSTRAINT fk_aud_usuario FOREIGN KEY (usu_id) REFERENCES USUARIO (usu_id),
    CONSTRAINT ck_aud_operacao CHECK (aud_operacao IN ('INSERT','UPDATE','DELETE','LOGIN','EXPORT')),
    CONSTRAINT ck_aud_antigos_json CHECK (aud_dados_antigos IS NULL OR aud_dados_antigos IS JSON),
    CONSTRAINT ck_aud_novos_json   CHECK (aud_dados_novos  IS NULL OR aud_dados_novos  IS JSON)
);

-- Indicadores e catalogo de tipos
CREATE TABLE INDICADOR (
    ind_id        NUMBER        GENERATED ALWAYS AS IDENTITY,
    ind_sigla     VARCHAR2(10)  NOT NULL,
    ind_nome      VARCHAR2(80)  NOT NULL,
    ind_descricao VARCHAR2(400),
    ind_unidade   VARCHAR2(40),
    CONSTRAINT pk_indicador PRIMARY KEY (ind_id),
    CONSTRAINT uk_indicador_sigla UNIQUE (ind_sigla)
);

CREATE TABLE TIPO_CONJUNTO (
    tip_id          NUMBER        GENERATED ALWAYS AS IDENTITY,
    tip_codigo      VARCHAR2(30)  NOT NULL,
    tip_nome        VARCHAR2(120) NOT NULL,
    tip_esquema     CLOB,
    tip_formatos    VARCHAR2(100),
    tip_indicadores VARCHAR2(60),
    CONSTRAINT pk_tipo_conjunto PRIMARY KEY (tip_id),
    CONSTRAINT uk_tipo_codigo UNIQUE (tip_codigo),
    CONSTRAINT ck_tipo_esquema_json CHECK (tip_esquema IS NULL OR tip_esquema IS JSON)
);

-- Fontes e conjuntos
CREATE TABLE FONTE_DO_DADO (
    fon_id            NUMBER        GENERATED ALWAYS AS IDENTITY,
    fon_nome          VARCHAR2(120) NOT NULL,
    fon_sigla         VARCHAR2(20),
    fon_esfera        VARCHAR2(20),
    fon_status        CHAR(1)       DEFAULT 'S' NOT NULL,
    usu_id            NUMBER,
    fon_data_cadastro TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_fonte PRIMARY KEY (fon_id),
    CONSTRAINT uk_fonte_nome UNIQUE (fon_nome),
    CONSTRAINT fk_fon_usuario FOREIGN KEY (usu_id) REFERENCES USUARIO (usu_id),
    CONSTRAINT ck_fonte_status CHECK (fon_status IN ('S','N'))
);

CREATE TABLE CONJUNTO_DADOS (
    con_id             NUMBER        GENERATED ALWAYS AS IDENTITY,
    fon_id             NUMBER        NOT NULL,
    tip_id             NUMBER        NOT NULL,
    usu_id             NUMBER,
    con_nome           VARCHAR2(160) NOT NULL,
    con_descricao      VARCHAR2(400),
    con_srs            NUMBER(6)     DEFAULT 4674,
    con_cobertura      VARCHAR2(30),
    con_periodicidade  VARCHAR2(20),
    con_forma_entrada  VARCHAR2(10)  DEFAULT 'UPLOAD' NOT NULL,
    con_api_endpoint   VARCHAR2(500),
    con_api_formato    VARCHAR2(20),
    con_api_auth       VARCHAR2(200),
    con_situacao       VARCHAR2(15)  DEFAULT 'RASCUNHO' NOT NULL,
    con_ver_vigente_id NUMBER,
    con_data_cadastro  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_conjunto PRIMARY KEY (con_id),
    CONSTRAINT uk_conjunto_fonte_tipo UNIQUE (fon_id, tip_id),
    CONSTRAINT fk_con_fonte   FOREIGN KEY (fon_id) REFERENCES FONTE_DO_DADO (fon_id),
    CONSTRAINT fk_con_tipo    FOREIGN KEY (tip_id) REFERENCES TIPO_CONJUNTO (tip_id),
    CONSTRAINT fk_con_usuario FOREIGN KEY (usu_id) REFERENCES USUARIO (usu_id),
    CONSTRAINT ck_con_situacao CHECK (con_situacao IN ('RASCUNHO','EM_VALIDACAO','PUBLICADO','ARQUIVADO')),
    CONSTRAINT ck_con_entrada  CHECK (con_forma_entrada IN ('UPLOAD','API','AMBOS'))
);

-- Pipeline e versionamento
CREATE TABLE EXECUCAO_PIPELINE (
    exe_id                    NUMBER       GENERATED ALWAYS AS IDENTITY,
    con_id                    NUMBER       NOT NULL,
    usu_id                    NUMBER,
    exe_etapa                 VARCHAR2(15) NOT NULL,
    exe_dag_run_id            VARCHAR2(120),
    exe_status                VARCHAR2(15) DEFAULT 'EM_ANDAMENTO' NOT NULL,
    exe_registros_processados NUMBER       DEFAULT 0,
    exe_registros_rejeitados  NUMBER       DEFAULT 0,
    exe_ver_gerada_id         NUMBER,
    exe_inicio                TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    exe_fim                   TIMESTAMP,
    exe_duracao_seg           NUMBER,
    CONSTRAINT pk_execucao PRIMARY KEY (exe_id),
    CONSTRAINT fk_exe_conjunto FOREIGN KEY (con_id) REFERENCES CONJUNTO_DADOS (con_id),
    CONSTRAINT fk_exe_usuario  FOREIGN KEY (usu_id) REFERENCES USUARIO (usu_id),
    CONSTRAINT ck_exe_etapa CHECK (exe_etapa IN
        ('CADASTRO','INGESTAO','VALIDACAO','TRATAMENTO','CALCULO','QUALIDADE','PUBLICACAO','CONSUMO')),
    CONSTRAINT ck_exe_status CHECK (exe_status IN ('EM_ANDAMENTO','CONCLUIDA','COM_RESSALVA','FALHOU'))
);

CREATE TABLE VERSAO (
    ver_id              NUMBER        GENERATED ALWAYS AS IDENTITY,
    con_id              NUMBER        NOT NULL,
    usu_id              NUMBER,
    ver_numero          NUMBER        NOT NULL,
    ver_hash            CHAR(64)      NOT NULL,
    ver_competencia     VARCHAR2(20),
    ver_situacao        VARCHAR2(15)  DEFAULT 'RASCUNHO' NOT NULL,
    ver_exe_id          NUMBER,
    ver_data_publicacao TIMESTAMP,
    ver_data_criacao    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_versao PRIMARY KEY (ver_id),
    CONSTRAINT uk_versao_con_num UNIQUE (con_id, ver_numero),
    CONSTRAINT fk_ver_conjunto FOREIGN KEY (con_id) REFERENCES CONJUNTO_DADOS (con_id),
    CONSTRAINT fk_ver_usuario  FOREIGN KEY (usu_id) REFERENCES USUARIO (usu_id),
    CONSTRAINT ck_ver_situacao CHECK (ver_situacao IN ('RASCUNHO','VIGENTE','ANTERIOR'))
);

ALTER TABLE CONJUNTO_DADOS
    ADD CONSTRAINT fk_con_ver_vigente FOREIGN KEY (con_ver_vigente_id) REFERENCES VERSAO (ver_id);
ALTER TABLE EXECUCAO_PIPELINE
    ADD CONSTRAINT fk_exe_ver_gerada FOREIGN KEY (exe_ver_gerada_id) REFERENCES VERSAO (ver_id);
ALTER TABLE VERSAO
    ADD CONSTRAINT fk_ver_execucao FOREIGN KEY (ver_exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id);

CREATE TABLE ENTRADA_EXECUCAO (
    ent_id     NUMBER        GENERATED ALWAYS AS IDENTITY,
    exe_id     NUMBER        NOT NULL,
    ver_id     NUMBER        NOT NULL,
    ent_papel  VARCHAR2(40),
    CONSTRAINT pk_entrada_exec PRIMARY KEY (ent_id),
    CONSTRAINT uk_entrada_exec UNIQUE (exe_id, ver_id),
    CONSTRAINT fk_ent_execucao FOREIGN KEY (exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id),
    CONSTRAINT fk_ent_versao   FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

CREATE TABLE PARAMETRO_EXECUCAO (
    par_id     NUMBER        GENERATED ALWAYS AS IDENTITY,
    exe_id     NUMBER        NOT NULL,
    par_nome   VARCHAR2(60)  NOT NULL,
    par_valor  VARCHAR2(200),
    CONSTRAINT pk_parametro_exec PRIMARY KEY (par_id),
    CONSTRAINT uk_parametro_exec UNIQUE (exe_id, par_nome),
    CONSTRAINT fk_par_execucao FOREIGN KEY (exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id)
);

CREATE TABLE TRANSFORMACAO (
    tra_id        NUMBER        GENERATED ALWAYS AS IDENTITY,
    exe_id        NUMBER        NOT NULL,
    tra_ordem     NUMBER(3)     NOT NULL,
    tra_tipo      VARCHAR2(30),
    tra_descricao VARCHAR2(400) NOT NULL,
    tra_data      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_transformacao PRIMARY KEY (tra_id),
    CONSTRAINT uk_transformacao_ordem UNIQUE (exe_id, tra_ordem),
    CONSTRAINT fk_tra_execucao FOREIGN KEY (exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id),
    CONSTRAINT ck_tra_tipo CHECK (tra_tipo IN
        ('VALIDACAO','PADRONIZACAO','REPROJECAO','DISSOLVER','CRUZAMENTO','CALCULO','AGREGACAO','OUTRO'))
);

-- Ingestao, zonas e qualidade
CREATE TABLE ARQUIVO_BRUTO (
    arq_bruto_id           NUMBER        GENERATED ALWAYS AS IDENTITY,
    con_id                 NUMBER        NOT NULL,
    usu_id                 NUMBER,
    arq_nome               VARCHAR2(255) NOT NULL,
    arq_formato            VARCHAR2(15),
    arq_path_armazenamento VARCHAR2(500) NOT NULL,
    arq_tamanho_bytes      NUMBER(15),
    arq_status             VARCHAR2(15),
    arq_hash               CHAR(64)      NOT NULL,
    arq_data_upload        TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_arquivo_bruto PRIMARY KEY (arq_bruto_id),
    CONSTRAINT uk_arquivo_hash UNIQUE (arq_hash),
    CONSTRAINT fk_arq_conjunto FOREIGN KEY (con_id) REFERENCES CONJUNTO_DADOS (con_id),
    CONSTRAINT fk_arq_usuario  FOREIGN KEY (usu_id) REFERENCES USUARIO (usu_id),
    CONSTRAINT ck_arq_status CHECK (arq_status IN ('RECEBIDO','VALIDANDO','APROVADO','REJEITADO','PROCESSADO'))
);

CREATE TABLE MOVIMENTACAO_ZONA (
    mov_id       NUMBER        GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id NUMBER        NOT NULL,
    exe_id       NUMBER,
    mov_zona     VARCHAR2(12)  NOT NULL,
    mov_status   VARCHAR2(15),
    mov_obs      VARCHAR2(400),
    mov_data     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_movimentacao PRIMARY KEY (mov_id),
    CONSTRAINT fk_mov_arquivo  FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_mov_execucao FOREIGN KEY (exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id),
    CONSTRAINT ck_mov_zona CHECK (mov_zona IN ('BRUTA','QUARENTENA','TRATADA','PUBLICADA'))
);

CREATE TABLE VALIDACAO (
    val_id                  NUMBER       GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id            NUMBER       NOT NULL,
    exe_id                  NUMBER,
    val_total_registros     NUMBER(10),
    val_registros_validos   NUMBER(10),
    val_registros_invalidos NUMBER(10),
    val_status_final        VARCHAR2(15),
    val_data_execucao       TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_validacao PRIMARY KEY (val_id),
    CONSTRAINT fk_val_arquivo  FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_val_execucao FOREIGN KEY (exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id),
    CONSTRAINT ck_val_status CHECK (val_status_final IN ('APROVADO','REJEITADO'))
);

CREATE TABLE REGRA_QUALIDADE (
    reg_id         NUMBER        GENERATED ALWAYS AS IDENTITY,
    reg_nome       VARCHAR2(120) NOT NULL,
    reg_tipo       VARCHAR2(20)  NOT NULL,
    reg_descricao  VARCHAR2(400),
    reg_severidade VARCHAR2(10)  DEFAULT 'ERRO' NOT NULL,
    reg_ativa      CHAR(1)       DEFAULT 'S' NOT NULL,
    CONSTRAINT pk_regra PRIMARY KEY (reg_id),
    CONSTRAINT uk_regra_nome UNIQUE (reg_nome),
    CONSTRAINT ck_regra_tipo CHECK (reg_tipo IN
        ('CAMPO','TIPO','DOMINIO','DUPLICIDADE','AREA','COORDENADA','GEOMETRIA')),
    CONSTRAINT ck_regra_severidade CHECK (reg_severidade IN ('ERRO','AVISO')),
    CONSTRAINT ck_regra_ativa CHECK (reg_ativa IN ('S','N'))
);

CREATE TABLE QUARENTENA (
    qua_id             NUMBER        GENERATED ALWAYS AS IDENTITY,
    val_id             NUMBER        NOT NULL,
    reg_id             NUMBER,
    qua_linha_origem   NUMBER(10),
    qua_coluna_erro    VARCHAR2(60),
    qua_motivo_erro    VARCHAR2(400) NOT NULL,
    qua_dado_original  CLOB,
    qua_situacao       VARCHAR2(15)  DEFAULT 'PENDENTE' NOT NULL,
    qua_data_registro  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    qua_data_resolucao TIMESTAMP,
    CONSTRAINT pk_quarentena PRIMARY KEY (qua_id),
    CONSTRAINT fk_qua_validacao FOREIGN KEY (val_id) REFERENCES VALIDACAO (val_id),
    CONSTRAINT fk_qua_regra     FOREIGN KEY (reg_id) REFERENCES REGRA_QUALIDADE (reg_id),
    CONSTRAINT ck_qua_dado_json CHECK (qua_dado_original IS NULL OR qua_dado_original IS JSON),
    CONSTRAINT ck_qua_situacao CHECK (qua_situacao IN ('PENDENTE','CORRIGIDO','DESCARTADO'))
);

-- Camadas geograficas (Oracle Spatial, SRID 4674)
CREATE TABLE MUNICIPIO (
    mun_id            NUMBER        GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id      NUMBER,
    mun_codigo_origem CHAR(7)       NOT NULL,
    mun_nome          VARCHAR2(120) NOT NULL,
    mun_uf            CHAR(2)       DEFAULT 'PR',
    mun_area_ha       NUMBER(14,4),
    mun_geometria     SDO_GEOMETRY,
    CONSTRAINT pk_municipio PRIMARY KEY (mun_id),
    CONSTRAINT uk_municipio_codigo UNIQUE (mun_codigo_origem),
    CONSTRAINT fk_mun_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id)
);

CREATE TABLE BIOMA (
    bio_id             NUMBER       GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id       NUMBER,
    bio_nome           VARCHAR2(80) NOT NULL,
    bio_perc_minimo_rl NUMBER(5,2),
    bio_geometria      SDO_GEOMETRY,
    CONSTRAINT pk_bioma PRIMARY KEY (bio_id),
    CONSTRAINT uk_bioma_nome UNIQUE (bio_nome),
    CONSTRAINT fk_bio_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id)
);

CREATE TABLE IMOVEL_RURAL (
    imo_id             NUMBER       GENERATED ALWAYS AS IDENTITY,
    mun_id             NUMBER,
    arq_bruto_id       NUMBER,
    ver_id             NUMBER,
    imo_codigo_car     VARCHAR2(60) NOT NULL,
    imo_geometria      SDO_GEOMETRY,
    imo_area_total     NUMBER(14,4),
    imo_data_informada TIMESTAMP,
    CONSTRAINT pk_imovel PRIMARY KEY (imo_id),
    CONSTRAINT uk_imovel_car UNIQUE (imo_codigo_car),
    CONSTRAINT fk_imo_municipio FOREIGN KEY (mun_id) REFERENCES MUNICIPIO (mun_id),
    CONSTRAINT fk_imo_arquivo   FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_imo_versao    FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

CREATE TABLE APP (
    app_id        NUMBER        GENERATED ALWAYS AS IDENTITY,
    imo_id        NUMBER        NOT NULL,
    arq_bruto_id  NUMBER,
    ver_id        NUMBER,
    app_area_ha   NUMBER(14,4),
    app_geometria SDO_GEOMETRY,
    CONSTRAINT pk_app PRIMARY KEY (app_id),
    CONSTRAINT fk_app_imovel  FOREIGN KEY (imo_id) REFERENCES IMOVEL_RURAL (imo_id),
    CONSTRAINT fk_app_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_app_versao  FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

CREATE TABLE RESERVA_LEGAL (
    rle_id        NUMBER        GENERATED ALWAYS AS IDENTITY,
    imo_id        NUMBER        NOT NULL,
    arq_bruto_id  NUMBER,
    ver_id        NUMBER,
    rle_area_ha   NUMBER(14,4),
    rle_geometria SDO_GEOMETRY,
    CONSTRAINT pk_reserva_legal PRIMARY KEY (rle_id),
    CONSTRAINT fk_rle_imovel  FOREIGN KEY (imo_id) REFERENCES IMOVEL_RURAL (imo_id),
    CONSTRAINT fk_rle_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_rle_versao  FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

CREATE TABLE VEGETACAO_NATIVA (
    veg_id        NUMBER        GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id  NUMBER,
    ver_id        NUMBER,
    veg_area_ha   NUMBER(14,4),
    veg_geometria SDO_GEOMETRY,
    CONSTRAINT pk_vegetacao PRIMARY KEY (veg_id),
    CONSTRAINT fk_veg_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_veg_versao  FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

CREATE TABLE AREA_PROTEGIDA (
    apr_id        NUMBER        GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id  NUMBER,
    ver_id        NUMBER,
    apr_tipo      VARCHAR2(20)  NOT NULL,
    apr_nome      VARCHAR2(200),
    apr_geometria SDO_GEOMETRY,
    CONSTRAINT pk_area_protegida PRIMARY KEY (apr_id),
    CONSTRAINT fk_apr_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_apr_versao  FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT ck_apr_tipo CHECK (apr_tipo IN ('UC','TERRA_INDIGENA','QUILOMBOLA','FLORESTA_PUBLICA'))
);

CREATE TABLE EMBARGO (
    emb_id            NUMBER       GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id      NUMBER,
    ver_id            NUMBER,
    emb_identificador VARCHAR2(80),
    emb_geometria     SDO_GEOMETRY,
    CONSTRAINT pk_embargo PRIMARY KEY (emb_id),
    CONSTRAINT fk_emb_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_emb_versao  FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

CREATE TABLE DESMATAMENTO (
    des_id         NUMBER       GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id   NUMBER,
    ver_id         NUMBER,
    des_ano_ref    NUMBER(4),
    des_data_corte DATE,
    des_geometria  SDO_GEOMETRY,
    CONSTRAINT pk_desmatamento PRIMARY KEY (des_id),
    CONSTRAINT fk_des_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_des_versao  FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

CREATE TABLE FOCO_CALOR (
    foc_id        NUMBER        GENERATED ALWAYS AS IDENTITY,
    arq_bruto_id  NUMBER,
    ver_id        NUMBER,
    foc_data      DATE,
    foc_latitude  NUMBER(9,6),
    foc_longitude NUMBER(9,6),
    foc_geometria SDO_GEOMETRY,
    CONSTRAINT pk_foco_calor PRIMARY KEY (foc_id),
    CONSTRAINT fk_foc_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id),
    CONSTRAINT fk_foc_versao  FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

-- Regras de calculo e resultados
CREATE TABLE REGRA_CALCULO (
    rca_id           NUMBER        GENERATED ALWAYS AS IDENTITY,
    ind_id           NUMBER        NOT NULL,
    rca_versao       VARCHAR2(20)  NOT NULL,
    rca_formula      VARCHAR2(600) NOT NULL,
    rca_descricao    VARCHAR2(400),
    rca_autor        VARCHAR2(120),
    rca_motivo       VARCHAR2(400),
    rca_vigente      CHAR(1)       DEFAULT 'S' NOT NULL,
    rca_data_criacao TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_regra_calculo PRIMARY KEY (rca_id),
    CONSTRAINT uk_rca_ind_versao UNIQUE (ind_id, rca_versao),
    CONSTRAINT fk_rca_indicador FOREIGN KEY (ind_id) REFERENCES INDICADOR (ind_id),
    CONSTRAINT ck_rca_vigente CHECK (rca_vigente IN ('S','N'))
);

CREATE TABLE PARAMETRO_REGRA_CALCULO (
    prc_id      NUMBER        GENERATED ALWAYS AS IDENTITY,
    rca_id      NUMBER        NOT NULL,
    prc_nome    VARCHAR2(60)  NOT NULL,
    prc_tipo    VARCHAR2(12)  NOT NULL,
    prc_unidade VARCHAR2(20),
    prc_valor   VARCHAR2(200) NOT NULL,
    CONSTRAINT pk_param_regra PRIMARY KEY (prc_id),
    CONSTRAINT uk_prc_regra_nome UNIQUE (rca_id, prc_nome),
    CONSTRAINT fk_prc_regra FOREIGN KEY (rca_id) REFERENCES REGRA_CALCULO (rca_id),
    CONSTRAINT ck_prc_tipo CHECK (prc_tipo IN ('PERCENTUAL','NUMERO','DATA','TEXTO'))
);

CREATE TABLE RESULTADO_INDICADOR (
    rin_id               NUMBER    GENERATED ALWAYS AS IDENTITY,
    imo_id               NUMBER    NOT NULL,
    ind_id               NUMBER    NOT NULL,
    ver_id               NUMBER    NOT NULL,
    exe_id               NUMBER,
    rca_id               NUMBER,
    rin_valor_percentual NUMBER(7,4),
    rin_valor_hectares   NUMBER(14,4),
    rin_valor_absoluto   NUMBER(14,4),
    rin_data_calculo     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_resultado_ind PRIMARY KEY (rin_id),
    CONSTRAINT uk_rin_imo_ind_ver UNIQUE (imo_id, ind_id, ver_id),
    CONSTRAINT fk_rin_imovel    FOREIGN KEY (imo_id) REFERENCES IMOVEL_RURAL (imo_id),
    CONSTRAINT fk_rin_indicador FOREIGN KEY (ind_id) REFERENCES INDICADOR (ind_id),
    CONSTRAINT fk_rin_versao    FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id),
    CONSTRAINT fk_rin_execucao  FOREIGN KEY (exe_id) REFERENCES EXECUCAO_PIPELINE (exe_id),
    CONSTRAINT fk_rin_regra     FOREIGN KEY (rca_id) REFERENCES REGRA_CALCULO (rca_id)
);

CREATE TABLE RESULTADO_MUNICIPIO (
    rmu_id             NUMBER     GENERATED ALWAYS AS IDENTITY,
    mun_id             NUMBER     NOT NULL,
    ind_id             NUMBER     NOT NULL,
    ver_id             NUMBER     NOT NULL,
    rmu_valor_agregado NUMBER(16,4),
    rmu_data_calculo   TIMESTAMP  DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_resultado_mun PRIMARY KEY (rmu_id),
    CONSTRAINT uk_rmu_mun_ind_ver UNIQUE (mun_id, ind_id, ver_id),
    CONSTRAINT fk_rmu_municipio FOREIGN KEY (mun_id) REFERENCES MUNICIPIO (mun_id),
    CONSTRAINT fk_rmu_indicador FOREIGN KEY (ind_id) REFERENCES INDICADOR (ind_id),
    CONSTRAINT fk_rmu_versao    FOREIGN KEY (ver_id) REFERENCES VERSAO (ver_id)
);

-- Metadados e indices espaciais
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('MUNICIPIO','MUN_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('BIOMA','BIO_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('IMOVEL_RURAL','IMO_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('APP','APP_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('RESERVA_LEGAL','RLE_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('VEGETACAO_NATIVA','VEG_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('AREA_PROTEGIDA','APR_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('EMBARGO','EMB_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('DESMATAMENTO','DES_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid) VALUES
 ('FOCO_CALOR','FOC_GEOMETRIA', SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG',-55,-48,0.5), SDO_DIM_ELEMENT('LAT',-27,-22,0.5)), 4674);
COMMIT;

CREATE INDEX idx_sp_municipio    ON MUNICIPIO(mun_geometria)        INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_bioma        ON BIOMA(bio_geometria)            INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_imovel       ON IMOVEL_RURAL(imo_geometria)     INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_app          ON APP(app_geometria)              INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_rle          ON RESERVA_LEGAL(rle_geometria)    INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_veg          ON VEGETACAO_NATIVA(veg_geometria) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_apr          ON AREA_PROTEGIDA(apr_geometria)   INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_embargo      ON EMBARGO(emb_geometria)          INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_desmatamento ON DESMATAMENTO(des_geometria)     INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;
CREATE INDEX idx_sp_foco         ON FOCO_CALOR(foc_geometria)       INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2;

-- Indices comuns
CREATE INDEX idx_usuario_perfil     ON USUARIO(per_id);
CREATE INDEX idx_fonte_usuario      ON FONTE_DO_DADO(usu_id);
CREATE INDEX idx_conjunto_fonte     ON CONJUNTO_DADOS(fon_id);
CREATE INDEX idx_conjunto_tipo      ON CONJUNTO_DADOS(tip_id);
CREATE INDEX idx_versao_conjunto    ON VERSAO(con_id);
CREATE INDEX idx_execucao_conjunto  ON EXECUCAO_PIPELINE(con_id);
CREATE INDEX idx_entrada_versao     ON ENTRADA_EXECUCAO(ver_id);
CREATE INDEX idx_param_execucao     ON PARAMETRO_EXECUCAO(exe_id);
CREATE INDEX idx_transf_execucao    ON TRANSFORMACAO(exe_id);
CREATE INDEX idx_arquivo_conjunto   ON ARQUIVO_BRUTO(con_id);
CREATE INDEX idx_mov_arquivo        ON MOVIMENTACAO_ZONA(arq_bruto_id);
CREATE INDEX idx_validacao_arquivo  ON VALIDACAO(arq_bruto_id);
CREATE INDEX idx_quarentena_val     ON QUARENTENA(val_id);
CREATE INDEX idx_quarentena_sit     ON QUARENTENA(qua_situacao);
CREATE INDEX idx_imovel_municipio   ON IMOVEL_RURAL(mun_id);
CREATE INDEX idx_imovel_versao      ON IMOVEL_RURAL(ver_id);
CREATE INDEX idx_regra_calc_ind     ON REGRA_CALCULO(ind_id);
CREATE UNIQUE INDEX uk_regra_vigente_ind ON REGRA_CALCULO (CASE WHEN rca_vigente = 'S' THEN ind_id END);
CREATE INDEX idx_param_regra        ON PARAMETRO_REGRA_CALCULO(rca_id);
CREATE INDEX idx_rin_versao         ON RESULTADO_INDICADOR(ver_id);
CREATE INDEX idx_rin_imovel         ON RESULTADO_INDICADOR(imo_id);
CREATE INDEX idx_rmu_versao         ON RESULTADO_MUNICIPIO(ver_id);
CREATE INDEX idx_auditoria_usu      ON AUDITORIA(usu_id);
