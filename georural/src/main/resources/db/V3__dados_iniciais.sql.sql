-- GeoRural DataHub - Database Script - 03 Dados Iniciais

-- Perfis
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('ADMIN',    'Administrador');
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('OPERADOR', 'Operador de dados');
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('ANALISTA', 'Analista');
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('GESTOR',   'Gestor');
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('AUDITOR',  'Auditor');

-- Indicadores
INSERT INTO INDICADOR (ind_sigla, ind_nome, ind_descricao, ind_unidade) VALUES
 ('IAE', 'Índice de Área Embargada',
  'Percentual e área do imóvel sobreposta por embargos ambientais federais.', '%');
INSERT INTO INDICADOR (ind_sigla, ind_nome, ind_descricao, ind_unidade) VALUES
 ('IRL', 'Índice de Reserva Legal',
  'Área de Reserva Legal sobre a área do imóvel, comparada ao mínimo do bioma.', '%');
INSERT INTO INDICADOR (ind_sigla, ind_nome, ind_descricao, ind_unidade) VALUES
 ('ICV', 'Índice de Cobertura Vegetal',
  'Área de vegetação nativa sobre a área do imóvel.', '%');
INSERT INTO INDICADOR (ind_sigla, ind_nome, ind_descricao, ind_unidade) VALUES
 ('IAPP', 'Índice de APP Conservada',
  'Área de APP com vegetação nativa sobre a APP total do imóvel.', '%');
INSERT INTO INDICADOR (ind_sigla, ind_nome, ind_descricao, ind_unidade) VALUES
 ('ISAP', 'Índice de Sobreposição a Áreas Protegidas',
  'Sobreposição do imóvel com UCs, terras indígenas, quilombolas e florestas públicas.', '%');
INSERT INTO INDICADOR (ind_sigla, ind_nome, ind_descricao, ind_unidade) VALUES
 ('IDESMAT', 'Índice de Desmatamento',
  'Área e percentual do imóvel com supressão de vegetação após os marcos legais.', '%');
INSERT INTO INDICADOR (ind_sigla, ind_nome, ind_descricao, ind_unidade) VALUES
 ('IFC', 'Índice de Focos de Calor',
  'Focos de calor detectados no imóvel, proporcional à área (por 1.000 ha).', 'focos/1000ha');

-- Regras de calculo v1
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_vigente)
 SELECT ind_id, 'v1', 'area(embargo inter imovel) / area(imovel) * 100',
        'Cálculo direto por sobreposição. Sem parâmetros editáveis.', 'S'
 FROM INDICADOR WHERE ind_sigla = 'IAE';
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_vigente)
 SELECT ind_id, 'v1', 'area(vegetacao inter imovel) / area(imovel) * 100',
        'Cálculo direto por sobreposição. Sem parâmetros editáveis.', 'S'
 FROM INDICADOR WHERE ind_sigla = 'ICV';
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_vigente)
 SELECT ind_id, 'v1', 'area(vegetacao inter APP) / area(APP) * 100',
        'Cálculo direto por sobreposição. Sem parâmetros editáveis.', 'S'
 FROM INDICADOR WHERE ind_sigla = 'IAPP';
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_vigente)
 SELECT ind_id, 'v1', 'area(areas_protegidas inter imovel) / area(imovel) * 100',
        'Cálculo direto por sobreposição. Sem parâmetros editáveis.', 'S'
 FROM INDICADOR WHERE ind_sigla = 'ISAP';

INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_vigente)
 SELECT ind_id, 'v1', 'area(reserva_legal) / area(imovel) * 100  comparado ao minimo do bioma',
        'Compara a RL declarada com o mínimo exigido pelo bioma; déficit/excedente em ha.', 'S'
 FROM INDICADOR WHERE ind_sigla = 'IRL';
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_vigente)
 SELECT ind_id, 'v1', 'area(desmatamento inter imovel após marco) / area(imovel) * 100',
        'Supressão dentro do imóvel a partir de cada marco legal.', 'S'
 FROM INDICADOR WHERE ind_sigla = 'IDESMAT';
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_vigente)
 SELECT ind_id, 'v1', 'qtd_focos(imovel, periodo) / area(imovel) * fator',
        'Focos no imóvel no período apurado, proporcionais à área, por fator de hectares.', 'S'
 FROM INDICADOR WHERE ind_sigla = 'IFC';

-- Parametros das regras
INSERT INTO PARAMETRO_REGRA_CALCULO (rca_id, prc_nome, prc_tipo, prc_unidade, prc_valor)
 SELECT r.rca_id, 'minimo_rl_percent', 'PERCENTUAL', '%', '20'
 FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
 WHERE i.ind_sigla = 'IRL' AND r.rca_versao = 'v1';

INSERT INTO PARAMETRO_REGRA_CALCULO (rca_id, prc_nome, prc_tipo, prc_unidade, prc_valor)
 SELECT r.rca_id, 'marco_2008', 'DATA', 'data', '2008-07-22'
 FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
 WHERE i.ind_sigla = 'IDESMAT' AND r.rca_versao = 'v1';
INSERT INTO PARAMETRO_REGRA_CALCULO (rca_id, prc_nome, prc_tipo, prc_unidade, prc_valor)
 SELECT r.rca_id, 'marco_2019', 'DATA', 'data', '2019-07-31'
 FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
 WHERE i.ind_sigla = 'IDESMAT' AND r.rca_versao = 'v1';
INSERT INTO PARAMETRO_REGRA_CALCULO (rca_id, prc_nome, prc_tipo, prc_unidade, prc_valor)
 SELECT r.rca_id, 'marco_2020', 'DATA', 'data', '2020-12-31'
 FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
 WHERE i.ind_sigla = 'IDESMAT' AND r.rca_versao = 'v1';

INSERT INTO PARAMETRO_REGRA_CALCULO (rca_id, prc_nome, prc_tipo, prc_unidade, prc_valor)
 SELECT r.rca_id, 'fator_area', 'NUMERO', 'ha', '1000'
 FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
 WHERE i.ind_sigla = 'IFC' AND r.rca_versao = 'v1';
INSERT INTO PARAMETRO_REGRA_CALCULO (rca_id, prc_nome, prc_tipo, prc_unidade, prc_valor)
 SELECT r.rca_id, 'periodo_meses', 'NUMERO', 'meses', '12'
 FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
 WHERE i.ind_sigla = 'IFC' AND r.rca_versao = 'v1';

-- Bioma
INSERT INTO BIOMA (bio_nome, bio_perc_minimo_rl) VALUES ('Mata Atlântica', 20);

-- Tipos de conjunto
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('IMOVEL', 'Imóvel rural (perímetro)',
  '{"campos":["geometria","cod_imovel","cod_car","municipio","area_ha"]}', '.shp,.gpkg', 'base - todos');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('APP', 'APP declarada',
  '{"campos":["geometria","tipo_app","area_ha"]}', '.shp,.geojson', 'IAPP, ICV');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('RESERVA_LEGAL', 'Reserva Legal declarada',
  '{"campos":["geometria","situacao_rl","area_ha"]}', '.shp,.geojson', 'IRL');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('VEGETACAO', 'Vegetação nativa',
  '{"campos":["geometria","classe","area_ha"]}', '.tif,.shp', 'ICV, IAPP');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('BIOMA', 'Limites de biomas',
  '{"campos":["geometria","bioma"]}', '.shp', 'IRL');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('AREA_PROTEGIDA', 'Áreas protegidas e territórios especiais',
  '{"campos":["geometria","categoria","nome","esfera"]}', '.shp', 'ISAP');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('EMBARGO', 'Embargos ambientais',
  '{"campos":["geometria","cod_embargo","data_embargo","area_ha"]}', '.shp,.csv', 'IAE');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('DESMATAMENTO', 'Desmatamento',
  '{"campos":["geometria","ano","marco","area_ha"]}', '.shp,.geojson', 'IDesmat');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('FOCO_CALOR', 'Focos de calor',
  '{"campos":["geometria","data_hora","satelite","municipio"]}', '.csv,.shp', 'IFC');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('MALHA_MUNICIPAL', 'Malha municipal',
  '{"campos":["geometria","cod_municipio","nome_municipio","uf"]}', '.shp', 'agregação');

COMMIT;