-- =====================================================================
-- GeoRural DataHub - V16 - IFC, indice de focos de calor
-- =====================================================================
-- O IFC (focos por 1.000 ha) ja tem indicador, regra v1 e os parametros
-- fator_area e periodo_meses desde o V2. Falta o que o calculo precisa para
-- rodar de ponta a ponta:
--
-- 1) RESULTADO_INDICADOR.rin_valor_percentual era NUMBER(7,4), teto de 999,9999.
--    O IFC nao e percentual: 1 foco num imovel de 0,5 ha da 2.000 focos/1.000 ha
--    e estouraria a coluna com ORA-01438. A coluna e o "valor principal" lido por
--    ordenacao, filtro, exportacao e agregacao municipal, entao o IFC fica nela e
--    ela e ampliada (ampliar precisao nao invalida as views que a leem).
--
-- 2) Indices para as duas consultas do calculo: focos de uma versao (escopo) e
--    focos de um periodo. O terceiro serve tambem de chave de deduplicacao na carga
--    (mesma data e mesma posicao = o mesmo foco).

ALTER TABLE RESULTADO_INDICADOR MODIFY (rin_valor_percentual NUMBER(14,4));

CREATE INDEX idx_foc_versao   ON FOCO_CALOR (ver_id);
CREATE INDEX idx_foc_data     ON FOCO_CALOR (foc_data);
CREATE INDEX idx_foc_dia_pos  ON FOCO_CALOR (foc_data, foc_latitude, foc_longitude);
