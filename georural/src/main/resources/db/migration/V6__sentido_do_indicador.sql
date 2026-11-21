-- V6: para que lado o indicador melhora, e a escala fixa dele.
--
-- Sem isso nao da para ordenar "pior primeiro": em IRL um valor alto e bom, em
-- IAE um valor alto e problema. Deixar isso num mapa dentro do Java faria todo
-- indicador novo nascer com a ordenacao errada em silencio.

ALTER TABLE INDICADOR ADD (
    ind_sentido    VARCHAR2(13) DEFAULT 'MAIOR_MELHOR' NOT NULL,
    ind_escala_min NUMBER(16,4),
    ind_escala_max NUMBER(16,4)
);

ALTER TABLE INDICADOR
    ADD CONSTRAINT ck_ind_sentido CHECK (ind_sentido IN ('MAIOR_MELHOR','MENOR_MELHOR'));

-- indicadores em que valor alto e o problema
UPDATE INDICADOR
   SET ind_sentido = 'MENOR_MELHOR'
 WHERE ind_sigla IN ('IAE', 'ISAP', 'IDESMAT', 'IFC');

-- os seis percentuais tem escala fixa de 0 a 100, para as faixas do mapa nao
-- mudarem de lugar quando se troca de versao. O IFC e focos/1000ha e nao tem
-- teto natural: fica sem escala e as faixas dele saem dos proprios dados.
UPDATE INDICADOR
   SET ind_escala_min = 0,
       ind_escala_max = 100
 WHERE ind_unidade = '%';
