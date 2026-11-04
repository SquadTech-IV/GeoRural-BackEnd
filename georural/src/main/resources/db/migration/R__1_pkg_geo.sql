-- PKG_GEO (repetivel): operacoes geoespaciais no banco
CREATE OR REPLACE PACKAGE PKG_GEO AS
    c_tol CONSTANT NUMBER := 0.05;

    FUNCTION area_ha(p_geom SDO_GEOMETRY) RETURN NUMBER;
    FUNCTION area_intersecao_ha(p_a SDO_GEOMETRY, p_b SDO_GEOMETRY) RETURN NUMBER;
    FUNCTION intersecao(p_a SDO_GEOMETRY, p_b SDO_GEOMETRY) RETURN SDO_GEOMETRY;

    -- limite da UF, com cache de sessao
    FUNCTION limite_uf(p_uf VARCHAR2 DEFAULT 'PR') RETURN SDO_GEOMETRY;
    PROCEDURE limpar_cache;

    FUNCTION recorte_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN SDO_GEOMETRY;
    FUNCTION dentro_da_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN VARCHAR2;

    -- 'TRUE' quando a geometria esta inteiramente dentro da UF
    FUNCTION contida_na_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN VARCHAR2;

    FUNCTION saneada(p_geom SDO_GEOMETRY) RETURN SDO_GEOMETRY;
    FUNCTION valida(p_geom SDO_GEOMETRY) RETURN VARCHAR2;
END PKG_GEO;
/

CREATE OR REPLACE PACKAGE BODY PKG_GEO AS

    g_uf_codigo VARCHAR2(20);
    g_uf_geom   SDO_GEOMETRY;

    PROCEDURE limpar_cache IS
    BEGIN
        g_uf_codigo := NULL;
        g_uf_geom := NULL;
    END limpar_cache;

    FUNCTION limite_uf(p_uf VARCHAR2 DEFAULT 'PR') RETURN SDO_GEOMETRY IS
    BEGIN
        IF g_uf_codigo = p_uf AND g_uf_geom IS NOT NULL THEN
            RETURN g_uf_geom;
        END IF;

        SELECT lim_geometria INTO g_uf_geom
          FROM LIMITE_TERRITORIAL
         WHERE lim_tipo = 'UF' AND lim_codigo = p_uf;

        g_uf_codigo := p_uf;
        RETURN g_uf_geom;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            g_uf_codigo := NULL;
            g_uf_geom := NULL;
            RAISE_APPLICATION_ERROR(-20020,
                'Limite da UF ' || p_uf || ' nao carregado em LIMITE_TERRITORIAL. '
                || 'Processe a malha municipal do IBGE e chame '
                || 'POST /api/pipeline/limite-territorial/recarregar.');
    END limite_uf;

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
            BEGIN
                RETURN SDO_GEOM.SDO_AREA(saneada(p_geom), c_tol, 'unit=HECTARE');
            EXCEPTION
                WHEN OTHERS THEN RETURN NULL;
            END;
    END area_ha;

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

    -- RELATE decide sem construir geometria nova
    FUNCTION contida_na_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN VARCHAR2 IS
        v_limite SDO_GEOMETRY;
    BEGIN
        IF p_geom IS NULL THEN RETURN 'FALSE'; END IF;
        v_limite := limite_uf(p_uf);
        IF SDO_GEOM.RELATE(v_limite, 'CONTAINS+COVERS', p_geom, c_tol) = 'FALSE' THEN
            RETURN 'FALSE';
        END IF;
        RETURN 'TRUE';
    EXCEPTION
        WHEN OTHERS THEN RETURN 'FALSE';
    END contida_na_uf;

    FUNCTION recorte_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN SDO_GEOMETRY IS
    BEGIN
        IF p_geom IS NULL THEN RETURN NULL; END IF;
        RETURN intersecao(p_geom, limite_uf(p_uf));
    END recorte_uf;

    FUNCTION dentro_da_uf(p_geom SDO_GEOMETRY, p_uf VARCHAR2 DEFAULT 'PR') RETURN VARCHAR2 IS
    BEGIN
        IF p_geom IS NULL THEN RETURN 'FALSE'; END IF;
        RETURN SDO_GEOM.RELATE(p_geom, 'ANYINTERACT', limite_uf(p_uf), c_tol);
    EXCEPTION
        WHEN OTHERS THEN RETURN 'FALSE';
    END dentro_da_uf;

    FUNCTION valida(p_geom SDO_GEOMETRY) RETURN VARCHAR2 IS
    BEGIN
        IF p_geom IS NULL THEN RETURN 'NULA'; END IF;
        RETURN SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(p_geom, c_tol);
    END valida;

END PKG_GEO;
/
