-- =====================================================================
-- GeoRural DataHub - PKG_GEO (repetivel)
-- =====================================================================
-- Operacoes geoespaciais no banco, substituindo JTS/GeoTools em Java.
-- Arquivo REPETIVEL: o Flyway reaplica sempre que o conteudo mudar.
--
-- Detalhe importante: o SRID 4674 (SIRGAS 2000) e GEODESICO. Para esse tipo de
-- sistema o Oracle ja calcula area geodesica correta e aceita 'unit=HECTARE'.
-- Ou seja, NAO e necessario reprojetar para Albers antes de medir area, como o
-- codigo Java faz hoje. A tolerancia (c_tol) e em metros.

CREATE OR REPLACE PACKAGE PKG_GEO AS
    c_tol CONSTANT NUMBER := 0.05;

    FUNCTION area_ha(p_geom SDO_GEOMETRY) RETURN NUMBER;
    FUNCTION area_intersecao_ha(p_a SDO_GEOMETRY, p_b SDO_GEOMETRY) RETURN NUMBER;
    FUNCTION intersecao(p_a SDO_GEOMETRY, p_b SDO_GEOMETRY) RETURN SDO_GEOMETRY;
    FUNCTION recorte_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN SDO_GEOMETRY;
    FUNCTION dentro_da_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN VARCHAR2;
    FUNCTION saneada(p_geom SDO_GEOMETRY) RETURN SDO_GEOMETRY;
    FUNCTION valida(p_geom SDO_GEOMETRY) RETURN VARCHAR2;
END PKG_GEO;
/

CREATE OR REPLACE PACKAGE BODY PKG_GEO AS

    -- Corrige geometrias invalidas (auto-intersecao, orientacao de aneis).
    FUNCTION saneada(p_geom SDO_GEOMETRY) RETURN SDO_GEOMETRY IS
    BEGIN
        IF p_geom IS NULL THEN RETURN NULL; END IF;
        RETURN SDO_UTIL.RECTIFY_GEOMETRY(p_geom, c_tol);
    END saneada;

    -- Area em hectares. NULL se a geometria for nula.
    FUNCTION area_ha(p_geom SDO_GEOMETRY) RETURN NUMBER IS
    BEGIN
        IF p_geom IS NULL THEN RETURN NULL; END IF;
        RETURN SDO_GEOM.SDO_AREA(p_geom, c_tol, 'unit=HECTARE');
    EXCEPTION
        WHEN OTHERS THEN
            -- geometria problematica: tenta sanear antes de desistir
            BEGIN
                RETURN SDO_GEOM.SDO_AREA(saneada(p_geom), c_tol, 'unit=HECTARE');
            EXCEPTION
                WHEN OTHERS THEN RETURN NULL;
            END;
    END area_ha;

    -- Geometria resultante do cruzamento entre duas camadas.
    FUNCTION intersecao(p_a SDO_GEOMETRY, p_b SDO_GEOMETRY) RETURN SDO_GEOMETRY IS
    BEGIN
        IF p_a IS NULL OR p_b IS NULL THEN RETURN NULL; END IF;
        RETURN SDO_GEOM.SDO_INTERSECTION(p_a, p_b, c_tol);
    EXCEPTION
        WHEN OTHERS THEN
            BEGIN
                RETURN SDO_GEOM.SDO_INTERSECTION(saneada(p_a), saneada(p_b), c_tol);
            EXCEPTION
                WHEN OTHERS THEN RETURN NULL;
            END;
    END intersecao;

    -- Area sobreposta entre duas geometrias, em hectares. 0 quando nao se tocam.
    FUNCTION area_intersecao_ha(p_a SDO_GEOMETRY, p_b SDO_GEOMETRY) RETURN NUMBER IS
        v_inter SDO_GEOMETRY;
    BEGIN
        IF p_a IS NULL OR p_b IS NULL THEN RETURN 0; END IF;
        IF SDO_GEOM.RELATE(p_a, 'ANYINTERACT', p_b, c_tol) <> 'TRUE' THEN
            RETURN 0;
        END IF;
        v_inter := intersecao(p_a, p_b);
        IF v_inter IS NULL THEN RETURN 0; END IF;
        RETURN NVL(area_ha(v_inter), 0);
    EXCEPTION
        WHEN OTHERS THEN RETURN 0;
    END area_intersecao_ha;

    -- Recorta a geometria pelo limite da UF. Substitui o ClipState em Java.
    FUNCTION recorte_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN SDO_GEOMETRY IS
        v_limite SDO_GEOMETRY;
    BEGIN
        IF p_geom IS NULL THEN RETURN NULL; END IF;
        SELECT lim_geometria INTO v_limite
          FROM LIMITE_TERRITORIAL
         WHERE lim_tipo = 'UF' AND lim_codigo = p_uf;
        RETURN intersecao(p_geom, v_limite);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20020,
                'Limite da UF ' || p_uf || ' nao carregado em LIMITE_TERRITORIAL.');
    END recorte_uf;

    -- 'TRUE' se a geometria encosta no limite da UF. Usado na regra de qualidade
    -- "Coordenada fora da area de interesse".
    FUNCTION dentro_da_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN VARCHAR2 IS
        v_limite SDO_GEOMETRY;
    BEGIN
        IF p_geom IS NULL THEN RETURN 'FALSE'; END IF;
        SELECT lim_geometria INTO v_limite
          FROM LIMITE_TERRITORIAL
         WHERE lim_tipo = 'UF' AND lim_codigo = p_uf;
        RETURN SDO_GEOM.RELATE(p_geom, 'ANYINTERACT', v_limite, c_tol);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20020,
                'Limite da UF ' || p_uf || ' nao carregado em LIMITE_TERRITORIAL.');
        WHEN OTHERS THEN RETURN 'FALSE';
    END dentro_da_uf;

    -- 'TRUE' quando valida; caso contrario, o codigo do erro do Oracle.
    FUNCTION valida(p_geom SDO_GEOMETRY) RETURN VARCHAR2 IS
    BEGIN
        IF p_geom IS NULL THEN RETURN 'NULA'; END IF;
        RETURN SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(p_geom, c_tol);
    END valida;

END PKG_GEO;
/
