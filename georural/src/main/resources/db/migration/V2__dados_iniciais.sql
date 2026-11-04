-- =====================================================================
-- GeoRural DataHub - V2 - Dados Iniciais
-- =====================================================================

-- =====================================================================
-- Perfis
-- =====================================================================
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('ADMIN',    'Administrador');
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('OPERADOR', 'Operador de dados');
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('ANALISTA', 'Analista');
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('GESTOR',   'Gestor');
INSERT INTO PERFIL (per_funcao, per_nome) VALUES ('AUDITOR',  'Auditor');

-- =====================================================================
-- Usuarios espelho
-- =====================================================================
INSERT INTO USUARIO (per_id, usu_nome, usu_email)
 VALUES ((SELECT per_id FROM PERFIL WHERE per_funcao = 'ADMIN'),
         'Administrador', 'admin@georural.invalid');
INSERT INTO USUARIO (per_id, usu_nome, usu_email)
 VALUES ((SELECT per_id FROM PERFIL WHERE per_funcao = 'OPERADOR'),
         'Operador de dados', 'operador@georural.invalid');
INSERT INTO USUARIO (per_id, usu_nome, usu_email)
 VALUES ((SELECT per_id FROM PERFIL WHERE per_funcao = 'ANALISTA'),
         'Analista', 'analista@georural.invalid');
INSERT INTO USUARIO (per_id, usu_nome, usu_email)
 VALUES ((SELECT per_id FROM PERFIL WHERE per_funcao = 'GESTOR'),
         'Gestor', 'gestor@georural.invalid');
INSERT INTO USUARIO (per_id, usu_nome, usu_email)
 VALUES ((SELECT per_id FROM PERFIL WHERE per_funcao = 'AUDITOR'),
         'Auditor', 'auditor@georural.invalid');

-- =====================================================================
-- Regras de qualidade
-- =====================================================================
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Campo obrigatorio ausente', 'CAMPO',
  'O arquivo nao traz um campo exigido pelo tipo de conjunto (ex.: codigo do CAR no imovel rural).', 'ERRO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Campo opcional ausente', 'CAMPO',
  'Campo util, porem nao obrigatorio, nao veio no arquivo. O registro segue para tratamento.', 'AVISO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Tipo de dado invalido', 'TIPO',
  'O valor nao corresponde ao tipo esperado (ex.: texto onde se espera numero ou data).', 'ERRO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Valor fora do dominio', 'DOMINIO',
  'O valor existe mas nao esta na lista aceita para o campo (ex.: UF diferente de PR).', 'ERRO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Registro duplicado no arquivo', 'DUPLICIDADE',
  'Duas ou mais feicoes com o mesmo identificador dentro do mesmo arquivo.', 'ERRO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Registro ja existente na base', 'DUPLICIDADE',
  'O identificador da feicao ja existe em uma versao anterior do conjunto.', 'AVISO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Area incompativel', 'AREA',
  'A area declarada difere da area calculada pela geometria alem da tolerancia aceita.', 'AVISO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Area nula ou negativa', 'AREA',
  'A feicao tem area zero ou negativa, o que impede o calculo dos indicadores.', 'ERRO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Coordenada fora da area de interesse', 'COORDENADA',
  'A feicao esta fora do limite do Parana, que e o recorte territorial do projeto.', 'ERRO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Sistema de referencia ausente ou divergente', 'COORDENADA',
  'O arquivo nao informa o sistema de referencia ou usa um diferente do declarado no conjunto.', 'AVISO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Geometria invalida', 'GEOMETRIA',
  'A geometria tem auto-intersecao, anel aberto ou orientacao incorreta.', 'ERRO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Geometria ausente', 'GEOMETRIA',
  'A feicao nao possui geometria, sendo obrigatoria para o tipo de conjunto.', 'ERRO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Geometria corrigida automaticamente', 'GEOMETRIA',
  'A geometria estava invalida e foi saneada pelo tratamento; o registro seguiu no fluxo.', 'AVISO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Formato divergente do declarado', 'FORMATO',
  'O formato detectado no arquivo e diferente do formato esperado no cadastro do conjunto.', 'AVISO');
INSERT INTO REGRA_QUALIDADE (reg_nome, reg_tipo, reg_descricao, reg_severidade) VALUES
 ('Formato nao suportado', 'FORMATO',
  'O arquivo nao esta em um dos formatos aceitos: .shp, .gpkg, .geojson, .json, .csv, .tif.', 'ERRO');

-- =====================================================================
-- Indicadores
-- =====================================================================
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

-- =====================================================================
-- Regras de calculo
-- =====================================================================
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_autor, rca_vigente)
 VALUES ((SELECT ind_id FROM INDICADOR WHERE ind_sigla = 'IAE'), 'v1',
         'area(embargo inter imovel) / area(imovel) * 100',
         'Cálculo direto por sobreposição com os embargos federais.', 'SquadTech', 'S');
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_autor, rca_vigente)
 VALUES ((SELECT ind_id FROM INDICADOR WHERE ind_sigla = 'IRL'), 'v1',
         'area(reserva_legal) / area(imovel) * 100  comparado ao minimo do bioma',
         'Compara a RL declarada com o mínimo exigido pelo bioma; déficit/excedente em ha.', 'SquadTech', 'S');
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_autor, rca_vigente)
 VALUES ((SELECT ind_id FROM INDICADOR WHERE ind_sigla = 'ICV'), 'v1',
         'area(vegetacao inter imovel) / area(imovel) * 100',
         'Cálculo direto por sobreposição.', 'SquadTech', 'S');
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_autor, rca_vigente)
 VALUES ((SELECT ind_id FROM INDICADOR WHERE ind_sigla = 'IAPP'), 'v1',
         'area(vegetacao inter APP) / area(APP) * 100',
         'Cálculo direto por sobreposição.', 'SquadTech', 'S');
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_autor, rca_vigente)
 VALUES ((SELECT ind_id FROM INDICADOR WHERE ind_sigla = 'ISAP'), 'v1',
         'area(areas_protegidas inter imovel) / area(imovel) * 100',
         'Cálculo direto por sobreposição.', 'SquadTech', 'S');
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_autor, rca_vigente)
 VALUES ((SELECT ind_id FROM INDICADOR WHERE ind_sigla = 'IDESMAT'), 'v1',
         'area(desmatamento inter imovel após marco) / area(imovel) * 100',
         'Supressão dentro do imóvel a partir de cada marco legal.', 'SquadTech', 'S');
INSERT INTO REGRA_CALCULO (ind_id, rca_versao, rca_formula, rca_descricao, rca_autor, rca_vigente)
 VALUES ((SELECT ind_id FROM INDICADOR WHERE ind_sigla = 'IFC'), 'v1',
         'qtd_focos(imovel, periodo) / area(imovel) * fator',
         'Focos no imóvel no período apurado, proporcionais à área.', 'SquadTech', 'S');

-- =====================================================================
-- Parametros das regras.
-- =====================================================================
INSERT INTO PARAMETRO_REGRA_CALCULO
       (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel, prc_min, prc_max)
 VALUES ((SELECT r.rca_id FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
           WHERE i.ind_sigla = 'IRL' AND r.rca_versao = 'v1'),
         'minimo_rl_percent', 'Reserva Legal mínima do bioma', 'PERCENTUAL', '%', '20', 'S', 0, 100);

INSERT INTO PARAMETRO_REGRA_CALCULO
       (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel)
 VALUES ((SELECT r.rca_id FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
           WHERE i.ind_sigla = 'IDESMAT' AND r.rca_versao = 'v1'),
         'marco_2008', 'Marco legal de 2008', 'DATA', 'data', '2008-07-22', 'S');
INSERT INTO PARAMETRO_REGRA_CALCULO
       (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel)
 VALUES ((SELECT r.rca_id FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
           WHERE i.ind_sigla = 'IDESMAT' AND r.rca_versao = 'v1'),
         'marco_2019', 'Marco legal de 2019', 'DATA', 'data', '2019-07-31', 'S');
INSERT INTO PARAMETRO_REGRA_CALCULO
       (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel)
 VALUES ((SELECT r.rca_id FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
           WHERE i.ind_sigla = 'IDESMAT' AND r.rca_versao = 'v1'),
         'marco_2020', 'Marco legal de 2020', 'DATA', 'data', '2020-12-31', 'S');

INSERT INTO PARAMETRO_REGRA_CALCULO
       (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel, prc_min, prc_max)
 VALUES ((SELECT r.rca_id FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
           WHERE i.ind_sigla = 'IFC' AND r.rca_versao = 'v1'),
         'fator_area', 'Fator de área para a proporção', 'NUMERO', 'ha', '1000', 'S', 1, 100000);
INSERT INTO PARAMETRO_REGRA_CALCULO
       (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel, prc_min, prc_max)
 VALUES ((SELECT r.rca_id FROM REGRA_CALCULO r JOIN INDICADOR i ON i.ind_id = r.ind_id
           WHERE i.ind_sigla = 'IFC' AND r.rca_versao = 'v1'),
         'periodo_meses', 'Período de apuração', 'NUMERO', 'meses', '12', 'S', 1, 120);

-- =====================================================================
-- Bioma
-- =====================================================================
INSERT INTO BIOMA (bio_nome, bio_perc_minimo_rl) VALUES ('Mata Atlântica', 20);

-- =====================================================================
-- Tipos de conjunto.
-- =====================================================================
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
  '{"campos":["geometria","cod_embargo","data_embargo","area_ha"]}', '.shp,.csv,.geojson', 'IAE');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('DESMATAMENTO', 'Desmatamento',
  '{"campos":["geometria","ano","marco","area_ha"]}', '.shp,.geojson', 'IDesmat');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('FOCO_CALOR', 'Focos de calor',
  '{"campos":["geometria","data_hora","satelite","municipio"]}', '.csv,.shp', 'IFC');
INSERT INTO TIPO_CONJUNTO (tip_codigo, tip_nome, tip_esquema, tip_formatos, tip_indicadores) VALUES
 ('MALHA_MUNICIPAL', 'Malha municipal',
  '{"campos":["geometria","cod_municipio","nome_municipio","uf"]}', '.shp', 'agregação');