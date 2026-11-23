-- =====================================================================
-- GeoRural DataHub - V11 - IDesmat (US#25)
-- =====================================================================

-- Garante colunas auxiliares para data de referencia e projeção equivalente
ALTER TABLE DESMATAMENTO ADD (
    des_bioma      VARCHAR2(80),
    des_area_ha    NUMBER(14,4)
);

CREATE INDEX idx_des_versao     ON DESMATAMENTO (ver_id);
CREATE INDEX idx_des_data_corte ON DESMATAMENTO (des_data_corte);
CREATE INDEX idx_des_ano_ref    ON DESMATAMENTO (des_ano_ref);

-- Parametro de SRID equivalente (Albers Equal Area South America / Brasil)
INSERT INTO PARAMETRO_REGRA_CALCULO
       (rca_id, prc_nome, prc_rotulo, prc_tipo, prc_unidade, prc_valor, prc_editavel)
SELECT r.rca_id, 'srid_equivalente', 'SRID da projeção equivalente para medição de área',
       'NUMERO', 'srid', '102033', 'S'
  FROM REGRA_CALCULO r
  JOIN INDICADOR i ON i.ind_id = r.ind_id
 WHERE i.ind_sigla = 'IDESMAT'
   AND r.rca_versao = 'v1'
   AND NOT EXISTS (
       SELECT 1 FROM PARAMETRO_REGRA_CALCULO p
        WHERE p.rca_id = r.rca_id AND p.prc_nome = 'srid_equivalente'
   );