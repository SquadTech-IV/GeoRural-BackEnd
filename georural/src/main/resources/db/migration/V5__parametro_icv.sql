-- =====================================================================
-- GeoRural DataHub - parametro da regra de calculo do ICV
-- =====================================================================
-- O indicador ICV e a REGRA_CALCULO v1 dele ja vieram no V2__dados_iniciais.sql,
-- mas sem parametro nenhum. Sem um minimo nao ha como dizer se o imovel esta
-- CONSERVADO ou DEGRADADO, nem como demonstrar a regra versionada que o criterio
-- de aceitacao da US#22 pede.
--
-- O padrao de 20% e o mesmo do minimo_rl_percent do IRL, por coerencia. Como
-- prc_editavel = 'S', o parametro ja entra no fluxo da US#14: editar pela tela
-- gera uma versao nova da regra automaticamente, sem codigo novo.

INSERT INTO PARAMETRO_REGRA_CALCULO (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade,
                                     prc_valor, prc_editavel, prc_min, prc_max)
 SELECT r.rca_id, 'minimo_cobertura_percent', 'Cobertura vegetal nativa minima',
        'PERCENTUAL', '%', '20', 'S', 0, 100
   FROM REGRA_CALCULO r
   JOIN INDICADOR i ON i.ind_id = r.ind_id
  WHERE i.ind_sigla = 'ICV'
    AND r.rca_versao = 'v1';
