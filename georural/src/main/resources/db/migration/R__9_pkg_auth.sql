-- =====================================================================
-- GeoRural DataHub - View e Package de Autorizacao por sub (US#13.03)
-- =====================================================================

CREATE OR REPLACE VIEW vw_usuario_autorizacao AS
SELECT u.usu_id,
       u.usu_keycloak_id,
       u.usu_nome,
       u.usu_email,
       u.usu_status,
       p.per_id,
       p.per_funcao,
       p.per_nome
  FROM USUARIO u
  JOIN PERFIL  p ON p.per_id = u.per_id;

CREATE OR REPLACE PACKAGE PKG_AUTH AS
    -- Dado o sub do Keycloak, devolve usu_id, usu_status e per_funcao.
    -- Caso nao exista na base local, devolve NULL nos parametros OUT.
    PROCEDURE consultar_por_sub(
        p_sub        IN  VARCHAR2,
        p_usu_id     OUT NUMBER,
        p_usu_status OUT VARCHAR2,
        p_per_funcao OUT VARCHAR2
    );
END PKG_AUTH;
/

CREATE OR REPLACE PACKAGE BODY PKG_AUTH AS

    PROCEDURE consultar_por_sub(
        p_sub        IN  VARCHAR2,
        p_usu_id     OUT NUMBER,
        p_usu_status OUT VARCHAR2,
        p_per_funcao OUT VARCHAR2
    ) IS
    BEGIN
        p_usu_id     := NULL;
        p_usu_status := NULL;
        p_per_funcao := NULL;

        IF p_sub IS NULL THEN
            RETURN;
        END IF;

        SELECT u.usu_id, u.usu_status, p.per_funcao
          INTO p_usu_id, p_usu_status, p_per_funcao
          FROM USUARIO u
          JOIN PERFIL  p ON p.per_id = u.per_id
         WHERE u.usu_keycloak_id = p_sub;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_usu_id     := NULL;
            p_usu_status := NULL;
            p_per_funcao := NULL;
    END consultar_por_sub;

END PKG_AUTH;
/