-- =====================================================================
-- GeoRural DataHub - V17 - padronizar a medicao de area
-- =====================================================================

DELETE FROM PARAMETRO_REGRA_CALCULO
 WHERE prc_nome = 'srid_equivalente';
